# ZenVis 项目级智能体规范

## 适用范围与权威来源

本文件适用于 ZenVis 根目录及其所有子目录。修改任何文件前，应先读取根规范和目标目录最近的 `AGENTS.md`。

规则优先级：

1. 当前用户的明确指令和系统/开发者约束。
2. 距离目标文件最近的 `AGENTS.md`；子目录规则覆盖根规范中的冲突内容。
3. 本文件的项目级通用规则。
4. `doc/`、模块 README 和代码注释中的说明。

当前代码、`pom.xml`、`package.json`、`Makefile`、Compose、插件 `index.json` 和实际配置是最终事实来源。文档与代码冲突时，先在交付说明中指出差异，不要静默按过期文档修改。

## 项目定位

ZenVis 是面向企业数据分析场景的配置化应用框架，统一提供数据接入、元数据管理、检索分析、可视化、插件扩展、业务服务接入和 AI 智能分析能力。具体业务模型、页面和接口优先通过插件交付；只有可被多个业务复用的能力才进入平台核心。

| 模块 | 职责 | 技术与权威入口 |
| --- | --- | --- |
| `agent-skills` | 面向研发人员和 Codex 的工作流技能，包括插件创建与 Issue 工作流 | Markdown、YAML、Python；各 `SKILL.md` 和 [`agent-skills` 指南](doc/07-开发指南/agent-skills-开发对接指南.md) |
| `deploy` | Docker Compose、Helm、开放配置、初始化脚本和运行数据目录 | Compose、Helm、Bash、Nginx；[`deploy` 指南](doc/07-开发指南/deploy-开发对接指南.md) |
| `doc` | 产品、部署、插件、AI、架构、开发、API 与 Issue 报告中心 | Markdown、Docsify 4；[文档中心](doc/README.md) |
| `zenvis-backend` | 平台 API、权限、Meta/Retrieval、插件生命周期、DIH/MCP/RAG、业务服务管理 | Java 17、Spring Boot 3.2.0、Spring AI 1.1.0-M4、Maven；[模块规范](zenvis-backend/AGENTS.md) |
| `zenvis-frontend` | 管理控制台、检索、看板、低代码、DIH 工作台 | Vue 3.5、TypeScript 6、Vite 8、Element Plus、Pinia、Yarn；[模块规范](zenvis-frontend/AGENTS.md) |
| `zenvis-plugin` | 平台、社区和企业插件，Meta、Vector、动态 API、UI、看板、MCP、Skill 与菜单 | JSON/YAML、Java、AMIS、HTML；[插件指南](doc/07-开发指南/zenvis-plugin-开发对接指南.md) |
| `zenvis-dependencies` | 业务服务接入 Starter、SDK 和公共依赖 | Java 17、Spring Boot 3.x、Go；[依赖说明](zenvis-dependencies/README.md) |
| `zenvis-service` | 独立业务服务集合，当前包含拓扑/威胁分析（analyzer）、资产、导出、运维、风险、Vectum 和 TraceHub 等服务 | Go、Java、Vue、Kafka、Redis；各服务 README 和最近的 `AGENTS.md` |

## 架构与运行边界

- 浏览器通过前端 `/zenvis` 前缀访问后端；开发代理将 `/zenvis/api/v1/**` 转发到后端 `/api/v1/**`，并在代理时移除 `/zenvis`。
- `zenvis-backend` 负责统一认证、权限、配置、Meta、Retrieval、看板、插件生命周期、DIH/MCP/RAG 和公开上报接口。Controller 只处理 HTTP 契约与参数校验，Service 承载业务规则和事务，Repository/查询引擎负责持久化。
- `zenvis-backend` 使用 MySQL 保存平台关系数据，ClickHouse 保存插件分析数据，Redis 保存 Session/缓存/运行状态，Redis Stack 提供向量检索，MinIO 保存对象，Kafka 与 Vectum 承担数据接入和推送任务。
- 插件是可独立升级的交付单元。Meta、Vector 转换、动态 API、MySQL 迁移、UI、看板、MCP、Skill、菜单和文档必须整体保持一致；不能只改其中一个环节。
- 业务服务通过 Spring Boot Starter、Go SDK 或公开 REST 接口向平台上报心跳和事件。服务异常不得向宿主业务线程传播；生产环境的上报与限流由部署层保障。
- DIH 普通问答不加载业务 MCP 工具；业务 Agent/Skill 只使用显式绑定的工具和知识库。不得增加任意 SQL 生成、执行或校验链路。

## 模块阅读与命令路由

开始修改前，先读取目标模块最近的 `AGENTS.md`；没有模块级文件时，读取模块 README 和开发指南。

- `zenvis-backend`：先读 [`zenvis-backend/AGENTS.md`](zenvis-backend/AGENTS.md) 和[后端开发指南](doc/07-开发指南/zenvis-backend-开发对接指南.md)。常用命令在 `zenvis-backend/` 运行 `mvn test`、`mvn clean compile`、`mvn spring-boot:run -Dspring-boot.run.profiles=dev`。
- `zenvis-frontend`：先读 [`zenvis-frontend/AGENTS.md`](zenvis-frontend/AGENTS.md) 和[前端开发指南](doc/07-开发指南/zenvis-frontend-开发对接指南.md)。常用命令在 `zenvis-frontend/` 运行 `yarn test`、`yarn lint`、`yarn build:pro`。
- `zenvis-plugin`：先读[插件开发与集成](doc/03-插件开发与集成/README.md)、[插件开发指南](doc/07-开发指南/zenvis-plugin-开发对接指南.md)以及目标插件 README。父工作树可使用 `cd zenvis-plugin && mvn test`；独立子工作树和打包命令以其 README 为准。
- `zenvis-service`：先读目标服务 README；`zenvis-service-analyzer`、`zenvis-service-vectum` 还必须读取各自的 `AGENTS.md`。Go 服务通常使用 `make test`/`go test ./...`，Java 服务使用 Maven；不要跨服务机械套用命令。
- `zenvis-dependencies`：先读[Starter 开发指南](doc/07-开发指南/zenvis-business-service-spring-boot-starter-开发对接指南.md)；Starter 测试可使用 `mvn -f zenvis-dependencies/zenvis-business-service-spring-boot-starter/pom.xml test`。
- `deploy`：先读 [`deploy` 开发指南](doc/07-开发指南/deploy-开发对接指南.md)。初始化、检查和启动完整环境使用 `./zenvisctl compose init`、`./zenvisctl compose doctor`、`./zenvisctl compose up`；只修改部署编排时，至少运行 `docker compose -f deploy/docker-compose.yml config`。
- `agent-skills`：先读目标 `SKILL.md` 和[`agent-skills` 开发指南](doc/07-开发指南/agent-skills-开发对接指南.md)；Issue 工作流测试使用 `python3 -m unittest discover -s agent-skills/zenvis-issue-workflow/tests -v`。
- `doc`：先读[开发指南索引](doc/07-开发指南/README.md)；修改文档后检查同目录链接、侧边栏 `doc/_sidebar.md` 和代码块闭合。

## 跨模块契约

- 后端 Controller 路径使用 `/api/v1/**`；动态插件 API 使用 `/api/v1/plugin/{package_name}/**`。
- JSON 请求/响应字段使用 `snake_case`；常规响应使用 `ResponseWrap<T>`，包含 `status`、`msg` 和 `data`，成功状态为 `0`。
- 请求和响应模型使用明确的 DTO/VO；不要在 Controller 中返回临时 Map 代替稳定契约。
- 修改字段、接口、配置、部署方式或用户可见行为时，同步检查前端类型、插件契约、API 文档、配置模板和测试。
- 修改插件能力时，同时核对 `index.json`、`00_doc`、`01_meta`、`02_push-task`、`03_api`、`04_ui`、`05_dashboard`、`06_menu`、`07_mcp`、`08_rag` 和 `09_skill` 中的实际受影响项。
- 版本以当前清单为准，不凭记忆填写依赖版本、端口或命令。

## 公共工程规则

- 先理解现有实现和既有风格，再做最小、聚焦的修改；不进行与任务无关的重构、格式化或依赖升级。
- 修改前检查目标工作树状态，保留用户和其他任务的未提交内容。不要清理、覆盖或提交不属于当前任务的文件。
- 优先使用 `rg`/`rg --files` 查找代码；读取实际的 Controller、Service、DTO、配置和测试，而不是只依据 README 推断。
- 修复缺陷时先定位根因；能补充最小回归测试时补充测试，并在交付中记录已执行和未执行的验证。
- 不提交 `target/`、`dist/`、`node_modules/`、`logs/`、运行数据、构建缓存、`.DS_Store`、本机配置或凭据。
- 不新增无意义注释、版权头或临时兼容层；不为通过检查而绕过测试、权限或契约校验。
- 完成前运行目标模块的测试/构建、`git diff --check`，并检查文档与代码契约是否同步。

## 安全与数据边界

- 不得在代码、文档、报告、示例、日志或提交信息中写入真实 token、密码、私钥、客户敏感数据、完整对话或未脱敏原始日志。
- 凭据使用环境变量或忽略提交的本机配置；示例只写占位符。发现已有凭据泄露时，建议撤销并重新生成，不要在交付说明中复述密钥。
- 公开 Issue 报告和审计文档只保存脱敏摘要，不上传 diff、完整日志或带凭据 URL。
- Retrieval、MCP 和 Agent 不得生成或执行任意 SQL；只允许平台定义的安全查询、工具白名单和审批流程。
- 外部 URL、iframe、静态资源和重定向只允许安全协议；HTML/Markdown 必须按项目现有清理规则处理。
- 涉及认证、权限、审批、对象存储、插件安装或跨租户数据时，必须检查越权、路径穿越、凭据泄露和失败降级路径。

## Git 与 Issue 交付

- 先确认实际 Git 根目录：在目标目录运行 `git rev-parse --show-toplevel` 和 `git status --short`。本仓库可能包含嵌套工作树或独立子仓库，禁止把不同工作树的改动混入同一提交。
- Codex 默认只输出完整、可复制的提交命令，不执行 `git add`、`git commit`、`git push`，也不创建或切换分支。用户明确说“由你提交”时才可运行 `commit --execute`，且只创建本地提交；用户另行明确说“推送”时才可推送。
- 提交是否关联 issue 由用户决定。未指定 issue 时生成普通提交；指定时一个提交只能关联一个 issue，并添加 `Zenvis-Issue` 与对应关闭 trailer。
- Gitee `origin` 是主提交仓库，GitHub 通过现有镜像同步代码；不要提供推送第二个 remote 的命令。镜像 Action 只在提交到达 `main` 并确认两个平台都存在该提交后评论并关闭 issue。
- 只有用户明确要求“整理 issue”时才生成报告，报告阶段只读、不改代码。只有明确指定 GitHub/Gitee 时才创建 issue；只有明确说“按报告修复”“修复 github#N”或“修复 gitee#<id>”时才进入修复。
- 每个保留 issue 完成代码和测试后停止，待用户确认提交完成再处理下一个，保持“一 issue 一 commit”。失败时停止整批处理，不跳过错项继续。
- 本工作流不安装或依赖 Git hooks。若 `doctor` 提示残留的 `core.hooksPath=.githooks`，建议用户执行 `git config --unset core.hooksPath`，不要自行修改用户 Git 配置。
- 完整 Issue 规范见 [`zenvis-issue-workflow/SKILL.md`](agent-skills/zenvis-issue-workflow/SKILL.md) 和 [Issue 工作流说明](doc/07-开发指南/issue-workflow.md)。

## 完成前检查

1. 在目标工作树确认修改范围，没有混入无关文件或凭据。
2. 运行受影响的单元测试、集成测试或构建；不能运行时明确说明原因和剩余风险。
3. 检查跨模块契约、配置、插件文档和用户文档是否同步。
4. 运行 `git diff --check`，检查 Markdown 链接、YAML/JSON 解析和代码块闭合。
5. 将提交命令交给用户，或仅在明确授权范围内提交；不要自行推送。
