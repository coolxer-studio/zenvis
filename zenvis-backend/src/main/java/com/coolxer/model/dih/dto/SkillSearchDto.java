package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.PageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 搜索传输对象
 */
@Data
@NoArgsConstructor
public class SkillSearchDto extends PageDto {

    /**
     * 关键词，匹配 ID、名称、描述和标签
     */
    private String keyword;

    /**
     * 智能体类型，如 agent_visualization_chart
     */
    private String agentType;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否自定义 Skill。true 只看页面创建的自定义 Skill，false 只看内置/插件 Skill
     */
    private Boolean custom;

    /**
     * 来源，支持按插件包名、custom 或 builtin 模糊筛选。
     */
    private String source;
}
