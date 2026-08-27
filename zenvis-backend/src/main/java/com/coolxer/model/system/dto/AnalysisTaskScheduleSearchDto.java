package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AnalysisTaskScheduleSearchDto extends SortPageDto {

    private String name;

    private Boolean enabled;
}
