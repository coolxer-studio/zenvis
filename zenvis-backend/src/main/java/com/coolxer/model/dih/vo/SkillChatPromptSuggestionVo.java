package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Skill 聊天开场提示建议。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillChatPromptSuggestionVo implements Serializable {

    private String label;

    private String prompt;
}
