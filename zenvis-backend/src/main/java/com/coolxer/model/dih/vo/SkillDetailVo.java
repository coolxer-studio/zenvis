package com.coolxer.model.dih.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 详情视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillDetailVo extends SkillVo {

    /**
     * Skill 入口文件内容
     */
    private String content;
}
