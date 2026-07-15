package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class BusinessServiceHeartbeatAckVo {

    private String serviceCode;

    private String instanceId;

    private boolean registered;

    private Date receivedAt;

    private BusinessServiceEffectiveStatus effectiveStatus;

    private long offlineAfterSeconds;
}
