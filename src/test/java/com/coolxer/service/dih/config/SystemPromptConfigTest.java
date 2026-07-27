package com.coolxer.service.dih.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptConfigTest {

    private final SystemPromptConfig config = new SystemPromptConfig();

    @Test
    void dataAnalysisPromptEnforcesThreeStagesAndStopsWithoutAnalysisService() {
        String prompt = config.agentDataAnalysisSystemPromptTemplate().getTemplate();

        assertThat(prompt)
                .contains("dataset_preparation", "service_analysis", "report_output")
                .contains("analysis.confirm_dataset", "analysis.confirm_service_result")
                .contains("zenvis:data-analysis-record")
                .contains("分析目标、字段说明、查询条件和聚合数据")
                .contains("没有合适分析 MCP")
                .contains("只输出 zenvis:notice 并停止");
    }

    @Test
    void configurationPromptEnforcesValidationConfirmationApprovalAndReadBack() {
        String prompt = config.agentConfigManagementSystemPromptTemplate().getTemplate();

        assertThat(prompt)
                .contains("config.confirm_trial", "config.confirm_apply")
                .contains("config_validate", "config_ensure_root", "config_add", "config_apply", "config_read")
                .contains("validationStatus=blocked")
                .contains("approvalStatus=approved", "writeSucceeded=true", "readBackMatched=true");
    }
}
