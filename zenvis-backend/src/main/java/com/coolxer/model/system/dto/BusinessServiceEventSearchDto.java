package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import com.coolxer.model.base.dto.PageDto;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
public class BusinessServiceEventSearchDto extends PageDto {

    private String keyword;

    private String serviceCode;

    private String instanceId;

    private BusinessServiceEventSeverity severity;

    private String eventType;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
}
