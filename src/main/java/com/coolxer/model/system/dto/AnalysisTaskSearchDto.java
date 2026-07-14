package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI分析任务搜索对象
 */
@Data
@NoArgsConstructor
public class AnalysisTaskSearchDto extends SortPageDto {

    /**
     * 任务名称
     */
    private String name;

    /**
     * 状态
     */
    private AnalysisTaskStatus status;

    /**
     * 模型
     */
    private String model;

    private AnalysisTaskApprovalMode approvalMode;
}
