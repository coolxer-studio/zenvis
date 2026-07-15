package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class BusinessServiceEventAckVo {

    private String eventId;

    private Date acceptedAt;

    private boolean duplicate;
}
