# 插件能力实验室

`zenvis-plugin-lab` 是“插件能力实验室”的源码工程。它用一个可运行的双实体示例覆盖 ZenVis 插件的主要交付面：

- Meta 双实体与字典、数组、JSON、详情/IP 链接；
- `demo_logs` 生产演示消息到两个 Kafka Topic，再由消费任务校验、写入 ClickHouse 并统一处理 DLQ；
- 动态 REST API、MySQL 迁移和事务写入；
- 低代码应用、实体列表、独立详情页、IP 调查页；
- HTML 大屏与低代码看板；
- 只读 MCP、插件托管 RAG、运行时 Skill。

| 项目 | 值 |
| --- | --- |
| 显示名称 | 插件能力实验室 |
| 包名 | `com.coolxer.plugin.lab` |
| 版本 | `1.0.1` |
| 发布包 | `com-coolxer-plugin-lab-1.0.1.tar.gz` |
| 插件目录 | `plugin-lab/` |
| 动态 API 工程 | `extend-lab/` |

## 构建

要求 JDK 17、Maven 3.9+、Bash 与 TAR。脚本会运行 Maven 测试打包、校验薄 JAR、复制到 `plugin-lab/03_api/` 并生成归档。

```bash
cd zenvis-plugin/zenvis-plugin-lab
./build.sh
```

输出位于当前目录：

```text
com-coolxer-plugin-lab-1.0.1.tar.gz
```

归档根目录包含 `index.json`。构建过程排除 `target/`、`build.log`、历史 JAR、临时归档与 `.DS_Store`。

## 开发验证

```bash
mvn -f extend-lab/pom.xml test
find plugin-lab -name '*.json' -print0 | xargs -0 -n1 jq empty
```

有 Vector 时额外运行两个推送任务的校验和测试。HTML 大屏应先提取内联 JavaScript 做 `node --check`，再用浏览器覆盖完整数据、空数据和失败重试状态。

## 安装后观察点

1. 两个推送任务均运行且 Kafka 可用时，安装后约 15 秒内访问事件和风险线索两张表应出现数据；
2. “插件能力实验室”应用提供总览、访问事件、风险线索和笔记四页；
3. “实验室 IP 关联调查”跨两个实体匹配源/目的 IP；
4. “插件能力实验室大屏”和“插件能力实验室数据观察台”分别验证 HTML 与低代码看板；
5. MCP `lab` 应发现 `lab_capability_catalog` 与 `lab_note_search`；
6. 知识库 `lab-kb` 构建成功后，可在聊天入口选择“插件能力实验室导览”。
