# 动态 API 与数据迁移

插件可以在 `03_api` 提供一个动态 REST API 薄 JAR，并通过版本化 SQL 维护插件自己的 MySQL 数据结构。

## 源码与发布结构

```text
plugin-example/
├── api-src/                         # 源码仓库内容，不进入发布包
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/coolxer/plugin/example/
│       └── test/java/com/coolxer/plugin/example/
└── 03_api/
    ├── plugin-example-api.jar       # 最多一个
    └── migrations/mysql/
        ├── V001__create_example.sql
        └── V002__add_status_index.sql
```

`zenvis-plugin` 根插件通常把源码放在插件的 `api-src/`，并由根 Maven Reactor 管理。嵌套场景或产品子仓库也可以维护独立的 `extend-*` Maven 工程，但最终归档仍只包含 `03_api` 下的一个运行 JAR。

## 动态加载契约

后端为每个插件创建独立 ClassLoader，并扫描 `com.coolxer.plugin` 下的：

- `@RestController`
- `@Service`
- `@Repository`
- `@Component`

所有 Bean 使用插件命名空间注册，Controller 路由自动增加：

```text
/api/v1/plugin/{package_name}
```

卸载或升级时，平台注销路由和 Bean 并关闭 ClassLoader。插件不需要 Spring Boot 启动类，也不存在额外的插件注解。

Java 业务类必须位于 `com.coolxer.plugin` 扫描根下；它与 `index.json.package_name` 分别承担类扫描和插件资源身份，二者不要求字面相同。

## 依赖边界

插件可以依赖：

- Java 17；
- Spring Web、Context、JDBC、Transaction；
- Jackson、Jakarta Validation；
- `zenvis-plugin/api-common` 中的轻量响应和分页模型；
- 平台明确公开的稳定 Bean。

插件不得引用 ZenVis 后端内部业务 Entity、Repository、Service 实现、Controller DTO 或工具类。内部类不是稳定 SDK，平台升级可能破坏二进制兼容。

Spring 等平台依赖使用 Maven `provided` Scope，不能把整个 Spring Boot 应用打入 JAR。发布 JAR 不应包含 `BOOT-INF/`。

## Repository

插件访问 MySQL 时使用平台提供的命名 Bean：

```java
package com.coolxer.plugin.example.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExampleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExampleRepository(
            @Qualifier("pluginMysqlJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count() {
        Long value = jdbcTemplate.getJdbcTemplate()
                .queryForObject("SELECT COUNT(*) FROM t_example_record", Long.class);
        return value == null ? 0L : value;
    }
}
```

生产插件不要自行创建数据库连接池，也不要依赖核心 JPA Entity 扫描。

## Service 与事务

```java
package com.coolxer.plugin.example.service;

import com.coolxer.plugin.example.repository.ExampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExampleService {

    private final ExampleRepository repository;

    public ExampleService(ExampleRepository repository) {
        this.repository = repository;
    }

    @Transactional("pluginMysqlTransactionManager")
    public long count() {
        return repository.count();
    }
}
```

使用构造器注入。跨多个写操作的业务方法显式指定 `pluginMysqlTransactionManager`。

## Controller

```java
package com.coolxer.plugin.example.controller;

import com.coolxer.plugin.common.api.ResponseWrap;
import com.coolxer.plugin.example.service.ExampleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics")
public class ExampleController {

    private final ExampleService service;

    public ExampleController(ExampleService service) {
        this.service = service;
    }

    @GetMapping("/count")
    public ResponseWrap<Long> count() {
        return ResponseWrap.success(service.count());
    }
}
```

插件包名为 `com.example.plugin.analytics` 时，最终地址是：

```text
GET /api/v1/plugin/com.example.plugin.analytics/statistics/count
```

Controller 只声明插件内相对路径，不重复写平台前缀。请求和响应模型由插件自身维护；需要统一响应时使用 `api-common` 的 `ResponseWrap`、`PageRows` 和 `PageQuery`。

## API 设计

- 路由按插件业务域组织，不覆盖平台 Controller。
- 对列表、详情、创建、更新和动作使用清晰 HTTP 语义。
- 参数执行长度、枚举、格式和权限校验。
- 错误信息不返回 SQL、堆栈、凭据和下游敏感响应。
- Repository 使用参数化 SQL，不拼接用户输入。
- 对外部服务设置连接超时、请求超时和有界重试。
- 使用插件自己的包名、Bean、表和配置前缀，避免跨插件冲突。

插件 API 的平台认证、权限过滤和完整 REST 接入方式见 [RESTful API 概览](/08-API参考/RestfulAPI/概览与接入.md)。

动态 Controller 在主程序全局认证拦截器之后执行。认证成功时，请求属性
`zenvis.authenticated.userId` 包含当前 Zenvis 用户 ID，Session 与普通 REST Bearer Token
均使用这一属性；`zenvis.authenticated.userName` 可用于提示展示。插件应以用户 ID 做权限
判断并按需从受控用户目录读取快照，不要依赖 `Principal`，也不要自行解析 Redis Session、
Cookie 或 Token。

## MySQL 迁移

迁移文件位于：

```text
03_api/migrations/mysql/V<版本>__<描述>.sql
```

合法示例：

```text
V001__create_example_record.sql
V002__add_status_index.sql
V1.2__add_owner_column.sql
```

版本由一个或多个数字段组成，描述只能使用英文、数字、下划线或连字符。

```sql
CREATE TABLE IF NOT EXISTS t_example_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
```

平台在 `t_sys_plugin_migration` 记录：

| 字段 | 内容 |
| --- | --- |
| `package_name` | 插件包名 |
| `migration_version` | 文件名版本 |
| `description` | 文件名描述 |
| `checksum` | SQL 文件 SHA-256 |
| `installed_on` | 执行时间 |

## 迁移规则

- 迁移按数字版本顺序执行。
- 同一插件不能存在重复版本。
- 已执行的迁移文件必须在后续包中保留。
- 已执行文件的名称、描述和 SHA-256 不可修改。
- 新增版本必须高于已经执行的最高版本。
- 升级和重新安装只执行未记录的迁移。
- MySQL 迁移只向前，不由插件卸载自动回滚。

迁移应具备幂等保护和向前兼容性。升级失败时，旧插件可能恢复运行，但已经执行的 MySQL DDL 不会自动撤销。

ClickHouse 分析表由 `01_meta` 管理，不要用 MySQL 迁移替代 Meta 自动建表。

## 构建

构建父仓库直接维护的插件时执行：

```bash
cd zenvis-plugin
mvn test
bash build.sh plugin-example
```

Windows：

```powershell
cd zenvis-plugin
mvn test
.\build.ps1 plugin-example
```

构建脚本会执行 API 构建、替换 `03_api` 旧 JAR，并排除 `api-src` 后生成归档。这些操作会改变构建产物，只在准备交付时执行。

## 测试

- Repository：参数绑定、分页、排序白名单和空结果。
- Service：事务、枚举转换、并发和失败补偿。
- Controller：请求校验、响应包装、异常脱敏和路径。
- 迁移：空库执行、已有版本跳过、校验和变化拒绝。
- JAR：只有一个目标 JAR、包含 class、无 `BOOT-INF`。
- 安装：动态路由注册成功，卸载后路由和 Bean 消失。

## 常见问题

### API 未注册

检查业务类是否位于 `com.coolxer.plugin`、JAR 是否包含 class、`03_api` 是否有多个 JAR，以及后端是否报告 Bean 或路由冲突。

### Bean 注入失败

确认依赖来自插件自身或平台稳定 Bean；不要注入后端内部实现。多个插件的类名相同不会直接复用 Bean，仍应保持业务包名唯一。

### 数据库连接失败

确认使用 `pluginMysqlJdbcTemplate` 和 `pluginMysqlTransactionManager`，并由平台部署提供数据库配置。

### 迁移校验和不一致

恢复曾经发布并执行的原 SQL 文件，再新增更高版本迁移。不要删除迁移历史绕过校验。

## 关联文档

- [插件包规范](/03-插件开发与集成/插件包规范.md)
- [生命周期与发布验证](/03-插件开发与集成/生命周期与发布验证.md)
- [平台配置与扩展 REST API](/08-API参考/RestfulAPI/平台配置与扩展.md)
