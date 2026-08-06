package businessservice

import (
	"os"
	"strconv"
	"strings"
	"unicode"
)

const (
	serviceCodeMaxLength   = 64
	serviceNameMaxLength   = 128
	instanceIDMaxLength    = 128
	hostMaxLength          = 255
	versionMaxLength       = 64
	environmentMaxLength   = 64
	managementURLMaxLength = 512
)

func resolveIdentity(config Config) identity {
	serviceCode := normalizeIdentifier(config.ServiceCode, "go-service", false, serviceCodeMaxLength)
	serviceName := truncate(defaultIfBlank(config.ServiceName, serviceCode), serviceNameMaxLength)
	host := truncate(defaultIfBlank(config.Host, localHost()), hostMaxLength)
	port := config.Port
	if port < 1 || port > 65535 {
		port = 0
	}
	portPart := "no-port"
	if port != 0 {
		portPart = strconv.Itoa(port)
	}
	instanceID := normalizeIdentifier(
		config.InstanceID,
		serviceCode+"-"+host+"-"+portPart,
		true,
		instanceIDMaxLength,
	)

	return identity{
		ServiceCode:   serviceCode,
		ServiceName:   serviceName,
		InstanceID:    instanceID,
		Version:       nullableTruncate(config.Version, versionMaxLength),
		Environment:   nullableTruncate(defaultIfBlank(config.Environment, "default"), environmentMaxLength),
		Host:          host,
		Port:          port,
		ManagementURL: nullableTruncate(config.ManagementURL, managementURLMaxLength),
		Metadata:      cloneMap(config.Metadata),
	}
}

func normalizeIdentifier(value, fallback string, allowColon bool, maxLength int) string {
	normalized := strings.TrimSpace(defaultIfBlank(value, fallback))
	var builder strings.Builder
	for _, r := range normalized {
		allowed := isASCIIAlphaNumeric(r) || r == '.' || r == '_' || r == '-'
		if allowColon {
			allowed = allowed || r == ':'
		}
		if allowed {
			builder.WriteRune(r)
		} else {
			builder.WriteByte('-')
		}
	}
	normalized = builder.String()
	if normalized == "" {
		normalized = fallback
	}
	first, _ := firstRune(normalized)
	if !isASCIIAlphaNumeric(first) {
		normalized = "service-" + normalized
	}
	return truncate(normalized, maxLength)
}

func localHost() string {
	host, err := os.Hostname()
	if err != nil || strings.TrimSpace(host) == "" {
		return "localhost"
	}
	return host
}

func defaultIfBlank(value, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func nullableTruncate(value string, maxLength int) string {
	if strings.TrimSpace(value) == "" {
		return ""
	}
	return truncate(strings.TrimSpace(value), maxLength)
}

func truncate(value string, maxLength int) string {
	runes := []rune(value)
	if len(runes) <= maxLength {
		return value
	}
	return string(runes[:maxLength])
}

func firstRune(value string) (rune, bool) {
	for _, r := range value {
		return r, true
	}
	return 0, false
}

func isASCIIAlphaNumeric(r rune) bool {
	return r <= unicode.MaxASCII && ((r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9'))
}

func cloneMap(source map[string]any) map[string]any {
	if len(source) == 0 {
		return nil
	}
	result := make(map[string]any, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}
