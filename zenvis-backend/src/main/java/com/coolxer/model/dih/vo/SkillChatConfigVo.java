package com.coolxer.model.dih.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 在 DIH 聊天输入区的展示和运行配置。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillChatConfigVo implements Serializable {

    private Boolean enabled = false;

    private String label;

    private String icon;

    private Integer order;

    @JsonAlias("agentType")
    private String agentType;

    private String prologue;

    @JsonAlias("promptSuggestions")
    private List<SkillChatPromptSuggestionVo> promptSuggestions = new ArrayList<>();
}
