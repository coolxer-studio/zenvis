package businessservice

import (
	"strings"
	"testing"
)

func TestResolveIdentityDerivesStableAndValidInstanceID(t *testing.T) {
	config := Config{
		ServiceCode: "synap server",
		ServiceName: "Synap Server",
		Host:        "pod name",
		Port:        11099,
	}

	got := resolveIdentity(applyDefaults(config))
	if got.ServiceCode != "synap-server" {
		t.Fatalf("ServiceCode = %q", got.ServiceCode)
	}
	if got.InstanceID != "synap-server-pod-name-11099" {
		t.Fatalf("InstanceID = %q", got.InstanceID)
	}
}

func TestResolveIdentityUsesGoFallbacks(t *testing.T) {
	got := resolveIdentity(applyDefaults(Config{Host: "pod-1"}))
	if got.ServiceCode != "go-service" {
		t.Fatalf("ServiceCode = %q", got.ServiceCode)
	}
	if got.ServiceName != "go-service" {
		t.Fatalf("ServiceName = %q", got.ServiceName)
	}
	if got.Environment != "default" {
		t.Fatalf("Environment = %q", got.Environment)
	}
	if got.InstanceID != "go-service-pod-1-no-port" {
		t.Fatalf("InstanceID = %q", got.InstanceID)
	}
}

func TestResolveIdentityNormalizesAndLimitsConfiguredInstanceID(t *testing.T) {
	got := resolveIdentity(applyDefaults(Config{
		InstanceID: "@" + strings.Repeat("instance id/", 20),
	}))
	if length := len([]rune(got.InstanceID)); length != instanceIDMaxLength {
		t.Fatalf("InstanceID length = %d", length)
	}
	if first, _ := firstRune(got.InstanceID); !isASCIIAlphaNumeric(first) {
		t.Fatalf("InstanceID must start with alphanumeric: %q", got.InstanceID)
	}
	for _, r := range got.InstanceID {
		if !isASCIIAlphaNumeric(r) && r != '.' && r != '_' && r != ':' && r != '-' {
			t.Fatalf("InstanceID contains invalid rune %q", r)
		}
	}
}
