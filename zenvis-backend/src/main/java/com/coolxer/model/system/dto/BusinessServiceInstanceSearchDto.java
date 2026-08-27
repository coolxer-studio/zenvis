package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import com.coolxer.model.base.dto.PageDto;
import lombok.Data;

@Data
public class BusinessServiceInstanceSearchDto extends PageDto {

    private String keyword;

    private String environment;

    private BusinessServiceEffectiveStatus status;
}
