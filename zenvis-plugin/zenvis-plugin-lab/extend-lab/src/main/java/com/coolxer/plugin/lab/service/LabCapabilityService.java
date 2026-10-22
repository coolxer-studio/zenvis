package com.coolxer.plugin.lab.service;

import com.coolxer.plugin.lab.model.LabCapability;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LabCapabilityService {

    private static final List<LabCapability> CAPABILITIES = List.of(
            new LabCapability("meta", "双实体数据模型", "数据层",
                    "展示字典、数组、JSON、数值和时间字段，以及详情与 IP 安全链接。", "访问事件 / 风险线索", true),
            new LabCapability("ingestion", "演示与生产双接入链路", "数据接入",
                    "demo_logs 提供开箱数据；Kafka 链路展示校验、ClickHouse 与独立 DLQ。", "推送任务配置", true),
            new LabCapability("api", "动态 REST API 与 MySQL", "扩展能力",
                    "通过薄 JAR 提供笔记 CRUD、分页、状态汇总和版本化数据库迁移。", "实验室笔记", false),
            new LabCapability("ui", "多配置低代码页面", "界面",
                    "包含应用导航、两个实体列表与详情、跨实体 IP 调查和 HTML 大屏。", "能力实验室应用", true),
            new LabCapability("dashboard", "HTML 与低代码看板", "界面",
                    "只使用真实 ZenVis 聚合 API，并提供加载、空态、失败、刷新与自动刷新。", "能力实验室大屏", true),
            new LabCapability("mcp", "只读 MCP 工具", "Agent 扩展",
                    "通过 SSE 暴露能力目录和笔记检索工具，写操作仍只走 REST。", "lab_capability_catalog", true),
            new LabCapability("rag", "插件托管知识库", "Agent 扩展",
                    "随插件安装文档数据集与知识库，升级时同步托管资源。", "lab-kb", true),
            new LabCapability("skill", "运行时 Skill", "Agent 扩展",
                    "把知识库、只读工具白名单、调用预算和降级说明组合成一个聊天入口。", "插件能力实验室导览", true));

    public List<LabCapability> list() {
        return CAPABILITIES;
    }
}
