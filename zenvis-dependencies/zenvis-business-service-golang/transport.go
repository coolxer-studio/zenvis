package businessservice

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
)

const (
	heartbeatPath        = "/api/v1/public/business-services/heartbeat"
	eventsPath           = "/api/v1/public/business-services/events"
	maxResponseBodyBytes = 1024 * 1024
)

type transport interface {
	reportHeartbeat(context.Context, heartbeatRequest) bool
	reportEvent(context.Context, eventRequest) bool
}

type httpTransport struct {
	baseURL string
	client  HTTPDoer
	logger  logger
}

func newHTTPTransport(config Config) *httpTransport {
	client := config.HTTPClient
	if client == nil {
		httpRoundTripper := http.DefaultTransport.(*http.Transport).Clone()
		httpRoundTripper.DialContext = (&net.Dialer{
			Timeout: config.ConnectTimeout,
		}).DialContext
		client = &http.Client{
			Transport: httpRoundTripper,
			Timeout:   config.ConnectTimeout + config.ReadTimeout,
		}
	}
	return &httpTransport{
		baseURL: strings.TrimRight(strings.TrimSpace(config.BaseURL), "/"),
		client:  client,
		logger:  config.Logger,
	}
}

func (t *httpTransport) reportHeartbeat(ctx context.Context, request heartbeatRequest) bool {
	return t.post(ctx, heartbeatPath, request, "心跳")
}

func (t *httpTransport) reportEvent(ctx context.Context, request eventRequest) bool {
	return t.post(ctx, eventsPath, request, "事件")
}

func (t *httpTransport) post(ctx context.Context, path string, payload any, operation string) bool {
	body, err := json.Marshal(payload)
	if err != nil {
		t.logger.Printf("Zenvis %s上报失败: JSON 序列化失败: %v", operation, err)
		return false
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, t.baseURL+path, bytes.NewReader(body))
	if err != nil {
		t.logger.Printf("Zenvis %s上报失败: %v", operation, err)
		return false
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	response, err := t.client.Do(req)
	if err != nil {
		t.logger.Printf("Zenvis %s上报失败: %v", operation, err)
		return false
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, maxResponseBodyBytes))
		t.logger.Printf("Zenvis %s上报失败: HTTP %d", operation, response.StatusCode)
		return false
	}

	var result struct {
		Status *int   `json:"status"`
		Msg    string `json:"msg"`
	}
	decoder := json.NewDecoder(io.LimitReader(response.Body, maxResponseBodyBytes))
	if err := decoder.Decode(&result); err != nil {
		t.logger.Printf("Zenvis %s上报失败: 无效响应: %v", operation, err)
		return false
	}
	if result.Status == nil || *result.Status != 0 {
		status := "null"
		if result.Status != nil {
			status = fmt.Sprint(*result.Status)
		}
		t.logger.Printf("Zenvis %s上报业务失败: status=%s, msg=%s", operation, status, result.Msg)
		return false
	}
	return true
}

type logger interface {
	Printf(string, ...any)
}
