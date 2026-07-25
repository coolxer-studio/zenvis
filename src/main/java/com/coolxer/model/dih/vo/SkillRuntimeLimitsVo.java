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
}
