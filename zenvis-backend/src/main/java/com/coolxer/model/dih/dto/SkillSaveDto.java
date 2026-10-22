package com.coolxer.model.dih.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 页面创建/更新自定义 Skill 的传输对象。字段遵循全局 snake_case 命名策略。
 */
@Data
@NoArgsConstructor
public class SkillSaveDto implements Serializable {

    /**
     * Skill 唯一标识。创建时必填，更新时以路径变量为准。
     */
    private String id;

    private String name;

    private String description;

    private String version;

    private String author;

    private List<String> agentTypes;

    private List<String> tags;

    private Boolean enabled;

    /**
     * 入口文件内容（SKILL.md）。创建时必填；更新时为 null 表示保留原内容。
     */
    private String content;

    /**
     * 绑定的 RAG 知识库 ID 列表。
     */
    private List<Integer> knowledgeBaseIds;

    /**
     * 提示词模式，如 skill_only。
     */
    private String promptMode;

    /**
     * 内置 MCP 服务工具白名单。
     */
    private List<ServiceToolsDto> localTools;

    /**
     * 外部 MCP 服务工具白名单，server 为服务 code。
     */
    private List<ServerToolsDto> mcpTools;

    private Integer maxToolCalls;

    private Integer maxRepeatedFailures;

    private Integer maxToolResultChars;

    private Integer maxAccumulatedToolResultChars;

    /**
     * 单轮模型输出上限。为空时继承平台默认输出预算。
     */
    private Integer maxOutputTokens;

    /**
     * 是否开放 DIH 聊天入口。
     */
    private Boolean chatEnabled;

    private String chatLabel;

    private String chatIcon;

    private Integer chatOrder;

    private String chatAgentType;

    private String chatPrologue;

    @Data
    @NoArgsConstructor
    public static class ServiceToolsDto implements Serializable {
        private String service;
        private List<String> tools;
    }

    @Data
    @NoArgsConstructor
    public static class ServerToolsDto implements Serializable {
        private String server;
        private List<String> tools;
    }
}
