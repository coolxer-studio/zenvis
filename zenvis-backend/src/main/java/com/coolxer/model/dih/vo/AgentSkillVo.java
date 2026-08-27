package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * DIH 内置智能体 Skill 入口视图对象。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentSkillVo implements Serializable {

    private String skillId;

    private String agentType;

    private String label;

    private String name;

    private String description;

    private Boolean enabled;

    private Integer order;

    private String path;

    private Date updateTime;
}
