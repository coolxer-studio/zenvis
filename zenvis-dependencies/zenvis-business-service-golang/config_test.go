package businessservice

import (
	"testing"
	"time"
)

func TestConfigFromEnv(t *testing.T) {
	t.Setenv(envPrefix+"ENABLED", "false")
	t.Setenv(envPrefix+"BASE_URL", "http://zenvis.test:11001")
	t.Setenv(envPrefix+"SERVICE_CODE", "order-api")
	t.Setenv(envPrefix+"PORT", "18080")
	t.Setenv(envPrefix+"HEARTBEAT_INTERVAL_MILLIS", "15000")
	t.Setenv(envPrefix+"METADATA", `{"zone":"az-1"}`)

	config, err := ConfigFromEnv()
	if err != nil {
		t.Fatal(err)
	}
	if !config.Disabled || config.BaseURL != "http://zenvis.test:11001" || config.ServiceCode != "order-api" {
		t.Fatalf("config = %#v", config)
	}
	if config.Port != 18080 || config.HeartbeatInterval != 15*time.Second {
		t.Fatalf("port/interval = %d/%s", config.Port, config.HeartbeatInterval)
	}
	if config.Metadata["zone"] != "az-1" {
		t.Fatalf("metadata = %#v", config.Metadata)
	}
}

func TestConfigFromEnvRejectsInvalidValues(t *testing.T) {
	t.Setenv(envPrefix+"PORT", "not-a-number")
	if _, err := ConfigFromEnv(); err == nil {
		t.Fatal("expected error")
	}
}
