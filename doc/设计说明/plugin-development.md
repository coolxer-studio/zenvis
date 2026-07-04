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
│  │   Asset     │  │   Audit     │  │   Custom    │         │
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
│  │  - 自动扫描并注册插件中的 @Service, @Component       │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 插件目录结构

```
deploy/open_config/plugin-package_config/
└── com.coolxer.plugin.asset/
    ├── index.json                # 插件描述，上传解析入口
    ├── README.md                 # 可选插件说明
    ├── icon.png                  # 可选插件图标
    ├── 00_doc/                   # 插件文档，可加载到 RAG
    ├── 01_meta/                  # 检索元数据和 ClickHouse 表结构
    ├── 02_push-task/             # 数推任务配置
    ├── 03_api/                   # 动态加载的 API Jar
    ├── 04_ui/                    # 低代码页面配置，安装到 <packageName>_config
    ├── 05_dashboard/             # 数据看板配置
    ├── 06_mcp/                   # MCP 服务配置
    ├── 07_skill/                 # 插件 Skill
    └── 08_menu/                  # 菜单配置，最后安装入口
```

## 插件配置

### index.json

```json
{
  "name": "资产管理",
  "package_name": "com.coolxer.plugin.asset",
  "version": "1.0.0",
  "description": "资产管理插件",
  "author": "coolxer",
  "icon": "icon.png"
}
```

### 插件索引配置

### 05_dashboard/config.json

```json
[
  {
    "name": "资产看板",
    "code": "com.coolxer.plugin.asset.dashboard",
    "type": "LOW_CODE_PAGE",
    "config_index": "com.coolxer.plugin.asset.dashboard"
  },
  {
    "name": "资产大屏",
    "code": "com.coolxer.plugin.asset.html",
    "type": "HTML_PAGE",
    "html_path": "asset-board.html"
  }
]
```

`LOW_CODE_PAGE` 对应配置放到 `05_dashboard/low-code/<config_index>_config/`。`HTML_PAGE` 对应静态文件放到 `05_dashboard/html-page/`，安装后系统会改写为 `/html-page/<packageName>/<relativePath>`。

### 06_mcp/config.json

```json
[
  {
    "code": "asset-mcp",
    "name": "资产 MCP 服务",
    "description": "资产插件提供的 MCP 服务配置",
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
    "name": "资产管理",
    "type": "LOW_CODE_APP",
    "params": "com.coolxer.plugin.asset"
  }
]
```

MCP 配置安装到 `t_ai_mcp_server`，看板和 MCP 均通过 `source = packageName` 记录插件归属，卸载插件时按该来源清理。

## 开发插件

### 1. 创建插件模块

```
src/
└── main/
    └── java/
        └── com/
            └── coolxer/
                └── plugin/
                    └── custom/
                        ├── CustomPlugin.java
                        ├── service/
                        │   └── CustomService.java
                        └── controller/
                            └── CustomController.java
```

### 2. 实现插件主类

```java
package com.coolxer.plugin.custom;

import org.springframework.stereotype.Component;
import com.coolxer.configuration.extend.ExtendJar;

/**
 * 插件入口类
 */
@ExtendJar(
    name = "com.coolxer.plugin.custom",
    version = "1.0.0",
    description = "自定义插件"
)
@Component
public class CustomPlugin {

    /**
     * 插件初始化
     */
    public void init() {
        // 初始化逻辑
    }

    /**
     * 插件销毁
     */
    public void destroy() {
        // 清理逻辑
    }
}
```

### 3. 实现业务逻辑

```java
@Service
public class CustomService {

    public Result<?> queryData(QueryDTO query) {
        // 业务逻辑
        return Result.success(data);
    }
}
```

### 4. 创建 REST 接口

```java
@RestController
@RequestMapping("/api/v1/custom")
public class CustomController {

    @Autowired
    private CustomService customService;

    @PostMapping("/query")
    public Result<?> query(@RequestBody QueryDTO query) {
        return customService.queryData(query);
    }
}
```

### 5. 数据库Schema配置

在 `deploy/open_config/web_config/schema/` 下创建 Schema 文件：

```json
{
  "name": "custom_data",
  "description": "自定义数据",
  "fields": [
    {
      "name": "id",
      "type": "String",
      "description": "主键ID"
    },
    {
      "name": "name",
      "type": "String",
      "description": "名称"
    }
  ]
}
```

## 插件打包

### Maven 配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.3.0</version>
    <configuration>
        <archive>
            <manifest>
                <mainClass>com.coolxer.plugin.custom.CustomPlugin</mainClass>
            </manifest>
        </archive>
    </configuration>
</plugin>
```

### 打包命令

```bash
mvn clean package -DskipTests
```

### 部署插件

将打包好的 JAR 放入插件目录：

```bash
cp target/custom-plugin-1.0.0.jar \
   deploy/open_config/plugin-package_config/com.coolxer.plugin.custom/lib/
```

## 插件生命周期

| 阶段 | 说明 |
| :--- | :--- |
| 扫描 | ExtendJarManager 扫描插件目录 |
| 加载 | 动态加载 JAR 文件 |
| 注册 | 自动注册 @Service, @Component 等 |
| 初始化 | 调用插件 init 方法 |
| 运行 | 插件提供服务 |
| 销毁 | 应用关闭时调用 destroy 方法 |

## 热加载

插件支持热加载：

```bash
# 触发插件重载
POST /api/v1/plugin/reload
```

## 已有插件参考

资产管理插件：`deploy/open_config/plugin-package_config/com.coolxer.plugin.asset/`

- `00_doc/README.md` - 插件文档
- `00_doc/数据库建表.md` - 数据库结构说明

## 常见问题

### 1. 插件加载失败

检查：
- JAR 文件是否完整
- `plugin.json` 配置是否正确
- 依赖是否满足

### 2. Bean 注入失败

确保插件类使用正确的注解：
- `@Service` - 服务类
- `@Component` - 组件类
- `@RestController` - 控制器

### 3. 数据库连接失败

检查插件中的数据源配置是否正确。

## 下一步

- [API参考](api-reference.md)
- [AI智能分析](ai-analysis.md)
