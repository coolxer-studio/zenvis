# 插件开发指南

ZenVis 采用插件化架构，支持动态加载和功能扩展。

## 插件架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Plugin System                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Plugin    │  │   Plugin    │  │   Plugin    │         │
│  │  Analytics  │  │   Audit     │  │   Custom    │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                  │
│         └────────────────┼────────────────┘                  │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              Plugin Manager                          │     │
│  │  - ExtendJarManager: 动态加载 JAR                    │     │
│  │  - ExtendJar: 插件实例管理                           │     │
│  │  - Cleaner: 资源清理                                 │     │
│  └─────────────────────────────────────────────────────┘     │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐     │
│  │         Spring Bean Registry                        │     │
│  │  - 自动注册 Spring stereotype 与动态 REST 接口       │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 插件目录结构

```
deploy/open_config/plugin-package_config/
└── com.coolxer.plugin.example/
    ├── index.json                # 插件描述，上传解析入口
    ├── README.md                 # 可选插件说明
    ├── icon.png                  # 可选插件图标
    ├── 00_doc/                   # 插件文档，可加载到 RAG
    ├── 01_meta/                  # 检索元数据和 ClickHouse 表结构
    ├── 02_push-task/             # 数据推送任务配置
    ├── 03_api/                   # 单一动态 API Jar 与数据库迁移
    ├── 04_ui/                    # 低代码配置；支持平铺兼容模式和多配置子目录
    ├── 05_dashboard/             # 数据看板配置
    ├── 06_mcp/                   # MCP 服务配置
    ├── 07_skill/                 # 插件 Skill
    └── 08_menu/                  # 菜单配置，最后安装入口
```

### 03_api 动态接口与 MySQL 迁移

- 每个插件最多包含一个薄 Jar，业务类必须位于 `com.coolxer.plugin` 包下。
- 运行时注册 `@Component`、`@Repository`、`@Service` 和 `@RestController`；
  接口统一增加
  `/api/v1/plugin/{package_name}` 前缀。
- JDBC Repository 通过 `pluginMysqlJdbcTemplate` 访问 MySQL，事务使用
  `pluginMysqlTransactionManager`，不要依赖核心工程的 JPA 实体扫描。
- MySQL 迁移放在 `03_api/migrations/mysql/`，文件名使用
  `Vnnn__description.sql`。平台按版本执行并校验 SHA-256，已执行版本禁止修改。
- 插件卸载只移除接口和 Spring Bean，不删除 MySQL 业务表或迁移历史。

## 插件配置

### index.json

```json
{
  "name": "示例分析",
  "package_name": "com.coolxer.plugin.example",
  "version": "1.0.0",
  "description": "示例数据分析插件",
  "author": "example",
  "icon": "icon.png"
}
```

### 插件索引配置

### 05_dashboard/config.json

```json
[
  {
    "name": "分析看板",
    "code": "com.coolxer.plugin.example.dashboard",
    "type": "LOW_CODE_PAGE",
    "config_index": "com.coolxer.plugin.example.dashboard"
  },
  {
    "name": "分析大屏",
    "code": "com.coolxer.plugin.example.html",
    "type": "HTML_PAGE",
    "html_path": "analytics-board.html"
  }
]
```

`LOW_CODE_PAGE` 对应配置放到 `05_dashboard/low-code/<config_index>_config/`。`HTML_PAGE` 对应静态文件放到 `05_dashboard/html-page/`，安装后系统会改写为 `/html-page/<packageName>/<relativePath>`。

### 06_mcp/config.json

```json
[
  {
    "code": "analytics-mcp",
    "name": "分析 MCP 服务",
    "description": "示例插件提供的 MCP 服务配置",
    "base_url": "https://example.com",
    "sse_endpoint": "/sse",
    "headers": "{\"Authorization\":\"Bearer token\"}",
    "enabled": true,
    "request_timeout_seconds": 30,
    "connect_timeout_seconds": 10
  }
]
```

### 08_menu/config.json

```json
[
  {
    "name": "示例分析",
    "type": "LOW_CODE_APP",
    "params": "com.coolxer.plugin.example"
  }
]
```

### 04_ui 多配置目录

`04_ui` 的每个一级子目录代表一套独立的低代码配置，配置索引自动生成为 `<packageName>.<子目录名>`：

```text
04_ui/
├── app/
│   ├── site.json
│   ├── overview.json
│   └── event-list.json
├── ip-statistics/
│   └── index.json
└── detail-event/
    └── index.json
```

以上目录分别安装为：

```text
<packageName>.app_config/
<packageName>.ip-statistics_config/
<packageName>.detail-event_config/
```

- 子目录包含 `site.json` 时按低代码应用使用，并允许同时存在应用子页面 `index.json`。
- 不包含 `site.json` 时必须包含 `index.json`，按独立低代码页面使用。
- 子目录名是安全逻辑名称，不包含 `_config`；菜单 `params` 和页面接口使用完整配置索引。
- 直接位于 `04_ui` 根目录的文件仍按旧规则安装到 `<packageName>_config`。
- 安装、回滚、重装、卸载和导出都会分别处理每套配置，导出时保留运行期间对配置所做的修改。

MCP 配置安装到 `t_ai_mcp_server`，看板和 MCP 均通过 `source = packageName` 记录插件归属，卸载插件时按该来源清理。

## 开发插件

### 1. 在插件仓库维护 API 源码

正式插件的 API 源码维护在 `zenvis-plugin`，不放入发布包：

```text
zenvis-plugin/
├── pom.xml
├── api-common/
└── plugin-custom/
    ├── api-src/
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/coolxer/plugin/custom/
    │       └── test/java/com/coolxer/plugin/custom/
    └── 03_api/
        └── migrations/mysql/
```

业务类必须位于 `com.coolxer.plugin` 包下。插件不需要入口类，也不存在
`@ExtendJar` 注解；运行时直接发现 Spring stereotype。

### 2. 实现 JDBC Repository

```java
package com.coolxer.plugin.custom.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomRepository(
            @Qualifier("pluginMysqlJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count() {
        Long value = jdbcTemplate.getJdbcTemplate()
                .queryForObject("SELECT COUNT(*) FROM t_custom_data", Long.class);
        return value == null ? 0L : value;
    }
}
```

禁止引用核心工程的业务 Entity、Repository、DTO 或内部工具类。插件与平台之间只使用文档明确的稳定 Bean、Spring/Jackson API 和插件自身模型。

### 3. 实现业务 Service

```java
package com.coolxer.plugin.custom.service;

import com.coolxer.plugin.custom.repository.CustomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomService {

    private final CustomRepository repository;

    public CustomService(CustomRepository repository) {
        this.repository = repository;
    }

    @Transactional("pluginMysqlTransactionManager")
    public long count() {
        return repository.count();
    }
}
```

### 4. 创建 REST 接口

```java
package com.coolxer.plugin.custom.controller;

import com.coolxer.plugin.common.api.ResponseWrap;
import com.coolxer.plugin.custom.service.CustomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics")
public class CustomController {

    private final CustomService service;

    public CustomController(CustomService service) {
        this.service = service;
    }

    @GetMapping("/count")
    public ResponseWrap<Long> count() {
        return ResponseWrap.success(service.count());
    }
}
```

假设插件包名为 `com.coolxer.plugin.custom`，运行时地址自动变为：

```text
GET /api/v1/plugin/com.coolxer.plugin.custom/statistics/count
```

Controller 中不要重复声明 `/api/v1/plugin/...`。

### 5. 添加版本化 MySQL 迁移

在插件目录创建 `03_api/migrations/mysql/V001__create_custom_data.sql`：

```sql
CREATE TABLE IF NOT EXISTS t_custom_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
```

迁移命名必须匹配 `V<数字版本>__<英文或数字描述>.sql`，支持点分版本，例如
`V1.2__add_index.sql`。已执行文件禁止修改；后续结构变化新增更高版本文件。

ClickHouse 分析实体仍通过 `01_meta/` 定义，不要用 MySQL 插件迁移替代 Meta 自动建表。

## API JAR 构建约束

- 一个插件最多一个 JAR。
- JAR 必须是普通薄 JAR，不能包含 `BOOT-INF/`。
- Spring Web、Spring Context、Spring JDBC、Jackson、Validation 等平台依赖使用
  Maven `provided` scope。
- 可以把 `api-common` 合并进插件 JAR，但不能把 Spring Boot 依赖打入 JAR。
- JAR 文件直接放在 `03_api/` 根目录，迁移脚本放在其子目录。

`zenvis-plugin` 根 Maven reactor 负责 API 编译。构建正式插件包时使用仓库脚本：

```bash
cd zenvis-plugin
bash build.sh plugin-custom
```

脚本执行 `clean package`，复制最终 JAR 到 `03_api/`，并在归档时排除 `api-src/`。Windows 使用：

```powershell
.\build.ps1 plugin-custom
```

## 插件生命周期

| 阶段 | 说明 |
| :--- | :--- |
| 上传 | 解析归档和 `index.json`，保存插件包 |
| 安装或升级 | 加载 Meta、推送任务和其他配置 |
| 数据迁移 | 执行尚未记录的 `03_api/migrations/mysql/*.sql` |
| API 加载 | 创建独立 ClassLoader，注册插件 Bean 与命名空间路由 |
| UI 和菜单 | 安装低代码配置、看板、MCP、Skill 和菜单 |
| 启动恢复 | 后端启动时重新加载所有已安装插件的唯一 API JAR |
| 卸载 | 注销插件路由、Bean 和 ClassLoader；保留 MySQL 表与迁移历史 |

安装失败时平台清理已经注册的插件 Bean 和配置。已经成功执行的数据库 DDL 和业务数据不作为补偿动作删除。

## 迁移执行规则

迁移历史记录在 `t_sys_plugin_migration`：

| 字段 | 说明 |
| :--- | :--- |
| `package_name` | 插件包名 |
| `migration_version` | 文件名中的版本 |
| `description` | 文件名中的描述 |
| `checksum` | SQL 文件 SHA-256 |
| `installed_on` | 执行时间 |

平台按数字语义排序版本，拒绝非法文件名、重复版本和已执行版本的校验和变化。插件没有 MySQL 迁移时，不要求创建 `migrations/mysql` 目录。

## 插件源码参考

正式插件源码位于 `zenvis-plugin`。API 模块统一放在各插件的 `api-src/`，
共用的轻量响应和分页模型放在 `api-common/`。

## 常见问题

### 1. 插件加载失败

检查：

- `index.json` 的 `package_name` 与插件是否一致；
- `03_api/` 是否包含超过一个 JAR；
- JAR 是否包含 `BOOT-INF/` 或缺少平台未提供的依赖；
- 业务类是否位于 `com.coolxer.plugin` 下；
- 后端日志是否报告 Bean 或路由注册失败。

### 2. Bean 注入失败

确保插件类使用正确的注解：

- `@Repository`：JDBC 数据访问；
- `@Service`：业务服务；
- `@Component`：通用组件；
- `@RestController`：动态接口。

同时确认构造器依赖来自插件自身或平台稳定 Bean。不要注入核心业务实现类。

### 3. 数据库连接失败

插件不要创建自己的生产数据源。MySQL Repository 注入 `pluginMysqlJdbcTemplate`，事务使用 `pluginMysqlTransactionManager`。

### 4. 迁移校验和不一致

说明某个已执行 SQL 被修改。恢复发布时执行过的原文件，并用新的更高版本迁移表达后续变化；不要删除 `t_sys_plugin_migration` 绕过检查。

### 5. 卸载后表仍然存在

这是设计行为。插件卸载用于移除运行能力和配置，不做破坏性数据删除。

## 下一步

- [API参考](api-reference.md)
- [MCP Client 与业务 Agent 设计](../DIH/MCP-Client-Agent-Design.md)
