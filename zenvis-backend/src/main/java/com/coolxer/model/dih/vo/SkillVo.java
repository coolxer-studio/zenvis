package com.coolxer.model.dih.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Skill 视图对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillVo implements Serializable {

    /**
     * Skill 唯一标识
     */
    private String id;

    /**
     * Skill 名称
     */
    private String name;

    /**
     * Skill 简介
     */
    private String description;

    /**
     * 版本号
     */
    private String version;

    /**
     * 作者
     */
    private String author;

    /**
     * 来源。插件安装的 Skill 使用插件包名，页面创建的 Skill 使用 custom，
     * 开放配置内置 Skill 使用 builtin。
     */
    private String source;

    /**
     * 是否允许在管理端编辑。插件 Skill 可编辑，内置 Skill 默认只读。
     */
    private Boolean editable;

    /**
     * 适用智能体类型。为空时表示全局可用。
     */
    @JsonAlias("agentTypes")
    private List<String> agentTypes = new ArrayList<>();

    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 是否启用
     */
    private Boolean enabled = false;

    /**
     * 是否为页面创建的自定义 Skill。null 或 false 表示内置/插件 Skill。
     */
    private Boolean custom;

    /**
     * 绑定的 RAG 知识库 ID 列表。绑定后平台会在每轮会话中自动注入该范围内的检索上下文。
     */
    @JsonAlias("knowledgeBaseIds")
    private List<Integer> knowledgeBaseIds;

    /**
     * 插件内知识库编码列表。加载时按插件包名和编码解析为 knowledgeBaseIds，
     * 使插件无需在打包时知道数据库自增 ID。
     */
    @JsonAlias("knowledgeBaseCodes")
    private List<String> knowledgeBaseCodes;

    /**
     * 绑定知识库的名称列表，顺序与 knowledgeBaseIds 对齐，仅用于页面展示。
     * 已删除的知识库回退显示为 #id。
     */
    private List<String> knowledgeBaseNames;

    /**
     * DIH 聊天入口配置。未配置或 chat.enabled=false 时不展示。
     */
    private SkillChatConfigVo chat;

    /**
     * 可选的 DIH 运行时提示词、工具白名单和预算配置。
     */
    private SkillRuntimeConfigVo runtime;

    /**
     * 入口文件，默认 SKILL.md
     */
    private String entry = "SKILL.md";

    /**
     * 相对 skill 根目录的路径
     */
    private String path;

    /**
     * 更新时间
     */
    private Date updateTime;
}
