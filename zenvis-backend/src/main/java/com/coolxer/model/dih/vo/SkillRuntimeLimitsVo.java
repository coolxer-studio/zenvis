package com.coolxer.model.dih.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Skill 单轮工具执行预算。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillRuntimeLimitsVo implements Serializable {

    @JsonAlias("maxToolCalls")
    private Integer maxToolCalls;

    @JsonAlias("maxRepeatedFailures")
    private Integer maxRepeatedFailures;

    @JsonAlias("maxToolResultChars")
    private Integer maxToolResultChars;

    @JsonAlias("maxAccumulatedToolResultChars")
    private Integer maxAccumulatedToolResultChars;

    @JsonAlias("maxAccumulatedToolResultTokens")
    private Integer maxAccumulatedToolResultTokens;

    /**
     * 单轮模型输出上限。为空时继承平台默认输出预算。
     */
    @JsonAlias("maxOutputTokens")
    private Integer maxOutputTokens;

    public SkillRuntimeLimitsVo(Integer maxToolCalls,
                                Integer maxRepeatedFailures,
                                Integer maxToolResultChars,
                                Integer maxAccumulatedToolResultChars,
                                Integer maxAccumulatedToolResultTokens) {
        this(maxToolCalls,
                maxRepeatedFailures,
                maxToolResultChars,
                maxAccumulatedToolResultChars,
                maxAccumulatedToolResultTokens,
                null);
    }
}
