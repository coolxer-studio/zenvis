package com.coolxer.zenvis.businessservice.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record BusinessServiceHeartbeatRequest(
        @JsonProperty("service_code") String serviceCode,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("status") BusinessServiceStatus status,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("version") String version,
        @JsonProperty("environment") String environment,
        @JsonProperty("host") String host,
        @JsonProperty("port") Integer port,
        @JsonProperty("management_url") String managementUrl,
        @JsonProperty("heartbeat_time") String heartbeatTime,
        @JsonProperty("metadata") Map<String, Object> metadata) {
}
