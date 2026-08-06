package businessservice

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const (
	eventTypeMaxLength        = 64
	titleMaxLength            = 255
	messageMaxLength          = 4000
	traceIDMaxLength          = 128
	heartbeatMetadataMaxBytes = 16 * 1024
	eventDataMaxBytes         = 64 * 1024
	reportingTimeFormat       = "2006-01-02 15:04:05"
)

type queuedEvent struct {
	eventType string
	severity  Severity
	title     string
	message   string
	traceID   string
	data      map[string]any
}

// Client 负责 Zenvis 业务服务的注册、心跳、生命周期事件和自定义事件上报。
type Client struct {
	config    Config
	identity  identity
	transport transport
	location  *time.Location
	events    chan queuedEvent

	started  atomic.Bool
	stopping atomic.Bool

	contextMu sync.Mutex
	ctx       context.Context
	cancel    context.CancelFunc

	dispatchMu           sync.Mutex
	stateMu              sync.Mutex
	registered           bool
	startupEventReported bool

	startupEventID  string
	stoppingEventID string

	workers   sync.WaitGroup
	closeOnce sync.Once
	closeDone chan struct{}
}

// New 创建 Client，但不会自动启动后台任务。调用 Start 后才开始上报。
func New(config Config) *Client {
	config = applyDefaults(config)
	location, err := time.LoadLocation(config.TimeZone)
	if err != nil {
		config.Logger.Printf("无效的 Zenvis 上报时区 %s，使用 Asia/Shanghai", config.TimeZone)
		location, err = time.LoadLocation("Asia/Shanghai")
		if err != nil {
			location = time.FixedZone("Asia/Shanghai", 8*60*60)
		}
	}

	client := &Client{
		config:          config,
		identity:        resolveIdentity(config),
		location:        location,
		events:          make(chan queuedEvent, config.EventQueueCapacity),
		startupEventID:  newEventID(),
		stoppingEventID: newEventID(),
		closeDone:       make(chan struct{}),
	}
	client.transport = newHTTPTransport(config)
	return client
}

// Start 启动事件 worker 和固定延迟心跳。首次心跳会立即执行。
// 重复调用 Start 不会创建重复后台任务。
func (c *Client) Start(parent context.Context) {
	if c.config.Disabled || c.stopping.Load() || !c.started.CompareAndSwap(false, true) {
		return
	}
	if parent == nil {
		parent = context.Background()
	}

	c.contextMu.Lock()
	c.ctx, c.cancel = context.WithCancel(context.Background())
	ctx := c.ctx
	c.contextMu.Unlock()

	c.workers.Add(2)
	go c.runEventWorker(ctx)
	go c.runHeartbeatWorker(ctx)

	go func() {
		select {
		case <-parent.Done():
			shutdownTimeout := 2 * (c.config.ConnectTimeout + c.config.ReadTimeout)
			ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
			defer cancel()
			_ = c.Close(ctx)
		case <-c.closeDone:
		}
	}()
}

// ReportEvent 将事件放入容量受限的单 worker 队列。
// 队列满、SDK 未启动、正在停止或已禁用时会丢弃事件，不阻塞业务调用方。
func (c *Client) ReportEvent(
	eventType string,
	severity Severity,
	title string,
	message string,
	traceID string,
	data map[string]any,
) {
	if c.config.Disabled || !c.started.Load() || c.stopping.Load() {
		return
	}
	event := queuedEvent{
		eventType: eventType,
		severity:  severity,
		title:     title,
		message:   message,
		traceID:   traceID,
		data:      cloneMap(data),
	}
	select {
	case c.events <- event:
	default:
		c.config.Logger.Printf("Zenvis 事件队列已满或不可用，丢弃事件: eventType=%s", eventType)
	}
}

// Close 停止后台任务；若实例已注册，则依次上报 SERVICE_STOPPING 和 DOWN 心跳。
// Close 可重复调用。ctx 仅控制调用方等待时长，后台关闭流程只执行一次。
func (c *Client) Close(ctx context.Context) error {
	if c.config.Disabled {
		return nil
	}
	if ctx == nil {
		ctx = context.Background()
	}
	c.closeOnce.Do(func() {
		go c.shutdown()
	})
	select {
	case <-c.closeDone:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (c *Client) shutdown() {
	defer close(c.closeDone)
	if !c.stopping.CompareAndSwap(false, true) {
		return
	}

	c.contextMu.Lock()
	if c.cancel != nil {
		c.cancel()
	}
	c.contextMu.Unlock()
	c.workers.Wait()

	c.stateMu.Lock()
	registered := c.registered
	c.stateMu.Unlock()
	if !registered {
		return
	}

	timeout := 2 * (c.config.ConnectTimeout + c.config.ReadTimeout)
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	c.dispatchMu.Lock()
	defer c.dispatchMu.Unlock()
	portData := map[string]any(nil)
	if c.identity.Port != 0 {
		portData = map[string]any{"port": c.identity.Port}
	}
	c.transport.reportEvent(ctx, c.buildEvent(
		c.stoppingEventID,
		serviceStopping,
		SeverityInfo,
		c.identity.ServiceName+" 服务正在停止",
		c.identity.ServiceCode+" 正在正常关闭",
		"",
		portData,
	))
	c.sendHeartbeat(ctx, statusDown, "stopping")
	c.stateMu.Lock()
	c.registered = false
	c.stateMu.Unlock()
}

func (c *Client) runHeartbeatWorker(ctx context.Context) {
	defer c.workers.Done()
	for {
		if ctx.Err() != nil {
			return
		}
		c.sendScheduledHeartbeat(ctx)
		timer := time.NewTimer(c.config.HeartbeatInterval)
		select {
		case <-ctx.Done():
			if !timer.Stop() {
				<-timer.C
			}
			return
		case <-timer.C:
		}
	}
}

func (c *Client) runEventWorker(ctx context.Context) {
	defer c.workers.Done()
	for {
		if ctx.Err() != nil {
			return
		}
		select {
		case <-ctx.Done():
			return
		case event := <-c.events:
			c.dispatchEvent(ctx, event)
		}
	}
}

func (c *Client) sendScheduledHeartbeat(ctx context.Context) {
	if c.stopping.Load() {
		return
	}
	c.dispatchMu.Lock()
	defer c.dispatchMu.Unlock()
	if c.stopping.Load() || !c.sendHeartbeat(ctx, statusUp, "ready") {
		return
	}

	c.stateMu.Lock()
	startupReported := c.startupEventReported
	c.stateMu.Unlock()
	if startupReported {
		return
	}

	portData := map[string]any(nil)
	if c.identity.Port != 0 {
		portData = map[string]any{"port": c.identity.Port}
	}
	startedEvent := c.buildEvent(
		c.startupEventID,
		serviceStarted,
		SeverityInfo,
		c.identity.ServiceName+" 服务已启动",
		c.identity.ServiceCode+" 已启动并完成 Zenvis 心跳注册",
		"",
		portData,
	)
	if c.transport.reportEvent(ctx, startedEvent) {
		c.stateMu.Lock()
		c.startupEventReported = true
		c.stateMu.Unlock()
		c.config.Logger.Printf("业务服务已注册到 Zenvis: serviceCode=%s, instanceId=%s", c.identity.ServiceCode, c.identity.InstanceID)
	}
}

func (c *Client) dispatchEvent(ctx context.Context, event queuedEvent) {
	c.dispatchMu.Lock()
	defer c.dispatchMu.Unlock()
	if c.stopping.Load() {
		return
	}
	c.stateMu.Lock()
	registered := c.registered
	c.stateMu.Unlock()
	if !registered && !c.sendHeartbeat(ctx, statusUp, "ready") {
		c.config.Logger.Printf("跳过 Zenvis 事件上报，实例尚未注册: eventType=%s", event.eventType)
		return
	}
	c.transport.reportEvent(ctx, c.buildEvent(
		newEventID(),
		event.eventType,
		event.severity,
		event.title,
		event.message,
		event.traceID,
		event.data,
	))
}

func (c *Client) sendHeartbeat(ctx context.Context, status serviceStatus, statusMessage string) bool {
	request := heartbeatRequest{
		ServiceCode:   c.identity.ServiceCode,
		ServiceName:   c.identity.ServiceName,
		InstanceID:    c.identity.InstanceID,
		Status:        status,
		StatusMessage: statusMessage,
		Version:       c.identity.Version,
		Environment:   c.identity.Environment,
		Host:          c.identity.Host,
		ManagementURL: c.identity.ManagementURL,
		HeartbeatTime: c.now(),
		Metadata:      sanitizeData(c.identity.Metadata, heartbeatMetadataMaxBytes, "heartbeat metadata"),
	}
	if c.identity.Port != 0 {
		port := c.identity.Port
		request.Port = &port
	}
	succeeded := c.transport.reportHeartbeat(ctx, request)
	if succeeded {
		c.stateMu.Lock()
		c.registered = status != statusDown
		c.stateMu.Unlock()
	}
	return succeeded
}

func (c *Client) buildEvent(
	eventID string,
	eventType string,
	severity Severity,
	title string,
	message string,
	traceID string,
	data map[string]any,
) eventRequest {
	normalizedType := normalizeEventType(eventType)
	return eventRequest{
		EventID:     eventID,
		ServiceCode: c.identity.ServiceCode,
		InstanceID:  c.identity.InstanceID,
		EventType:   normalizedType,
		Severity:    normalizeSeverity(severity),
		Title:       truncate(defaultIfBlank(title, normalizedType), titleMaxLength),
		Message:     nullableTruncate(message, messageMaxLength),
		OccurredAt:  c.now(),
		TraceID:     nullableTruncate(traceID, traceIDMaxLength),
		Data:        sanitizeData(data, eventDataMaxBytes, "event data"),
	}
}

func normalizeEventType(eventType string) string {
	normalized := strings.TrimSpace(defaultIfBlank(eventType, "UNKNOWN_EVENT"))
	var builder strings.Builder
	for _, r := range normalized {
		if isASCIIAlphaNumeric(r) || r == '.' || r == '_' || r == ':' || r == '-' {
			builder.WriteRune(r)
		} else {
			builder.WriteByte('_')
		}
	}
	normalized = builder.String()
	first, _ := firstRune(normalized)
	if !isASCIIAlphaNumeric(first) {
		normalized = "EVENT_" + normalized
	}
	return truncate(normalized, eventTypeMaxLength)
}

func normalizeSeverity(severity Severity) Severity {
	switch severity {
	case SeverityInfo, SeverityWarn, SeverityError, SeverityCritical:
		return severity
	default:
		return SeverityError
	}
}

func sanitizeData(data map[string]any, maxBytes int, description string) map[string]any {
	if len(data) == 0 {
		return nil
	}
	copy := cloneMap(data)
	encoded, err := json.Marshal(copy)
	if err != nil {
		return map[string]any{
			"truncated": true,
			"reason":    description + " could not be serialized",
		}
	}
	if len(encoded) > maxBytes {
		return map[string]any{
			"truncated": true,
			"reason":    description + " exceeded size limit",
		}
	}
	return copy
}

func (c *Client) now() string {
	return time.Now().In(c.location).Format(reportingTimeFormat)
}
