package com.coolxer.zenvis.businessservice;

import java.util.Map;

/**
 * 向 Zenvis 上报业务服务运行事件。
 */
@FunctionalInterface
public interface BusinessServiceReporter {

    /**
     * 异步、尽力上报一个服务运行事件。实现不会向业务调用方传播上报异常。
     */
    void reportEvent(String eventType,
                     BusinessServiceEventSeverity severity,
                     String title,
                     String message,
                     String traceId,
                     Map<String, Object> data);
}
