# 插件能力实验室（插件包）

本目录是最终进入 `com-coolxer-plugin-lab-1.0.1.tar.gz` 的运行时内容。

## 包信息

| 项目 | 值 |
| --- | --- |
| 显示名称 | 插件能力实验室 |
| 包名 | `com.coolxer.plugin.lab` |
| 版本 | `1.0.1` |
| 图标 | `icon.png`，100×100 PNG |
| 完整文档 | `00_doc/README.md` |

## 已提供能力

- 实体 `lab_access_event` 与 `lab_risk_signal`，ClickHouse 表同名；
- `demo_logs` → 双 Kafka Topic → 双 Source 消费 → ClickHouse 的演示链路，以及统一 DLQ；
- 动态笔记 REST API、MySQL 迁移、分页与状态统计；
- 四页低代码应用、两个实体详情页、跨实体 IP 调查页；
- HTML 实时大屏与低代码数据观察台；
- 三种菜单入口：配置、应用、独立页面；
- `lab` MCP 两个只读工具；
- `lab-docs` 数据集、`lab-kb` 知识库与 `lab-capability-guide` Skill。

## 重要约定

- 安装身份与旧 `com.coolxer.plugin.user.event` 不兼容；
- 不使用旧实体/表/API/菜单；
- 不定义平台保留字段，分区使用 `toYYYYMM(zenvis_insert_time)`；
- 笔记写入只走 REST/低代码页面，MCP 与 Skill 保持只读；
- 所有演示数据为合成数据，IP 关联不代表真实攻击因果。

## 构建位置

从上一级 `zenvis-plugin-lab/` 执行 `./build.sh`。构建会把 `extend-lab` 生成的薄 JAR 复制到 `03_api/`，再打包本目录全部运行时内容。
