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
     * 智能体类型，如 agent_inspect
     */
    private String agentType;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
