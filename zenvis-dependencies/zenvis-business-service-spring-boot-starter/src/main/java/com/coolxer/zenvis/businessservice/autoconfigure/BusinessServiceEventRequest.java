package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record BusinessServiceEventRequest(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("service_code") String serviceCode,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("severity") BusinessServiceEventSeverity severity,
        @JsonProperty("title") String title,
        @JsonProperty("message") String message,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("data") Map<String, Object> data) {
}
