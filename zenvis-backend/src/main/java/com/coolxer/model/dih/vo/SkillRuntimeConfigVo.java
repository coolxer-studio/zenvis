package com.coolxer.model.dih.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Skill 在 DIH 中的运行时能力边界。
 *
 * <p>该配置为可选项；没有选择 Skill 时继续继承 Agent 的现有工具范围。
 * 已选择 Skill 但未声明 tools 时采用 fail-closed，不向模型暴露工具。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillRuntimeConfigVo implements Serializable {

    public static final String PROMPT_MODE_SKILL_ONLY = "skill_only";

    /**
     * 提示词模式。skill_only 表示以 Skill 内容为主，不叠加业务 Agent 的固定流程。
     */
    @JsonAlias("promptMode")
    private String promptMode;

    /**
     * 允许暴露给模型的本地及外部 MCP 工具。
     */
    private SkillRuntimeToolsVo tools;

    /**
     * 单轮工具调用和结果预算。
     */
    private SkillRuntimeLimitsVo limits;
}
