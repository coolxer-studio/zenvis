package com.coolxer.lubinsun.model;

import com.coolxer.model.base.dto.SortPageDto;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LubinsunTaskSearchDto extends SortPageDto {

    private String name;

    private String skill;

    @JsonAlias({"ip", "target_ip"})
    private String ip;

    private LubinsunTaskStatus status;

    @JsonAlias("run_id")
    private String runId;
}
