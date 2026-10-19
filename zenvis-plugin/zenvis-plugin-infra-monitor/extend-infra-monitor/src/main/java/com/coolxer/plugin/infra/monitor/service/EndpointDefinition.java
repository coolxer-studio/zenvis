package com.coolxer.plugin.infra.monitor.service;

import com.coolxer.plugin.infra.monitor.model.PrometheusScrape;

public record EndpointDefinition(
        String id,
        String name,
        String category,
        String mode,
        String description,
        String url,
        String target,
        String verificationMetric,
        VerificationMode verificationMode,
        PrometheusScrape prometheus
) {
    public enum VerificationMode {
        POSITIVE,
        PRESENT
    }
}
