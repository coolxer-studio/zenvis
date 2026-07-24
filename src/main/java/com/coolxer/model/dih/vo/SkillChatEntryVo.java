package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DIH 聊天输入区可选择的 Skill 入口。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillChatEntryVo implements Serializable {

    private String skillId;

    private String chatType;

    private String agentType;

    private String label;

    private String description;

    private String icon;

    private Integer order;
}
