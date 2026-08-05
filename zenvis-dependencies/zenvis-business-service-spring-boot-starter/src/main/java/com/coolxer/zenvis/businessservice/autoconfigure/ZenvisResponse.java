package com.coolxer.zenvis.businessservice.autoconfigure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record ZenvisResponse<T>(Integer status, String msg, T data) {

    boolean successful() {
        return Integer.valueOf(0).equals(status);
    }
}
