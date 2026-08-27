package businessservice

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHTTPTransportSendsExactPublicPathsAndSnakeCaseBodies(t *testing.T) {
	var paths []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		paths = append(paths, r.URL.Path)
		if r.Method != http.MethodPost {
			t.Errorf("method = %s", r.Method)
		}
		if got := r.Header.Get("Authorization"); got != "" {
			t.Errorf("Authorization = %q", got)
		}
		if got := r.Header.Get("Content-Type"); got != "application/json" {
			t.Errorf("Content-Type = %q", got)
		}

		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Errorf("decode body: %v", err)
		}
		if _, ok := body["service_code"]; !ok {
			t.Errorf("missing service_code: %#v", body)
		}
		if _, exists := body["serviceCode"]; exists {
			t.Errorf("unexpected camelCase body: %#v", body)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"status":0,"msg":"请求成功","data":{}}`)
	}))
	defer server.Close()

	config := applyDefaults(Config{
		BaseURL: server.URL + "/",
		Logger:  log.New(io.Discard, "", 0),
	})
	transport := newHTTPTransport(config)
	if !transport.reportHeartbeat(context.Background(), heartbeatRequest{
		ServiceCode: "synap-server",
		ServiceName: "Synap Server",
		InstanceID:  "synap-server-pod-1-11099",
		Status:      statusUp,
	}) {
		t.Fatal("heartbeat failed")
	}
	if !transport.reportEvent(context.Background(), eventRequest{
		EventID:     "01234567-89ab-cdef-0123-456789abcdef",
		ServiceCode: "synap-server",
		InstanceID:  "synap-server-pod-1-11099",
		EventType:   "RULE_EXECUTION_FAILED",
		Severity:    SeverityError,
		Title:       "规则执行失败",
	}) {
		t.Fatal("event failed")
	}

	want := []string{heartbeatPath, eventsPath}
	if len(paths) != len(want) {
		t.Fatalf("paths = %#v", paths)
	}
	for i := range want {
		if paths[i] != want[i] {
			t.Fatalf("paths[%d] = %q, want %q", i, paths[i], want[i])
		}
	}
}

func TestHTTPTransportTreatsHTTPBusinessAndInvalidResponsesAsFailure(t *testing.T) {
	tests := []struct {
		name       string
		statusCode int
		body       string
	}{
		{name: "http", statusCode: http.StatusBadGateway, body: `{"status":0}`},
		{name: "business", statusCode: http.StatusOK, body: `{"status":400,"msg":"参数错误"}`},
		{name: "missing status", statusCode: http.StatusOK, body: `{"msg":"请求成功"}`},
		{name: "invalid json", statusCode: http.StatusOK, body: `{not-json`},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.WriteHeader(test.statusCode)
				_, _ = io.WriteString(w, test.body)
			}))
			defer server.Close()
			transport := newHTTPTransport(applyDefaults(Config{
				BaseURL: server.URL,
				Logger:  log.New(io.Discard, "", 0),
			}))
			if transport.reportHeartbeat(context.Background(), heartbeatRequest{}) {
				t.Fatal("expected failure")
			}
		})
	}
}
