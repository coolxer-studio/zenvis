package businessservice

import (
	"log"
	"net/http"
	"strings"
	"time"
)

// HTTPDoer 是 SDK 发起 HTTP 请求所需的最小接口。
type HTTPDoer interface {
	Do(*http.Request) (*http.Response, error)
}

// Config 定义 Zenvis 业务服务上报配置。
//
// Config 的零值可用：上报默认开启，Zenvis 地址默认为 http://localhost:11001。
type Config struct {
	BaseURL            string
	ServiceCode        string
	ServiceName        string
	InstanceID         string
	Version            string
	Environment        string
	Host               string
	Port               int
	ManagementURL      string
	HeartbeatInterval  time.Duration
	ConnectTimeout     time.Duration
	ReadTimeout        time.Duration
	EventQueueCapacity int
	TimeZone           string
	Metadata           map[string]any

	// Disabled 显式禁用全部上报。禁用后的 Client 可安全调用。
	Disabled bool

	// HTTPClient 可选。为空时 SDK 创建独立的标准库 HTTP 客户端。
	HTTPClient HTTPDoer
	// Logger 可选。为空时使用 log.Default()。
	Logger *log.Logger
}

// DefaultConfig 返回与 Spring Boot Starter 对齐的默认配置。
func DefaultConfig() Config {
	return Config{
		BaseURL:            "http://localhost:11001",
		HeartbeatInterval:  30 * time.Second,
		ConnectTimeout:     2 * time.Second,
		ReadTimeout:        3 * time.Second,
		EventQueueCapacity: 100,
		TimeZone:           "Asia/Shanghai",
	}
}

func applyDefaults(config Config) Config {
	defaults := DefaultConfig()
	if strings.TrimSpace(config.BaseURL) == "" {
		config.BaseURL = defaults.BaseURL
	}
	if config.HeartbeatInterval <= 0 {
		config.HeartbeatInterval = defaults.HeartbeatInterval
	}
	if config.ConnectTimeout <= 0 {
		config.ConnectTimeout = defaults.ConnectTimeout
	}
	if config.ReadTimeout <= 0 {
		config.ReadTimeout = defaults.ReadTimeout
	}
	if config.EventQueueCapacity < 1 {
		config.EventQueueCapacity = defaults.EventQueueCapacity
	}
	if strings.TrimSpace(config.TimeZone) == "" {
		config.TimeZone = defaults.TimeZone
	}
	if config.Logger == nil {
		config.Logger = log.Default()
	}
	return config
}
