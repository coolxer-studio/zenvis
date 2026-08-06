package businessservice

// Reporter 是可注入业务组件的异步事件上报接口。
type Reporter interface {
	ReportEvent(eventType string, severity Severity, title, message, traceID string, data map[string]any)
}

// Severity 表示业务服务事件严重级别。
type Severity string

const (
	SeverityInfo     Severity = "INFO"
	SeverityWarn     Severity = "WARN"
	SeverityError    Severity = "ERROR"
	SeverityCritical Severity = "CRITICAL"
)

type serviceStatus string

const (
	statusUp   serviceStatus = "UP"
	statusDown serviceStatus = "DOWN"
)

const (
	serviceStarted  = "SERVICE_STARTED"
	serviceStopping = "SERVICE_STOPPING"
)

type identity struct {
	ServiceCode   string
	ServiceName   string
	InstanceID    string
	Version       string
	Environment   string
	Host          string
	Port          int
	ManagementURL string
	Metadata      map[string]any
}

type heartbeatRequest struct {
	ServiceCode   string         `json:"service_code"`
	ServiceName   string         `json:"service_name"`
	InstanceID    string         `json:"instance_id"`
	Status        serviceStatus  `json:"status"`
	StatusMessage string         `json:"status_message,omitempty"`
	Version       string         `json:"version,omitempty"`
	Environment   string         `json:"environment,omitempty"`
	Host          string         `json:"host,omitempty"`
	Port          *int           `json:"port,omitempty"`
	ManagementURL string         `json:"management_url,omitempty"`
	HeartbeatTime string         `json:"heartbeat_time,omitempty"`
	Metadata      map[string]any `json:"metadata,omitempty"`
}

type eventRequest struct {
	EventID     string         `json:"event_id"`
	ServiceCode string         `json:"service_code"`
	InstanceID  string         `json:"instance_id"`
	EventType   string         `json:"event_type"`
	Severity    Severity       `json:"severity"`
	Title       string         `json:"title"`
	Message     string         `json:"message,omitempty"`
	OccurredAt  string         `json:"occurred_at,omitempty"`
	TraceID     string         `json:"trace_id,omitempty"`
	Data        map[string]any `json:"data,omitempty"`
}
