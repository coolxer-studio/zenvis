package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class BusinessServiceSummaryVo {

    private long serviceCount;
    private long instanceCount;
    private long upCount;
    private long degradedCount;
    private long downCount;
    private long offlineCount;
    private long abnormalCount;
    private long eventCount24h;
    private Date checkedAt;
}
