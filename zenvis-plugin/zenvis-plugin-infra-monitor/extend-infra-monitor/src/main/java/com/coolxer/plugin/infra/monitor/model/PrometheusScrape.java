package com.coolxer.plugin.infra.monitor.model;

public record PrometheusScrape(
        String job,
        String target,
        String metricsPath,
        String targetParameter
) {
}
