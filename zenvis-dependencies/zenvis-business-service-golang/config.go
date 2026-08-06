package businessservice

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

const envPrefix = "ZENVIS_BUSINESS_SERVICE_"

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

// ConfigFromEnv 从 ZENVIS_BUSINESS_SERVICE_* 环境变量读取配置。
// 未设置的环境变量保留 DefaultConfig 的默认值。
func ConfigFromEnv() (Config, error) {
	config := DefaultConfig()
	var err error

	setStringFromEnv(&config.BaseURL, "BASE_URL")
	setStringFromEnv(&config.ServiceCode, "SERVICE_CODE")
	setStringFromEnv(&config.ServiceName, "SERVICE_NAME")
	setStringFromEnv(&config.InstanceID, "INSTANCE_ID")
	setStringFromEnv(&config.Version, "VERSION")
	setStringFromEnv(&config.Environment, "ENVIRONMENT")
	setStringFromEnv(&config.Host, "HOST")
	setStringFromEnv(&config.ManagementURL, "MANAGEMENT_URL")
	setStringFromEnv(&config.TimeZone, "TIME_ZONE")

	if value, ok := lookupEnv("ENABLED"); ok {
		enabled, parseErr := strconv.ParseBool(value)
		if parseErr != nil {
			return Config{}, fmt.Errorf("解析 %sENABLED: %w", envPrefix, parseErr)
		}
		config.Disabled = !enabled
	}
	if config.Port, err = intFromEnv("PORT", config.Port); err != nil {
		return Config{}, err
	}
	if config.EventQueueCapacity, err = intFromEnv("EVENT_QUEUE_CAPACITY", config.EventQueueCapacity); err != nil {
		return Config{}, err
	}
	if config.HeartbeatInterval, err = millisFromEnv("HEARTBEAT_INTERVAL_MILLIS", config.HeartbeatInterval); err != nil {
		return Config{}, err
	}
	if config.ConnectTimeout, err = millisFromEnv("CONNECT_TIMEOUT_MILLIS", config.ConnectTimeout); err != nil {
		return Config{}, err
	}
	if config.ReadTimeout, err = millisFromEnv("READ_TIMEOUT_MILLIS", config.ReadTimeout); err != nil {
		return Config{}, err
	}
	if value, ok := lookupEnv("METADATA"); ok {
		if err := json.Unmarshal([]byte(value), &config.Metadata); err != nil {
			return Config{}, fmt.Errorf("解析 %sMETADATA: %w", envPrefix, err)
		}
	}
	return config, nil
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

func setStringFromEnv(target *string, suffix string) {
	if value, ok := lookupEnv(suffix); ok {
		*target = value
	}
}

func lookupEnv(suffix string) (string, bool) {
	value, ok := os.LookupEnv(envPrefix + suffix)
	return strings.TrimSpace(value), ok
}

func intFromEnv(suffix string, fallback int) (int, error) {
	value, ok := lookupEnv(suffix)
	if !ok {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("解析 %s%s: %w", envPrefix, suffix, err)
	}
	return parsed, nil
}

func millisFromEnv(suffix string, fallback time.Duration) (time.Duration, error) {
	value, ok := lookupEnv(suffix)
	if !ok {
		return fallback, nil
	}
	millis, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("解析 %s%s: %w", envPrefix, suffix, err)
	}
	return time.Duration(millis) * time.Millisecond, nil
}
