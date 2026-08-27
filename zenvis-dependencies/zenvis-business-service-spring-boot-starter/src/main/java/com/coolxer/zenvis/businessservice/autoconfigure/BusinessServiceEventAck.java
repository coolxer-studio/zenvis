package com.coolxer.zenvis.businessservice.autoconfigure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record BusinessServiceEventAck(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("accepted_at") String acceptedAt,
        @JsonProperty("duplicate") boolean duplicate) {
}
