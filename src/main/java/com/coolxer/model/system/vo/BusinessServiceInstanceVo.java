package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import com.coolxer.commons.enums.BusinessServiceReportedStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Map;

@Data
@Accessors(chain = true)
public class BusinessServiceInstanceVo {

    private Integer id;
    private String serviceCode;
    private String serviceName;
    private String instanceId;
    private BusinessServiceReportedStatus reportedStatus;
    private BusinessServiceEffectiveStatus effectiveStatus;
    private boolean online;
    private long secondsSinceHeartbeat;
    private String statusMessage;
    private String version;
    private String environment;
    private String host;
    private Integer port;
    private String managementUrl;
    private Map<String, Object> metadata;
    private String remoteAddress;
    private Date firstHeartbeatTime;
    private Date lastHeartbeatTime;
    private Date reportedHeartbeatTime;
    private Date lastEventTime;
    private Date createTime;
    private Date updateTime;
}
