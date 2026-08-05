package com.coolxer.zenvis.businessservice.autoconfigure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record BusinessServiceHeartbeatAck(
        @JsonProperty("service_code") String serviceCode,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("registered") boolean registered,
        @JsonProperty("received_at") String receivedAt,
        @JsonProperty("effective_status") String effectiveStatus,
        @JsonProperty("offline_after_seconds") long offlineAfterSeconds) {
}
