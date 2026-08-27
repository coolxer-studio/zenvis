package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.PageDto;
import lombok.Data;

@Data
public class McpServerSearchDto extends PageDto {

    private String keyword;

    private Boolean enabled;

    private Boolean connected;
}
