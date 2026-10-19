package com.coolxer.plugin.infra.monitor.model;

import java.util.List;

public record MonitorOverview(
        String generatedAt,
        MonitorSummary summary,
        List<EndpointStatus> endpoints
) {
}
