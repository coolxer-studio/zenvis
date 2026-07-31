# UI、看板与菜单

插件可以提供低代码应用、独立低代码页面、Dashboard 和菜单入口。页面引用逻辑 Entity 与 Attribute，不直接绑定 ClickHouse 物理列。

## 04_ui 两种布局

### 新版多配置目录

```text
04_ui/
├── app/
│   ├── site.json
│   └── index.json
├── ip-statistics/
│   └── index.json
└── detail-event/
    └── index.json
```

每个一级子目录是一套独立配置。安装器生成：

| 子目录 | 配置索引 | 安装目录 |
| --- | --- | --- |
| `app` | `<package_name>.app` | `<package_name>.app_config` |
| `ip-statistics` | `<package_name>.ip-statistics` | `<package_name>.ip-statistics_config` |
| `detail-event` | `<package_name>.detail-event` | `<package_name>.detail-event_config` |

规则：

- 子目录名是安全逻辑名称，不包含 `_config`。
- 包含 `site.json` 时作为低代码应用，可以同时包含应用页面。
- 不包含 `site.json` 时必须包含 `index.json`，作为独立低代码页面。
- 菜单 `params`、`schemaApi`、页面跳转和 Meta 链接使用完整配置索引。

### 历史平铺布局

```text
04_ui/
├── site.json
├── index.json
├── event-list.json
└── event-view.json
```

平铺文件继续安装到 `<package_name>_config`。已有插件可以保持该布局；新建插件优先使用多配置目录，以避免应用、统计页和详情页共用一个配置索引。

## 低代码应用

`site.json` 组织应用导航和页面配置：

```json
{
  "status": 0,
  "msg": "",
  "data": {
    "pages": [
      {
        "label": "事件列表",
        "url": "/events",
        "schemaApi": "get:/zenvis/api/v1/config/com.example.plugin.analytics.app/get?file_name=index.json"
      }
    ]
  }
}
```

每个结构化 Entity 应有明确的列表入口。页面请求使用 Entity 逻辑名称：

```text
/zenvis/api/v1/entity/example_event/list
```

不要用表名替代 Entity，也不要把多个不同 Entity 偷换成一个合并接口。

## 独立页面与详情页

独立页面只包含 `index.json`。推荐每个 Entity 有自己的详情页：

```text
/#/service/low-code-page/com.example.plugin.analytics.detail-event?record_id=<zenvis_id>
```

详情数据：

```text
GET /zenvis/api/v1/entity/example_event/{record_id}/view
```

列表链接、Meta `link_template` 和详情页 Entity 必须一致。共享详情页只有在业务明确要求且能够严格隔离 Entity 时才使用。

跨 Entity IP 调查页应：

- 支持 `ip` 查询参数和手工输入；
- 对同一 Entity 的源 IP、目的 IP 使用 OR；
- 同一记录最多计数一次；
- 多 Entity 参数使用当前服务支持的逗号分隔标量；
- 为没有 IP 字段的 Entity 返回明确的零结果或不适用状态；
- 给实际 IP Attribute 配置到该页面的安全链接。

## 05_dashboard

```text
05_dashboard/
├── config.json
├── low-code/
└── html-page/
```

`config.json` 是与当前 `DashboardDto` 对齐的数组。支持：

| 类型 | 关键字段 | 说明 |
| --- | --- | --- |
| `LOW_CODE_PAGE` | `config_index` | 读取 `low-code/<config_index>_config/` |
| `HTML_PAGE` | `html_path` | 读取 `html-page/` 下的相对文件 |
| `LINK` | `url` | 受控外部链接 |
| `BUILT` | 平台内置配置 | 通常不由业务插件创建 |

### HTML 看板

```json
[
  {
    "name": "示例安全态势",
    "code": "com.example.plugin.analytics.dashboard.security-overview",
    "type": "HTML_PAGE",
    "html_path": "security-overview.html"
  }
]
```

文件位于：

```text
05_dashboard/html-page/security-overview.html
```

`html_path` 只填写相对于 `html-page` 的路径，不添加包名、部署前缀、`/html-page/` 或绝对 URL。安装器会复制文件并生成运行时路径。

HTML 看板应：

- 使用真实 ZenVis API 和当前登录 Session；
- 提供加载、空数据、失败、最后更新时间和手动刷新状态；
- 自动刷新时展示周期和暂停状态；
- 使用自包含 CSS/JavaScript，除非部署明确允许外部资源；
- 为全屏和常见 16:9 视口设计；
- 只有具备心跳、延迟或新鲜度依据时才声称链路“健康”。

### 低代码看板

```json
[
  {
    "name": "示例分析",
    "code": "com.example.plugin.analytics.dashboard.low-code",
    "type": "LOW_CODE_PAGE",
    "config_index": "com.example.plugin.analytics.dashboard"
  }
]
```

对应目录：

```text
05_dashboard/low-code/com.example.plugin.analytics.dashboard_config/
```

Dashboard `code` 全局唯一并保持稳定。升级时平台用它匹配原看板，以保留数据库身份和默认状态；当前默认看板不能被升级包直接删除。

## 08_menu

```json
[
  {
    "name": "示例事件管理",
    "type": "LOW_CODE_APP",
    "params": "com.example.plugin.analytics.app"
  },
  {
    "name": "事件详情查询",
    "type": "LOW_CODE_PAGE",
    "params": "com.example.plugin.analytics.detail-event"
  }
]
```

当前菜单类型：

| 类型 | 默认路由 | `params` |
| --- | --- | --- |
| `LOW_CODE_APP` | `low-code-app` | 应用配置索引 |
| `LOW_CODE_PAGE` | `low-code-page` | 独立页面配置索引 |
| `HTML_PAGE` | `html-page` | HTML 页面路径 |
| `EXTERNAL_APP` | `external-app` | 受控外部地址 |
| `POLICY_CONFIG` | `policy-config` | 策略或配置索引 |
| `BUILT_APP` | 配置的 `route` | 平台内置路由 |

插件菜单安装为一级菜单。安装器设置来源、层级、父级和默认顺序；复杂树形结构不应假设会按任意嵌套原样导入。

菜单配置只创建入口，不自动为所有角色授权。安装完成后仍需在角色权限中分配菜单。

## 配置身份和升级

升级依赖稳定身份：

- `04_ui` 子目录名决定配置索引；
- Dashboard 通过 `code` 匹配；
- Menu 优先通过 `type + params` 匹配；
- 没有 `params` 时使用 `type + route + name`；
- HTML 看板路径相对于插件自己的 HTML 根目录。

不要仅为重命名显示文案而修改配置索引、Dashboard Code 或 Menu Params。

升级时：

- UI 配置以新包内容更新；
- Dashboard 通过稳定 Code 保留身份；
- 菜单通过稳定匹配键保留 ID 和角色授权；
- 用户在运行期修改的外部 MCP 连接参数按单独规则处理，不由本页负责；
- 导出插件会带出平台当前保存的低代码配置和看板资源。

## URL 与 iframe 安全

- 页面内部优先使用平台相对 URL。
- 外部链接只允许受控 `http/https`。
- 不构造 `javascript:`、`data:`、`file:` 或协议相对 URL。
- HTML 页面运行在 iframe 容器中，不依赖访问父窗口敏感状态。
- 静态页面对 API 错误和未登录状态提供明确提示。
- 不在 HTML、JSON 或浏览器日志中嵌入凭据。

## 检查清单

- 每个多配置子目录都有 `site.json` 或 `index.json`。
- 子目录名不带 `_config`，所有引用使用完整配置索引。
- Entity 列表、详情页、Meta 链接和 API 路径一致。
- Dashboard Code 唯一，HTML 路径为插件内相对路径。
- Menu Type、Params 和实际页面类型一致。
- 安装后已为目标角色分配菜单。
- 页面覆盖加载、空数据、错误和刷新状态。
- 低代码 Schema 能由当前前端容器正常加载。

## 关联文档

- [Meta 与数据建模](/03-插件开发与集成/Meta与数据建模.md)
- [生命周期与发布验证](/03-插件开发与集成/生命周期与发布验证.md)
- [看板与菜单 MCP Tool](/08-API参考/MCPtool/看板与菜单工具.md)
