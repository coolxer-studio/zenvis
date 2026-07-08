package com.coolxer.lubinsun.model;

import java.util.List;
import java.util.Map;

public record LubinsunSipLogLookupResult(
        List<Map<String, Object>> securityEvents,
        List<Map<String, Object>> securityAlarms,
        List<String> errors
) {
}
