package businessservice

import (
	"context"
	"io"
	"log"
	"strings"
	"sync"
	"testing"
	"time"
)

type recordingTransport struct {
	mu               sync.Mutex
	heartbeatResults []bool
	operations       []string
	heartbeats       []heartbeatRequest
	events           []eventRequest
	notify           chan struct{}
}

func newRecordingTransport() *recordingTransport {
	return &recordingTransport{notify: make(chan struct{}, 20)}
}

func (t *recordingTransport) reportHeartbeat(_ context.Context, request heartbeatRequest) bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.operations = append(t.operations, "heartbeat")
	t.heartbeats = append(t.heartbeats, request)
	result := true
	if len(t.heartbeatResults) > 0 {
		result = t.heartbeatResults[0]
		t.heartbeatResults = t.heartbeatResults[1:]
	}
	select {
	case t.notify <- struct{}{}:
	default:
	}
	return result
}

func (t *recordingTransport) reportEvent(_ context.Context, request eventRequest) bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.operations = append(t.operations, "event")
	t.events = append(t.events, request)
	select {
	case t.notify <- struct{}{}:
	default:
	}
	return true
}

func (t *recordingTransport) snapshot() ([]string, []heartbeatRequest, []eventRequest) {
	t.mu.Lock()
	defer t.mu.Unlock()
	return append([]string(nil), t.operations...), append([]heartbeatRequest(nil), t.heartbeats...), append([]eventRequest(nil), t.events...)
}

func newTestClient(transport transport) *Client {
	client := New(Config{
		ServiceCode:       "synap-server",
		ServiceName:       "Synap Server",
		Host:              "pod-1",
		Port:              11099,
		Environment:       "test",
		HeartbeatInterval: time.Hour,
		Logger:            log.New(io.Discard, "", 0),
	})
	client.transport = transport
	return client
}

func waitForOperations(t *testing.T, transport *recordingTransport, count int) {
	t.Helper()
	deadline := time.NewTimer(time.Second)
	defer deadline.Stop()
	for {
		operations, _, _ := transport.snapshot()
		if len(operations) >= count {
			return
		}
		select {
		case <-transport.notify:
		case <-deadline.C:
			t.Fatalf("timed out waiting for %d operations, got %#v", count, operations)
		}
	}
}

func TestClientStartsImmediatelyAndReportsStartedOnlyOnce(t *testing.T) {
	transport := newRecordingTransport()
	client := newTestClient(transport)
	client.Start(context.Background())
	waitForOperations(t, transport, 2)

	client.sendScheduledHeartbeat(context.Background())
	operations, heartbeats, events := transport.snapshot()
	if len(heartbeats) != 2 {
		t.Fatalf("heartbeats = %d, operations=%#v", len(heartbeats), operations)
	}
	if len(events) != 1 || events[0].EventType != serviceStarted {
		t.Fatalf("events = %#v", events)
	}
	if err := client.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
}

func TestClientRetriesRegistrationOnNextHeartbeat(t *testing.T) {
	transport := newRecordingTransport()
	transport.heartbeatResults = []bool{false, true}
	client := newTestClient(transport)

	client.sendScheduledHeartbeat(context.Background())
	_, _, events := transport.snapshot()
	if len(events) != 0 {
		t.Fatalf("events after failed heartbeat = %#v", events)
	}
	client.sendScheduledHeartbeat(context.Background())
	_, _, events = transport.snapshot()
	if len(events) != 1 || events[0].EventType != serviceStarted {
		t.Fatalf("events = %#v", events)
	}
}

func TestClientRegistersBeforeRuntimeEventAndUsesUUID(t *testing.T) {
	transport := newRecordingTransport()
	client := newTestClient(transport)
	client.started.Store(true)
	ctx, cancel := context.WithCancel(context.Background())
	client.workers.Add(1)
	go client.runEventWorker(ctx)

	client.ReportEvent(
		"RULE_EXECUTION_FAILED",
		SeverityError,
		"规则失败",
		"missing script",
		"trace-1",
		map[string]any{"script_name": "MissingRule.groovy"},
	)
	waitForOperations(t, transport, 2)
	cancel()
	client.workers.Wait()

	operations, _, events := transport.snapshot()
	if operations[0] != "heartbeat" || operations[1] != "event" {
		t.Fatalf("operations = %#v", operations)
	}
	if len(events) != 1 || len(events[0].EventID) != 36 || events[0].TraceID != "trace-1" {
		t.Fatalf("event = %#v", events)
	}
}

func TestClientReportsStoppingBeforeDownHeartbeat(t *testing.T) {
	transport := newRecordingTransport()
	client := newTestClient(transport)
	client.sendScheduledHeartbeat(context.Background())

	transport.mu.Lock()
	transport.operations = nil
	transport.heartbeats = nil
	transport.events = nil
	transport.mu.Unlock()

	if err := client.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	operations, heartbeats, events := transport.snapshot()
	if len(operations) != 2 || operations[0] != "event" || operations[1] != "heartbeat" {
		t.Fatalf("operations = %#v", operations)
	}
	if len(events) != 1 || events[0].EventType != serviceStopping {
		t.Fatalf("events = %#v", events)
	}
	if len(heartbeats) != 1 || heartbeats[0].Status != statusDown || heartbeats[0].StatusMessage != "stopping" {
		t.Fatalf("heartbeats = %#v", heartbeats)
	}
}

func TestClientNormalizesAndTruncatesEvent(t *testing.T) {
	client := newTestClient(newRecordingTransport())
	event := client.buildEvent(
		"event-1",
		"@订单 失败",
		"INVALID",
		"",
		strings.Repeat("m", messageMaxLength+10),
		strings.Repeat("t", traceIDMaxLength+10),
		map[string]any{"payload": strings.Repeat("x", eventDataMaxBytes)},
	)
	if event.EventType != "EVENT_______" {
		t.Fatalf("EventType = %q", event.EventType)
	}
	if event.Severity != SeverityError {
		t.Fatalf("Severity = %q", event.Severity)
	}
	if event.Title != event.EventType {
		t.Fatalf("Title = %q", event.Title)
	}
	if len([]rune(event.Message)) != messageMaxLength || len([]rune(event.TraceID)) != traceIDMaxLength {
		t.Fatalf("message/trace lengths = %d/%d", len([]rune(event.Message)), len([]rune(event.TraceID)))
	}
	if truncated, _ := event.Data["truncated"].(bool); !truncated {
		t.Fatalf("Data = %#v", event.Data)
	}
}

func TestClientQueueFullDoesNotBlockCaller(t *testing.T) {
	client := New(Config{
		ServiceCode:        "queue-test",
		EventQueueCapacity: 1,
		Logger:             log.New(io.Discard, "", 0),
	})
	client.started.Store(true)

	client.ReportEvent("FIRST", SeverityInfo, "first", "", "", nil)
	done := make(chan struct{})
	go func() {
		client.ReportEvent("SECOND", SeverityWarn, "second", "", "", nil)
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(100 * time.Millisecond):
		t.Fatal("ReportEvent blocked when queue was full")
	}
	if got := len(client.events); got != 1 {
		t.Fatalf("queue length = %d", got)
	}
}

func TestDisabledClientIsSafeNoOp(t *testing.T) {
	client := New(Config{Disabled: true, Logger: log.New(io.Discard, "", 0)})
	client.Start(context.Background())
	client.ReportEvent("IGNORED", SeverityInfo, "ignored", "", "", nil)
	if err := client.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	if client.started.Load() {
		t.Fatal("disabled client must not start")
	}
}

func TestSanitizeDataHandlesSerializationFailure(t *testing.T) {
	cyclic := map[string]any{}
	cyclic["self"] = cyclic
	got := sanitizeData(cyclic, eventDataMaxBytes, "event data")
	if truncated, _ := got["truncated"].(bool); !truncated {
		t.Fatalf("sanitizeData = %#v", got)
	}
	if got["reason"] != "event data could not be serialized" {
		t.Fatalf("reason = %#v", got["reason"])
	}
}
