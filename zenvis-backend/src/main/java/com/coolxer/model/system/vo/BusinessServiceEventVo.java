package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Map;

@Data
@Accessors(chain = true)
public class BusinessServiceEventVo {

    private Integer id;
    private String eventId;
    private Integer serviceInstanceId;
    private String serviceCode;
    private String instanceId;
    private String eventType;
    private BusinessServiceEventSeverity severity;
    private String title;
    private String message;
    private Date occurredTime;
    private String traceId;
    private Map<String, Object> data;
    private String remoteAddress;
    private Date createTime;
}
