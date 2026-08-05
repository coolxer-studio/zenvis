package com.coolxer.zenvis.businessservice.autoconfigure;

interface ZenvisBusinessServiceTransport {

    boolean reportHeartbeat(BusinessServiceHeartbeatRequest request);

    boolean reportEvent(BusinessServiceEventRequest request);
}
