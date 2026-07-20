package com.coolxer.zenvis.businessservice.autoconfigure;

import java.util.Map;

record BusinessServiceIdentity(
        String serviceCode,
        String serviceName,
        String instanceId,
        String version,
        String environment,
        String host,
        Integer port,
        String managementUrl,
        Map<String, Object> metadata) {
}
