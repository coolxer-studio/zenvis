package com.coolxer.service.dih;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DataVisualizationDemoResponseService {

    public static final String USER_EVENT_VISUALIZATION_DEMO_TITLE = "用户事件数据可视化演示";
    public static final String CHART_EXAMPLE_PROMPT =
            "请查看用户事件数据的上报情况，并生成一个临时性的可视化图表。";
    public static final String PAGE_EXAMPLE_PROMPT =
            "请根据用户事件数据生成一个单页面应用。";
    public static final String SIDEBAR_APP_EXAMPLE_PROMPT =
            "请生成一个带侧边栏的用户事件数据应用。";
    public static final String DASHBOARD_EXAMPLE_PROMPT =
            "请生成一个用户事件数据看板。";
    public static final String MENU_EXAMPLE_PROMPT =
            "请添加一个用户事件外部看板菜单。";

    private static final String ENTITY = "user-event";
    private static final String ENTITY_LABEL = "用户事件";
    private static final String PAGE_CONFIG_TYPE = "user-event-page";
    private static final String APP_CONFIG_TYPE = "user-event-app";
    private static final String DASHBOARD_CONFIG_TYPE = "user-event-dashboard";
    private static final String HTML_PAGE_FILE = "user-event-page.html";
    private static final String HTML_DASHBOARD_FILE = "user-event-dashboard.html";
    private static final String HTML_PAGE_PATH = "/html-page/" + HTML_PAGE_FILE;
    private static final String HTML_DASHBOARD_PATH = HTML_DASHBOARD_FILE;
    private static final String ACTION_ADD_CHART_LIBRARY = "data_visualization.add_chart_library";
    private static final String ACTION_APPLY_CONFIG = "data_visualization.apply_config";
    private static final String SOURCE_PREFIX = "data-visualization-demo:user-event:";
    private static final String MENU_DEMO_NAME = "用户事件外部看板";
    private static final String MENU_DEMO_URL = "https://example.com/user-event-dashboard";
    private static final String MENU_DEMO_SOURCE = SOURCE_PREFIX + "menu-external-dashboard";
    private static final String DECISION_ACTIONS = "[\"apply_config\",\"abandon\",\"revise\"]";
    private static final int DEMO_STREAM_CHUNK_SIZE = 20;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(45);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'，,。)）]+", Pattern.CASE_INSENSITIVE);

    private static final String CHART_AMIS_CONFIG = """
            {
              "type": "page",
              "title": "用户事件上报趋势",
              "body": [
                {
                  "type": "chart",
                  "api": {
                    "method": "post",
                    "url": "/zenvis/api/v1/entity/trend/query",
                    "data": {
                      "entities": ["user-event"],
                      "time_range": {"preset": "LAST_7_DAYS"},
                      "granularity": "DAY"
                    },
                    "adaptor": "var d=payload&&payload.data?payload.data:{};var o=d.echarts&&d.echarts.option?d.echarts.option:{};return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},chart_series:o.series||[]}};"
                  },
                  "config": {
                    "title": {
                      "text": "用户事件上报趋势"
                    },
                    "tooltip": {
                      "trigger": "axis"
                    },
                    "legend": {},
                    "dataset": "${chart_dataset}",
                    "xAxis": {
                      "type": "category"
                    },
                    "yAxis": {
                      "type": "value"
                    },
                    "series": "${chart_series}"
                  }
                }
              ]
            }
            """;

    private static final String CHART_ECHARTS_OPTION = """
            {
              "title": {
                "text": "用户事件上报趋势",
                "left": "center",
                "textStyle": {
                  "fontSize": 14
                }
              },
              "tooltip": {
                "trigger": "axis"
              },
              "legend": {
                "top": 28,
                "data": ["登录", "点击", "浏览", "删除", "修改"]
              },
              "grid": {
                "left": 36,
                "right": 24,
                "top": 72,
                "bottom": 32
              },
              "xAxis": {
                "type": "category",
                "boundaryGap": false,
                "data": ["00:00", "04:00", "08:00", "12:00", "16:00", "20:00"]
              },
              "yAxis": {
                "type": "value"
              },
              "series": [
                {"name": "登录", "type": "line", "smooth": true, "data": [12, 18, 46, 52, 39, 31]},
                {"name": "点击", "type": "line", "smooth": true, "data": [24, 35, 72, 91, 83, 60]},
                {"name": "浏览", "type": "line", "smooth": true, "data": [38, 44, 88, 126, 110, 78]},
                {"name": "删除", "type": "line", "smooth": true, "data": [2, 4, 6, 8, 5, 3]},
                {"name": "修改", "type": "line", "smooth": true, "data": [5, 8, 13, 17, 11, 9]}
              ]
            }
            """;

    private static final String USER_EVENT_PAGE_CONFIG = """
            {
              "type": "page",
              "title": "用户事件管理",
              "toolbar": [
                {
                  "type": "button",
                  "label": "创建记录",
                  "primary": true,
                  "actionType": "dialog",
                  "dialog": {
                    "title": "创建用户事件",
                    "body": {
                      "type": "form",
                      "api": "/zenvis/api/v1/entity/user-event/add",
                      "body": [
                        {"name": "event_id", "type": "uuid"},
                        {"type": "input-text", "name": "procid", "label": "进程id", "required": true},
                        {"type": "input-text", "name": "user", "label": "用户", "required": true},
                        {"type": "select", "name": "event_type", "label": "事件类型", "source": "/zenvis/api/v1/entity/user-event/event_type/mapping", "required": true},
                        {"type": "input-number", "name": "reliability", "label": "可信度", "min": 0, "max": 10, "required": true},
                        {"type": "textarea", "name": "detail", "label": "数据详情", "required": true},
                        {"type": "input-tag", "name": "tags", "label": "标记", "source": "/zenvis/api/v1/entity/user-event/tags/list"},
                        {"type": "input-datetime", "name": "server_time", "label": "入库时间", "format": "YYYY-MM-DD HH:mm:ss", "value": "now", "required": true}
                      ]
                    }
                  }
                }
              ],
              "body": [
                {
                  "type": "crud",
                  "api": "/zenvis/api/v1/entity/user-event/list",
                  "quickSaveItemApi": "/zenvis/api/v1/entity/user-event/$zenvis_id/update",
                  "autoGenerateFilter": true,
                  "columns": [
                    {"type": "tpl", "name": "event_id", "label": "事件ID", "tpl": "${event_id|truncate:14}", "copyable": true},
                    {"name": "procid", "label": "进程id", "searchable": true},
                    {"name": "user", "label": "用户", "searchable": true},
                    {
                      "name": "event_type",
                      "label": "事件类型",
                      "type": "mapping",
                      "map": {
                        "login": "<span class='label label-info'>登录</span>",
                        "click": "<span class='label label-info'>点击</span>",
                        "view": "<span class='label label-info'>浏览</span>",
                        "delete": "<span class='label label-warning'>删除</span>",
                        "modify": "<span class='label label-warning'>修改</span>",
                        "*": "其他"
                      },
                      "searchable": {
                        "type": "select",
                        "source": "/zenvis/api/v1/entity/user-event/event_type/mapping",
                        "clearable": true
                      }
                    },
                    {"name": "reliability", "label": "可信度", "searchable": true},
                    {"name": "server_time", "label": "入库时间", "searchable": {"type": "input-datetime-range", "name": "server_time"}},
                    {"type": "tpl", "name": "tags", "label": "标记", "tpl": "${tags}"},
                    {"type": "tpl", "name": "detail", "label": "详情", "tpl": "${detail | json | truncate:24}", "popOver": {"body": {"type": "json", "value": "${detail | json}"}}},
                    {
                      "type": "operation",
                      "label": "操作",
                      "buttons": [
                        {
                          "type": "button",
                          "icon": "fa fa-pencil",
                          "actionType": "dialog",
                          "dialog": {
                            "title": "编辑用户事件",
                            "body": {
                              "type": "form",
                              "api": "/zenvis/api/v1/entity/user-event/$zenvis_id/update",
                              "body": [
                                {"type": "static", "name": "event_id", "label": "事件ID"},
                                {"type": "input-text", "name": "procid", "label": "进程id", "required": true},
                                {"type": "input-text", "name": "user", "label": "用户", "required": true},
                                {"type": "select", "name": "event_type", "label": "事件类型", "source": "/zenvis/api/v1/entity/user-event/event_type/mapping"},
                                {"type": "input-number", "name": "reliability", "label": "可信度", "min": 0, "max": 10},
                                {"type": "input-tag", "name": "tags", "label": "标记", "source": "/zenvis/api/v1/entity/user-event/tags/list"}
                              ]
                            }
                          }
                        },
                        {"type": "button", "icon": "fa fa-times text-danger", "actionType": "ajax", "confirmText": "确认删除该事件？", "api": "delete:/zenvis/api/v1/entity/user-event/$zenvis_id"}
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String USER_EVENT_APP_SITE_CONFIG = """
            {
              "status": 0,
              "msg": "",
              "data": {
                "pages": [
                  {"label": "Home", "url": "/", "redirect": "/index"},
                  {
                    "children": [
                      {"label": "首页", "url": "index", "icon": "fa-solid fa-house", "schemaApi": "get:/zenvis/api/v1/config/user-event-app/get?file_name=index.json"},
                      {"label": "管理页面", "url": "manage", "icon": "fa-solid fa-table", "schemaApi": "get:/zenvis/api/v1/config/user-event-app/get?file_name=manage.json"},
                      {"label": "上报趋势", "url": "trend", "icon": "fa-solid fa-chart-line", "schemaApi": "get:/zenvis/api/v1/config/user-event-app/get?file_name=trend.json"}
                    ]
                  }
                ]
              }
            }
            """;

    private static final String USER_EVENT_APP_HOME_CONFIG = """
            {
              "type": "page",
              "title": "用户事件应用首页",
              "body": [
                {
                  "type": "service",
                  "api": "/zenvis/api/v1/entity/user-event/list?page=1&per_page=1",
                  "body": {
                    "type": "panel",
                    "title": "用户事件数据应用",
                    "body": [
                      {"type": "tpl", "tpl": "本应用基于 user-event 元数据实体，提供上报趋势查看和事件管理能力。"},
                      {"type": "divider"},
                      {"type": "tpl", "tpl": "当前可通过左侧菜单进入管理页面或上报趋势页面。"}
                    ]
                  }
                }
              ]
            }
            """;

    private static final String USER_EVENT_APP_TREND_CONFIG = """
            {
              "type": "page",
              "title": "用户事件上报趋势",
              "body": [
                {
                  "type": "chart",
                  "api": {
                    "method": "post",
                    "url": "/zenvis/api/v1/entity/trend/query",
                    "data": {"entities": ["user-event"], "time_range": {"preset": "LAST_7_DAYS"}, "granularity": "DAY"},
                    "adaptor": "var d=payload&&payload.data?payload.data:{};var o=d.echarts&&d.echarts.option?d.echarts.option:{};return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},chart_series:o.series||[]}};"
                  },
                  "config": {
                    "title": {"text": "用户事件上报趋势"},
                    "tooltip": {"trigger": "axis"},
                    "legend": {},
                    "dataset": "${chart_dataset}",
                    "xAxis": {"type": "category"},
                    "yAxis": {"type": "value"},
                    "series": "${chart_series}"
                  }
                }
              ]
            }
            """;

    private static final String USER_EVENT_DASHBOARD_CONFIG = """
            {
              "type": "page",
              "title": "用户事件数据看板",
              "body": [
                {
                  "type": "grid",
                  "columns": [
                    {
                      "body": {
                        "type": "service",
                        "api": "/zenvis/api/v1/entity/user-event/list?page=1&per_page=1",
                        "body": {"type": "tpl", "tpl": "<div style='font-size:16px'>用户事件总览</div><div style='font-size:28px;font-weight:700'>${total || 0}</div>"}
                      }
                    },
                    {
                      "body": {
                        "type": "service",
                        "api": "/zenvis/api/v1/entity/user-event/list?event_type=login&page=1&per_page=1",
                        "body": {"type": "tpl", "tpl": "<div style='font-size:16px'>登录事件</div><div style='font-size:28px;font-weight:700'>${total || 0}</div>"}
                      }
                    }
                  ]
                },
                {
                  "type": "chart",
                  "api": {
                    "method": "post",
                    "url": "/zenvis/api/v1/entity/trend/query",
                    "data": {"entities": ["user-event"], "time_range": {"preset": "LAST_7_DAYS"}, "granularity": "DAY"},
                    "adaptor": "var d=payload&&payload.data?payload.data:{};var o=d.echarts&&d.echarts.option?d.echarts.option:{};return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},chart_series:o.series||[]}};"
                  },
                  "config": {
                    "title": {"text": "近 7 天上报趋势"},
                    "tooltip": {"trigger": "axis"},
                    "legend": {},
                    "dataset": "${chart_dataset}",
                    "xAxis": {"type": "category"},
                    "yAxis": {"type": "value"},
                    "series": "${chart_series}"
                  }
                }
              ]
            }
            """;

    private static final String USER_EVENT_PAGE_HTML = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>用户事件管理</title>
              <style>
                body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f6f7fb; color: #1f2937; }
                header { padding: 18px 24px; background: #ffffff; border-bottom: 1px solid #e5e7eb; }
                main { padding: 18px 24px; }
                .toolbar, .panel { background: #ffffff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px; margin-bottom: 14px; }
                .toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
                input, select, button { height: 32px; border-radius: 6px; border: 1px solid #d1d5db; padding: 0 10px; }
                button { background: #2563eb; border-color: #2563eb; color: #fff; cursor: pointer; }
                table { width: 100%; border-collapse: collapse; background: #fff; }
                th, td { padding: 10px; border-bottom: 1px solid #e5e7eb; text-align: left; font-size: 13px; }
                th { color: #4b5563; background: #f9fafb; }
                .muted { color: #6b7280; }
              </style>
            </head>
            <body>
              <header>
                <h2>用户事件管理</h2>
                <div class="muted">基于 /zenvis/api/v1/entity/user-event REST API 的静态 HTML 单页面。</div>
              </header>
              <main>
                <section class="toolbar">
                  <input id="user" placeholder="用户" />
                  <select id="eventType">
                    <option value="">全部事件类型</option>
                    <option value="login">登录</option>
                    <option value="click">点击</option>
                    <option value="view">浏览</option>
                    <option value="delete">删除</option>
                    <option value="modify">修改</option>
                  </select>
                  <button onclick="loadRows()">查询</button>
                  <button onclick="createDemo()">创建演示事件</button>
                </section>
                <section class="panel">
                  <table>
                    <thead>
                      <tr><th>事件ID</th><th>用户</th><th>类型</th><th>可信度</th><th>入库时间</th><th>操作</th></tr>
                    </thead>
                    <tbody id="rows"><tr><td colspan="6">加载中...</td></tr></tbody>
                  </table>
                </section>
              </main>
              <script>
                const apiBase = '/zenvis/api/v1/entity/user-event';
                async function request(url, options) {
                  const res = await fetch(url, options);
                  const json = await res.json();
                  return json.data || json;
                }
                async function loadRows() {
                  const params = new URLSearchParams({ page: '1', per_page: '20' });
                  const user = document.getElementById('user').value.trim();
                  const eventType = document.getElementById('eventType').value;
                  if (user) params.set('user', user);
                  if (eventType) params.set('event_type', eventType);
                  const data = await request(`${apiBase}/list?${params}`);
                  const rows = data.rows || [];
                  document.getElementById('rows').innerHTML = rows.length ? rows.map(row => `
                    <tr>
                      <td>${row.event_id || ''}</td>
                      <td>${row.user || ''}</td>
                      <td>${row.event_type || ''}</td>
                      <td>${row.reliability ?? ''}</td>
                      <td>${row.server_time || ''}</td>
                      <td><button onclick="removeRow('${row.zenvis_id}')">删除</button></td>
                    </tr>
                  `).join('') : '<tr><td colspan="6">暂无数据</td></tr>';
                }
                async function createDemo() {
                  const body = {
                    event_id: crypto.randomUUID(),
                    procid: 101,
                    user: 'demo-user',
                    event_type: 'login',
                    reliability: 8.8,
                    detail: JSON.stringify({ method: 'POST', path: '/demo' }),
                    tags: '演示,可视化',
                    server_time: new Date().toISOString().slice(0, 19).replace('T', ' ')
                  };
                  await request(`${apiBase}/add`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
                  loadRows();
                }
                async function removeRow(zenvisId) {
                  await fetch(`${apiBase}/${zenvisId}`, { method: 'DELETE' });
                  loadRows();
                }
                loadRows();
              </script>
            </body>
            </html>
            """;

    private static final String USER_EVENT_DASHBOARD_HTML = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>用户事件数据看板</title>
              <style>
                body { margin: 0; min-height: 100vh; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #111827; color: #f9fafb; }
                main { padding: 24px; }
                h1 { margin: 0 0 18px; font-size: 24px; }
                .grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 14px; margin-bottom: 16px; }
                .card { background: #1f2937; border: 1px solid #374151; border-radius: 8px; padding: 16px; }
                .label { color: #9ca3af; font-size: 13px; }
                .value { margin-top: 8px; font-size: 30px; font-weight: 700; }
                .bars { display: grid; gap: 10px; margin-top: 12px; }
                .bar { display: grid; grid-template-columns: 64px 1fr 48px; gap: 10px; align-items: center; }
                .bar-line { height: 12px; border-radius: 999px; background: #334155; overflow: hidden; }
                .bar-fill { height: 100%; background: #38bdf8; }
              </style>
            </head>
            <body>
              <main>
                <h1>用户事件数据看板</h1>
                <section class="grid">
                  <div class="card"><div class="label">总上报量</div><div class="value" id="total">-</div></div>
                  <div class="card"><div class="label">登录事件</div><div class="value" id="login">-</div></div>
                  <div class="card"><div class="label">删除事件</div><div class="value" id="delete">-</div></div>
                  <div class="card"><div class="label">修改事件</div><div class="value" id="modify">-</div></div>
                </section>
                <section class="card">
                  <div class="label">事件类型分布</div>
                  <div class="bars" id="bars"></div>
                </section>
              </main>
              <script>
                const apiBase = '/zenvis/api/v1/entity/user-event';
                async function count(eventType) {
                  const params = new URLSearchParams({ page: '1', per_page: '1' });
                  if (eventType) params.set('event_type', eventType);
                  const res = await fetch(`${apiBase}/list?${params}`);
                  const json = await res.json();
                  return Number((json.data || json).total || 0);
                }
                async function loadBoard() {
                  const types = ['login', 'click', 'view', 'delete', 'modify'];
                  const values = {};
                  const total = await count('');
                  for (const type of types) values[type] = await count(type);
                  document.getElementById('total').textContent = total;
                  document.getElementById('login').textContent = values.login;
                  document.getElementById('delete').textContent = values.delete;
                  document.getElementById('modify').textContent = values.modify;
                  const max = Math.max(...Object.values(values), 1);
                  document.getElementById('bars').innerHTML = types.map(type => `
                    <div class="bar">
                      <span>${type}</span>
                      <span class="bar-line"><span class="bar-fill" style="width:${Math.round(values[type] / max * 100)}%"></span></span>
                      <span>${values[type]}</span>
                    </div>
                  `).join('');
                }
                loadBoard();
                setInterval(loadBoard, 30000);
              </script>
            </body>
            </html>
            """;

    private final ConfigService configService;

    public DataVisualizationDemoResponseService(ConfigService configService,
                                                MenuService menuService,
                                                DashboardService dashboardService) {
        this.configService = configService;
    }

    public static boolean isUserEventVisualizationDemoPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return false;
        }
        String normalized = prompt.trim();
        return CHART_EXAMPLE_PROMPT.equals(normalized)
                || PAGE_EXAMPLE_PROMPT.equals(normalized)
                || SIDEBAR_APP_EXAMPLE_PROMPT.equals(normalized)
                || DASHBOARD_EXAMPLE_PROMPT.equals(normalized)
                || MENU_EXAMPLE_PROMPT.equals(normalized);
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        return findResponse(chatSession, chatId, prompt, user, McpToolContext.empty());
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        if (isAddChartLibraryPrompt(prompt)) {
            return Optional.of(streamResponse(addChartLibraryResponse()));
        }
        if (isAbandonVisualizationConfigPrompt(prompt)) {
            return Optional.of(streamResponse(abandonVisualizationConfigResponse()));
        }
        if (isReviseVisualizationConfigPrompt(prompt)) {
            return Optional.of(streamAction(
                    () -> reviseLatestVisualizationConfig(chatSession, mcpToolContext)));
        }
        if (isApplyVisualizationConfigPrompt(prompt)) {
            return Optional.of(streamAction(
                    () -> applyLatestVisualizationConfig(chatSession, mcpToolContext)));
        }
        if (isChartInfoSubmitted(prompt)) {
            return Optional.of(streamResponse(buildChartPreviewResponse()));
        }
        if (isSinglePageInfoSubmitted(prompt)) {
            return Optional.of(streamResponse(buildSinglePageConfigResponse(prompt)));
        }
        if (isSidebarAppInfoSubmitted(prompt)) {
            return Optional.of(streamResponse(buildSidebarAppConfigResponse()));
        }
        if (isDashboardInfoSubmitted(prompt) || isDashboardLinkInfoSubmitted(prompt)) {
            if (selectDashboardType(prompt).equals("link") && !StringUtils.hasText(extractUrl(prompt))) {
                return Optional.of(streamResponse(buildDashboardLinkInfoStepsResponse()));
            }
            return Optional.of(streamResponse(buildDashboardConfigResponse(prompt)));
        }
        if (isChartRequirement(prompt)) {
            return Optional.of(streamResponse(withMetadataNotice(buildChartInfoStepsResponse())));
        }
        if (isSinglePageRequirement(prompt)) {
            return Optional.of(streamResponse(withMetadataNotice(buildSinglePageInfoStepsResponse())));
        }
        if (isSidebarAppRequirement(prompt)) {
            return Optional.of(streamResponse(withMetadataNotice(buildSidebarAppInfoStepsResponse())));
        }
        if (isDashboardRequirement(prompt)) {
            return Optional.of(streamResponse(withMetadataNotice(buildDashboardInfoStepsResponse())));
        }
        if (isMenuRequirement(prompt)) {
            return Optional.of(streamAction(
                    () -> buildMenuConfirmationResponse(mcpToolContext)));
        }
        return Optional.empty();
    }

    private Flux<String> streamAction(Callable<String> action) {
        return Mono.fromCallable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamResponse);
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        return Flux.fromIterable(splitResponseChunks(response))
                .delayElements(DEMO_STREAM_DELAY);
    }

    private List<String> splitResponseChunks(String response) {
        List<String> chunks = new java.util.ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int nextLineBreak = response.indexOf('\n', index);
            int limit = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            int end = nextLineBreak >= index && nextLineBreak < limit ? nextLineBreak + 1 : limit;
            chunks.add(response.substring(index, end));
            index = end;
        }
        return chunks;
    }

    private static boolean isChartRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (CHART_EXAMPLE_PROMPT.equals(prompt.trim())
                || prompt.contains("# 用户事件数据可视化：临时图表"));
    }

    private static boolean isSinglePageRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (PAGE_EXAMPLE_PROMPT.equals(prompt.trim())
                || prompt.contains("# 用户事件数据可视化：单页面应用"));
    }

    private static boolean isSidebarAppRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (SIDEBAR_APP_EXAMPLE_PROMPT.equals(prompt.trim())
                || prompt.contains("# 用户事件数据可视化：带侧边栏应用"));
    }

    private static boolean isDashboardRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (DASHBOARD_EXAMPLE_PROMPT.equals(prompt.trim())
                || prompt.contains("# 用户事件数据可视化：数据看板"));
    }

    private static boolean isMenuRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (MENU_EXAMPLE_PROMPT.equals(prompt.trim())
                || prompt.contains("# 用户事件数据可视化：添加菜单"));
    }

    private boolean isChartInfoSubmitted(String prompt) {
        return prompt.contains("用户事件临时图表信息确认");
    }

    private boolean isSinglePageInfoSubmitted(String prompt) {
        return prompt.contains("用户事件单页面应用实现方式确认");
    }

    private boolean isSidebarAppInfoSubmitted(String prompt) {
        return prompt.contains("用户事件侧边栏应用信息确认");
    }

    private boolean isDashboardInfoSubmitted(String prompt) {
        return prompt.contains("用户事件数据看板信息确认");
    }

    private boolean isDashboardLinkInfoSubmitted(String prompt) {
        return prompt.contains("用户事件外链看板地址确认");
    }

    private boolean isAddChartLibraryPrompt(String prompt) {
        return prompt.contains("我已确认把上一轮临时图表加入图表库")
                || prompt.contains("data_visualization.add_chart_library");
    }

    private boolean isApplyVisualizationConfigPrompt(String prompt) {
        return prompt.contains("我已确认并授权应用上一轮数据可视化配置")
                || prompt.contains("data_visualization.apply_config");
    }

    private boolean isAbandonVisualizationConfigPrompt(String prompt) {
        return prompt.contains("我选择放弃本次数据可视化配置")
                || prompt.contains("已放弃本次数据可视化配置");
    }

    private boolean isReviseVisualizationConfigPrompt(String prompt) {
        return prompt.contains("我需要补充信息继续更新数据可视化配置")
                || prompt.contains("已补充数据可视化配置调整要求");
    }

    private String withMetadataNotice(String response) {
        if (metadataAvailable()) {
            return response;
        }
        return """
                ```zenvis:notice
                {"title":"元数据配置提醒","content":"该演示基于 user-event 用户事件实体。如果当前环境尚未生成用户事件元数据，请先通过数据接入智能体的用户事件数据接入示例添加元数据配置。","level":"warning"}
                ```

                """ + response;
    }

    private boolean metadataAvailable() {
        try {
            return configService.fileExistsInConfigPath("meta", "user_event.json")
                    || configService.fileExistsInConfigPath("meta", "user-event.json");
        } catch (Exception e) {
            return false;
        }
    }

    private String buildChartInfoStepsResponse() {
        return """
                我会先确认临时图表的统计口径，再生成可预览的图表和可复用的 amis 配置。

                ```zenvis:info-steps
                {
                  "title": "用户事件临时图表信息确认",
                  "content": "请补充时间范围、图表类型和统计维度。",
                  "submitLabel": "生成临时图表",
                  "steps": [
                    {
                      "id": "time_range",
                      "title": "时间范围",
                      "required": true,
                      "description": "选择本次查看的用户事件上报时间范围。",
                      "suggestions": [
                        {"label": "近 24 小时", "value": "查看近 24 小时用户事件上报情况"},
                        {"label": "近 7 天", "value": "查看近 7 天用户事件上报情况"},
                        {"label": "今天", "value": "查看今天 00:00 至当前时间的用户事件上报情况"}
                      ],
                      "placeholder": "例如：2026-07-08 00:00 到 2026-07-09 00:00"
                    },
                    {
                      "id": "chart_type",
                      "title": "图表类型",
                      "required": true,
                      "description": "选择曲线图或柱状图。",
                      "suggestions": [
                        {"label": "曲线图", "value": "使用曲线图展示上报趋势"},
                        {"label": "柱状图", "value": "使用柱状图展示上报量"},
                        {"label": "曲线图并区分事件类型", "value": "使用曲线图并按 event_type 分组展示"}
                      ],
                      "placeholder": "也可以补充希望展示的其他图表类型"
                    },
                    {
                      "id": "dimension",
                      "title": "统计维度",
                      "required": true,
                      "description": "选择趋势聚合维度。",
                      "suggestions": [
                        {"label": "按小时 + 事件类型", "value": "按 server_time 小时聚合，并按 event_type 分组"},
                        {"label": "按天 + 事件类型", "value": "按 server_time 天聚合，并按 event_type 分组"},
                        {"label": "总上报量趋势", "value": "仅展示总上报量趋势"}
                      ],
                      "placeholder": "例如：按小时统计登录、点击、浏览、删除、修改事件"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildSinglePageInfoStepsResponse() {
        return """
                我会先确认单页面应用的实现方式，再生成可落地的配置和菜单。

                ```zenvis:info-steps
                {
                  "title": "用户事件单页面应用实现方式确认",
                  "content": "请选择用低代码 amis 还是静态 HTML 实现用户事件增删改查单页面。",
                  "submitLabel": "生成单页面应用配置",
                  "steps": [
                    {
                      "id": "implementation",
                      "title": "实现方式",
                      "required": true,
                      "description": "低代码方式会生成 open_config 配置目录和低代码页面菜单；静态 HTML 会生成 html-page_config 文件和 HTML 页面菜单。",
                      "suggestions": [
                        {"label": "低代码 amis", "value": "使用低代码 amis 方式实现单页面 CRUD 应用"},
                        {"label": "静态 HTML", "value": "使用静态 HTML 单页面直接调用实体 REST API"}
                      ],
                      "placeholder": "例如：使用低代码 amis"
                    },
                    {
                      "id": "fields",
                      "title": "展示字段",
                      "required": false,
                      "description": "确认需要展示和编辑的字段。",
                      "suggestions": [
                        {"label": "使用完整字段", "value": "展示 event_id、procid、user、event_type、reliability、detail、tags、server_time，行操作使用 zenvis_id"},
                        {"label": "使用核心字段", "value": "展示 event_id、user、event_type、reliability、server_time，行操作使用 zenvis_id"}
                      ],
                      "placeholder": "也可以补充字段裁剪或排序要求"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildSidebarAppInfoStepsResponse() {
        return """
                我会生成一个带侧边栏的低代码用户事件数据应用。请确认侧边栏菜单和展示重点。

                ```zenvis:info-steps
                {
                  "title": "用户事件侧边栏应用信息确认",
                  "content": "首页和管理页面会固定包含；可以继续选择是否加入趋势页或明细页。",
                  "submitLabel": "生成侧边栏应用配置",
                  "steps": [
                    {
                      "id": "menus",
                      "title": "侧边栏菜单",
                      "required": true,
                      "description": "固定包含首页和管理页面，可补充其他菜单。",
                      "suggestions": [
                        {"label": "首页 + 管理页面", "value": "侧边栏包含首页和管理页面"},
                        {"label": "首页 + 管理页面 + 上报趋势", "value": "侧边栏包含首页、管理页面和上报趋势"},
                        {"label": "首页 + 管理页面 + 上报趋势 + 明细页", "value": "侧边栏包含首页、管理页面、上报趋势和明细页"}
                      ],
                      "placeholder": "也可以说明希望的菜单名称"
                    },
                    {
                      "id": "style",
                      "title": "应用重点",
                      "required": false,
                      "description": "说明应用更偏运营概览还是管理操作。",
                      "suggestions": [
                        {"label": "运营概览优先", "value": "首页突出上报趋势和事件类型分布"},
                        {"label": "管理操作优先", "value": "管理页面突出查询、编辑和删除操作"}
                      ],
                      "placeholder": "例如：首页展示趋势，管理页展示 CRUD"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildDashboardInfoStepsResponse() {
        return """
                我会先确认看板实现方式，再生成对应看板配置。

                ```zenvis:info-steps
                {
                  "title": "用户事件数据看板信息确认",
                  "content": "请选择低代码、静态 HTML 或外链接方式。",
                  "submitLabel": "生成看板配置",
                  "steps": [
                    {
                      "id": "implementation",
                      "title": "实现方式",
                      "required": true,
                      "description": "低代码和静态 HTML 会生成系统内配置；外链接方式需要继续补充 URL。",
                      "suggestions": [
                        {"label": "低代码看板", "value": "使用低代码 amis 页面实现数据看板"},
                        {"label": "静态 HTML 看板", "value": "使用静态 HTML 页面实现数据看板"},
                        {"label": "外链接看板", "value": "使用外链接方式接入已有看板"}
                      ],
                      "placeholder": "例如：低代码看板"
                    },
                    {
                      "id": "metrics",
                      "title": "看板指标",
                      "required": false,
                      "description": "确认看板展示指标。",
                      "suggestions": [
                        {"label": "上报量 + 类型分布", "value": "展示总上报量、登录事件、删除事件、修改事件和事件类型分布"},
                        {"label": "趋势优先", "value": "重点展示近 7 天上报趋势"},
                        {"label": "运营概览", "value": "展示核心指标卡片、趋势图和事件类型分布"}
                      ],
                      "placeholder": "也可以补充指标名称和布局要求"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildMenuConfirmationResponse(McpToolContext mcpToolContext) {
        try {
            Map<String, Object> typeResult = toolResultObject(callTool(
                    mcpToolContext,
                    "menu_type_options",
                    Map.of()));
            List<Map<String, Object>> typeOptions = listOfMaps(typeResult.get("options"));
            boolean externalAppAvailable = typeOptions.stream().anyMatch(option ->
                    "EXTERNAL_APP".equals(String.valueOf(option.get("value"))));
            if (!externalAppAvailable) {
                throw new IllegalStateException(
                        "menu_type_options 未返回 EXTERNAL_APP 菜单类型");
            }

            Map<String, Object> parentResult = toolResultObject(callTool(
                    mcpToolContext,
                    "menu_parent_options",
                    Map.of()));
            List<Map<String, Object>> parentOptions =
                    listOfMaps(parentResult.get("options"));
            List<Map<String, Object>> existing = listOfMaps(toolResultObject(callTool(
                    mcpToolContext,
                    "menu_list",
                    Map.of("request", Map.of(
                            "page", 1,
                            "per_page", 100,
                            "name", MENU_DEMO_NAME)))).get("rows"));
            Map<String, Object> request = menuDemoRequest();
            Optional<Map<String, Object>> existingTarget = existing.stream()
                    .filter(candidate -> MENU_DEMO_NAME.equals(
                            String.valueOf(candidate.get("name")))
                            || MENU_DEMO_SOURCE.equals(
                            String.valueOf(candidate.get("source"))))
                    .findFirst();
            if (existingTarget.isPresent()
                    && !menuRequestMatches(request, existingTarget.get())) {
                throw new IllegalStateException(
                        "系统已存在同名或同 source 但内容不同的菜单，不能生成覆盖方案");
            }
            boolean createRequired = existingTarget.isEmpty();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("title", "确认添加用户事件菜单");
            card.put("content",
                    "已通过 MCP 查询菜单类型、父级菜单和同名菜单。"
                            + (createRequired
                            ? "确认后将调用 menu_create，并由平台展示高风险 MCP 审批；"
                            : "系统中已存在完全一致的菜单，确认后将幂等复用；")
                            + "随后调用 menu_view 读回校验。");
            card.put("action", ACTION_APPLY_CONFIG);
            card.put("actions", List.of("apply_config", "abandon", "revise"));
            card.put("demoScenario", "menu");
            card.put("menu", Map.of("request", request));
            card.put("mcpEvidence", List.of(
                    Map.of(
                            "tool", "menu_type_options",
                            "request", Map.of(),
                            "status", "success",
                            "resultSummary", "可用菜单类型 " + typeOptions.size() + " 个"),
                    Map.of(
                            "tool", "menu_parent_options",
                            "request", Map.of(),
                            "status", "success",
                            "resultSummary", "可选父级菜单 " + parentOptions.size() + " 个"),
                    Map.of(
                            "tool", "menu_list",
                            "request", Map.of(
                                    "page", 1,
                                    "per_page", 100,
                                    "name", MENU_DEMO_NAME),
                            "status", "success",
                            "resultSummary", "同名一级菜单 " + existing.size() + " 个")
            ));

            return """
                    已通过 MCP 接口查询系统菜单能力，并生成确定性的菜单创建方案。

                    - `menu_type_options`，参数：`{}`
                    - `menu_parent_options`，参数：`{}`
                    - `menu_list`，参数：`{"page":1,"per_page":100,"name":"%s"}`
                    - 目标菜单：%s
                    - 类型：EXTERNAL_APP
                    - 层级：LEVEL_1，parentId=0
                    - 目标地址：%s

                    ```zenvis:confirm
                    %s
                    ```
                    """.formatted(
                    MENU_DEMO_NAME,
                    MENU_DEMO_NAME,
                    MENU_DEMO_URL,
                    JacksonUtil.toJson(card));
        } catch (Exception e) {
            log.error("查询添加菜单演示所需 MCP 信息失败: {}", e.getMessage(), e);
            return menuFailureResponse("菜单方案查询", e);
        }
    }

    private String buildDashboardLinkInfoStepsResponse() {
        return """
                外链接看板需要补充可访问的看板 URL。

                ```zenvis:info-steps
                {
                  "title": "用户事件外链看板地址确认",
                  "content": "请提供外链接看板地址，确认后会创建 LINK 类型看板。",
                  "submitLabel": "生成外链看板配置",
                  "steps": [
                    {
                      "id": "url",
                      "title": "外链接地址",
                      "required": true,
                      "description": "请输入以 http:// 或 https:// 开头的看板地址。",
                      "suggestions": [
                        {"label": "演示外链", "value": "https://example.com/user-event-dashboard"},
                        {"label": "内网看板", "value": "https://dashboard.example.local/user-event"}
                      ],
                      "placeholder": "例如：https://dashboard.example.com/user-event"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildChartPreviewResponse() {
        return """
                已根据补充信息生成临时图表。图表会直接在对话中预览，下面的 amis 配置可加入图表库后继续复用。

                ```zenvis:visualization-chart-preview
                {
                  "title": "用户事件上报趋势图",
                  "content": "按系统创建时间统计近 7 天用户事件上报趋势。",
                  "chartType": "line",
                  "entity": "%s",
                  "api": "/zenvis/api/v1/entity/trend/query",
                  "echarts": {
                    "chart_type": "line",
                    "option": %s
                  },
                  "amisConfig": %s,
                  "action": "%s"
                }
                ```
                """.formatted(
                ENTITY,
                CHART_ECHARTS_OPTION.trim(),
                CHART_AMIS_CONFIG.trim(),
                ACTION_ADD_CHART_LIBRARY
        );
    }

    private String addChartLibraryResponse() {
        return """
                已加入本次会话图表库。

                ```zenvis:visualization-chart-record
                {
                  "id": "demo-user-event-report-trend",
                  "title": "图表库记录已创建",
                  "name": "用户事件上报趋势图",
                  "description": "按系统创建时间统计近 7 天用户事件上报趋势的临时 amis 图表配置。",
                  "entity": "%s",
                  "chartType": "line",
                  "api": "/zenvis/api/v1/entity/trend/query",
                  "status": "temporary",
                  "echartsOption": %s,
                  "amisConfig": %s,
                  "config": %s
                }
                ```
                """.formatted(
                ENTITY,
                CHART_ECHARTS_OPTION.trim(),
                CHART_AMIS_CONFIG.trim(),
                CHART_AMIS_CONFIG.trim());
    }

    private String buildSinglePageConfigResponse(String prompt) {
        String implementation = selectImplementation(prompt);
        if ("html".equals(implementation)) {
            return """
                    已生成用户事件静态 HTML 单页面配置，请确认后写入系统。

                    ```zenvis:html-page-config
                    %s
                    ```

                    ```zenvis:confirm
                    {"title":"是否写入用户事件 HTML 单页面","content":"确认后平台将通过 config_tree 检查配置；新文件依次调用 config_ensure_root、config_add，随后调用 config_apply（高风险 MCP 审批）和 config_read 读回校验；菜单调用 menu_list、menu_create（高风险 MCP 审批）和 menu_view。","action":"%s","actions":%s,"demoScenario":"single_page","implementation":"html"}
                    ```
                    """.formatted(USER_EVENT_PAGE_HTML.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
        }
        return """
                已生成用户事件低代码单页面配置，请确认后写入系统。

                ```zenvis:low-code-page-config
                %s
                ```

                ```zenvis:confirm
                {"title":"是否写入用户事件低代码单页面","content":"确认后平台将通过 config_tree 检查配置；新文件依次调用 config_ensure_root、config_add，随后调用 config_apply（高风险 MCP 审批）和 config_read 读回校验；两个菜单分别调用 menu_list、menu_create（高风险 MCP 审批）和 menu_view。","action":"%s","actions":%s,"demoScenario":"single_page","implementation":"low_code"}
                ```
                """.formatted(USER_EVENT_PAGE_CONFIG.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
    }

    private String buildSidebarAppConfigResponse() {
        return """
                已生成带侧边栏的用户事件低代码应用配置，请确认后写入系统。

                ```zenvis:low-code-app-config
                %s
                ```

                ```zenvis:confirm
                {"title":"是否写入用户事件侧边栏应用","content":"确认后平台将为 site.json、index.json、manage.json、trend.json 执行 config_tree、必要时 config_ensure_root/config_add、config_apply（高风险 MCP 审批）及 config_read；两个菜单分别执行 menu_list、menu_create（高风险 MCP 审批）和 menu_view。","action":"%s","actions":%s,"demoScenario":"sidebar_app","implementation":"low_code_app"}
                ```
                """.formatted(USER_EVENT_APP_SITE_CONFIG.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
    }

    private String buildDashboardConfigResponse(String prompt) {
        String dashboardType = selectDashboardType(prompt);
        if ("link".equals(dashboardType)) {
            String url = extractUrl(prompt);
            return """
                    已生成用户事件外链接看板配置，请确认后创建看板。

                    ```zenvis:confirm
                    {"title":"是否创建用户事件外链看板","content":"确认后平台将调用 dashboard_list 检查同名看板，必要时调用 dashboard_create（高风险 MCP 审批），并用 dashboard_view 读回校验。外链地址：%s","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"link","url":"%s"}
                    ```
                    """.formatted(escapeJson(url), ACTION_APPLY_CONFIG, DECISION_ACTIONS, escapeJson(url));
        }
        if ("html".equals(dashboardType)) {
            return """
                    已生成用户事件静态 HTML 看板页面，请确认后写入系统并创建看板。

                    ```zenvis:html-page-config
                    %s
                    ```

                    ```zenvis:confirm
                    {"title":"是否写入用户事件 HTML 看板","content":"确认后平台将通过 config_tree 检查配置；必要时调用 config_ensure_root/config_add，再调用 config_apply（高风险 MCP 审批）和 config_read；看板调用 dashboard_list、dashboard_create（高风险 MCP 审批）和 dashboard_view。","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"html"}
                    ```
                    """.formatted(USER_EVENT_DASHBOARD_HTML.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
        }
        return """
                已生成用户事件低代码看板配置，请确认后写入系统并创建看板。

                ```zenvis:low-code-page-config
                %s
                ```

                ```zenvis:confirm
                {"title":"是否写入用户事件低代码看板","content":"确认后平台将执行 config_tree、必要时 config_ensure_root/config_add、config_apply（高风险 MCP 审批）和 config_read；配置菜单执行 menu_list/menu_create/menu_view；看板执行 dashboard_list/dashboard_create（高风险 MCP 审批）/dashboard_view。","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"low_code"}
                ```
                """.formatted(USER_EVENT_DASHBOARD_CONFIG.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
    }

    private String selectImplementation(String prompt) {
        String selected = selectedAnswerText(prompt);
        String source = StringUtils.hasText(selected) ? selected : prompt;
        if (source.contains("静态 HTML") || source.contains("HTML") || source.contains("html")) {
            return "html";
        }
        return "low_code";
    }

    private String selectDashboardType(String prompt) {
        if (prompt.contains("用户事件外链看板地址确认")) {
            return "link";
        }
        String selected = selectedAnswerText(prompt);
        String source = StringUtils.hasText(selected) ? selected : prompt;
        if (source.contains("外链接") || source.contains("外链") || source.contains("LINK") || source.contains("link")) {
            return "link";
        }
        if (source.contains("静态 HTML") || source.contains("HTML") || source.contains("html")) {
            return "html";
        }
        return "low_code";
    }

    @SuppressWarnings("unchecked")
    private String selectedAnswerText(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        int start = prompt.indexOf('{');
        int end = prompt.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        try {
            Map<String, Object> payload = JacksonUtil.toMap(
                    prompt.substring(start, end + 1),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            Object answers = payload.get("answers");
            if (!(answers instanceof List<?> answerList)) {
                return "";
            }
            return answerList.stream()
                    .filter(Map.class::isInstance)
                    .map(answer -> ((Map<String, Object>) answer).get("value"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        } catch (RuntimeException e) {
            log.debug("解析数据可视化补充信息选项失败，将回退到全文判断: {}", e.getMessage());
            return "";
        }
    }

    private String applyLatestVisualizationConfig(ChatSession chatSession,
                                                  McpToolContext mcpToolContext) {
        String history = allMessagesText(chatSession);
        int singlePageIndex = history.lastIndexOf("\"demoScenario\":\"single_page\"");
        if (singlePageIndex < 0) {
            singlePageIndex = history.lastIndexOf("\"demoScenario\": \"single_page\"");
        }
        int sidebarIndex = history.lastIndexOf("\"demoScenario\":\"sidebar_app\"");
        if (sidebarIndex < 0) {
            sidebarIndex = history.lastIndexOf("\"demoScenario\": \"sidebar_app\"");
        }
        int dashboardIndex = history.lastIndexOf("\"demoScenario\":\"dashboard\"");
        if (dashboardIndex < 0) {
            dashboardIndex = history.lastIndexOf("\"demoScenario\": \"dashboard\"");
        }
        int menuIndex = history.lastIndexOf("\"demoScenario\":\"menu\"");
        if (menuIndex < 0) {
            menuIndex = history.lastIndexOf("\"demoScenario\": \"menu\"");
        }
        if (menuIndex >= singlePageIndex
                && menuIndex >= sidebarIndex
                && menuIndex >= dashboardIndex
                && menuIndex >= 0) {
            return applyMenuDemo(mcpToolContext);
        }
        if (singlePageIndex >= sidebarIndex
                && singlePageIndex >= dashboardIndex
                && singlePageIndex >= menuIndex
                && singlePageIndex >= 0) {
            String scope = history.substring(singlePageIndex);
            return scope.contains("\"implementation\":\"html\"") || scope.contains("\"implementation\": \"html\"")
                    ? applySinglePageHtml(mcpToolContext)
                    : applySinglePageLowCode(mcpToolContext);
        }
        if (sidebarIndex >= singlePageIndex
                && sidebarIndex >= dashboardIndex
                && sidebarIndex >= menuIndex
                && sidebarIndex >= 0) {
            return applySidebarApp(mcpToolContext);
        }
        if (dashboardIndex >= menuIndex && dashboardIndex >= 0) {
            String scope = history.substring(dashboardIndex);
            if (scope.contains("\"dashboardType\":\"link\"") || scope.contains("\"dashboardType\": \"link\"")) {
                return applyDashboardLink(extractUrl(scope), mcpToolContext);
            }
            if (scope.contains("\"dashboardType\":\"html\"") || scope.contains("\"dashboardType\": \"html\"")) {
                return applyDashboardHtml(mcpToolContext);
            }
            return applyDashboardLowCode(mcpToolContext);
        }
        return """
                ```zenvis:notice
                {"title":"未找到待应用配置","content":"没有找到上一轮数据可视化演示确认卡，请重新选择示例并生成配置。","level":"warning"}
                ```
                """;
    }

    private String abandonVisualizationConfigResponse() {
        return """
                已放弃本次数据可视化配置，未写入 open_config，也不会创建菜单或看板。

                ```zenvis:notice
                {"title":"本次配置已放弃","content":"数据可视化演示流程已结束；如需重新生成，可再次发送数据可视化示例需求。","level":"info"}
                ```
                """;
    }

    private String reviseLatestVisualizationConfig(ChatSession chatSession,
                                                   McpToolContext mcpToolContext) {
        String history = allMessagesText(chatSession);
        int singlePageIndex = history.lastIndexOf("\"demoScenario\":\"single_page\"");
        if (singlePageIndex < 0) {
            singlePageIndex = history.lastIndexOf("\"demoScenario\": \"single_page\"");
        }
        int sidebarIndex = history.lastIndexOf("\"demoScenario\":\"sidebar_app\"");
        if (sidebarIndex < 0) {
            sidebarIndex = history.lastIndexOf("\"demoScenario\": \"sidebar_app\"");
        }
        int dashboardIndex = history.lastIndexOf("\"demoScenario\":\"dashboard\"");
        if (dashboardIndex < 0) {
            dashboardIndex = history.lastIndexOf("\"demoScenario\": \"dashboard\"");
        }
        int menuIndex = history.lastIndexOf("\"demoScenario\":\"menu\"");
        if (menuIndex < 0) {
            menuIndex = history.lastIndexOf("\"demoScenario\": \"menu\"");
        }
        if (menuIndex >= singlePageIndex
                && menuIndex >= sidebarIndex
                && menuIndex >= dashboardIndex
                && menuIndex >= 0) {
            return """
                    菜单演示采用固定且经过 MCP 校验的外部看板入口，已重新查询系统菜单能力并生成方案。

                    %s
                    """.formatted(buildMenuConfirmationResponse(mcpToolContext).trim());
        }
        if (singlePageIndex >= sidebarIndex
                && singlePageIndex >= dashboardIndex
                && singlePageIndex >= menuIndex
                && singlePageIndex >= 0) {
            String scope = history.substring(singlePageIndex);
            String implementation = scope.contains("\"implementation\":\"html\"") || scope.contains("\"implementation\": \"html\"")
                    ? "使用静态 HTML 单页面直接调用实体 REST API"
                    : "使用低代码 amis 方式实现单页面 CRUD 应用";
            return """
                    已根据补充信息更新用户事件单页面应用配置，请再次确认后续处理。

                    %s
                    """.formatted(buildSinglePageConfigResponse("""
                    {"answers":[{"value":"%s"}]}
                    """.formatted(implementation)).trim());
        }
        if (sidebarIndex >= singlePageIndex
                && sidebarIndex >= dashboardIndex
                && sidebarIndex >= menuIndex
                && sidebarIndex >= 0) {
            return """
                    已根据补充信息更新用户事件侧边栏应用配置，请再次确认后续处理。

                    %s
                    """.formatted(buildSidebarAppConfigResponse().trim());
        }
        if (dashboardIndex >= menuIndex && dashboardIndex >= 0) {
            String scope = history.substring(dashboardIndex);
            String dashboardType;
            if (scope.contains("\"dashboardType\":\"link\"") || scope.contains("\"dashboardType\": \"link\"")) {
                dashboardType = "使用外链接方式接入已有看板";
            } else if (scope.contains("\"dashboardType\":\"html\"") || scope.contains("\"dashboardType\": \"html\"")) {
                dashboardType = "使用静态 HTML 页面实现数据看板";
            } else {
                dashboardType = "使用低代码 amis 页面实现数据看板";
            }
            String url = extractUrl(scope);
            return """
                    已根据补充信息更新用户事件数据看板配置，请再次确认后续处理。

                    %s
                    """.formatted(buildDashboardConfigResponse("""
                    {"answers":[{"value":"%s"}]} %s
                    """.formatted(dashboardType, url)).trim());
        }
        return """
                ```zenvis:notice
                {"title":"未找到待更新配置","content":"没有找到上一轮数据可视化演示确认卡，请重新选择示例并生成配置。","level":"warning"}
                ```
                """;
    }

    private String applyMenuDemo(McpToolContext mcpToolContext) {
        Map<String, Object> request = menuDemoRequest();
        try {
            List<Map<String, Object>> candidates = listOfMaps(toolResultObject(callTool(
                    mcpToolContext,
                    "menu_list",
                    Map.of("request", Map.of(
                            "page", 1,
                            "per_page", 100,
                            "name", MENU_DEMO_NAME)))).get("rows"));
            Map<String, Object> matched = candidates.stream()
                    .filter(candidate -> MENU_DEMO_NAME.equals(
                            String.valueOf(candidate.get("name")))
                            || MENU_DEMO_SOURCE.equals(
                            String.valueOf(candidate.get("source"))))
                    .findFirst()
                    .orElse(null);

            long menuId;
            if (matched == null) {
                Map<String, Object> created = toolResultObject(callTool(
                        mcpToolContext,
                        "menu_create",
                        Map.of("request", request)));
                menuId = longValue(created.get("id"));
                if (menuId <= 0) {
                    throw new IllegalStateException(
                            "menu_create 未返回有效菜单 ID："
                                    + describeToolResult(JacksonUtil.toJson(created)));
                }
            } else {
                if (!menuRequestMatches(request, matched)) {
                    throw new IllegalStateException(
                            "已存在同名或同 source 但内容不同的菜单，禁止覆盖");
                }
                menuId = longValue(matched.get("id"));
                if (menuId <= 0) {
                    throw new IllegalStateException("menu_list 返回的已有菜单缺少有效 ID");
                }
            }

            Map<String, Object> readBack = toolResultObject(callTool(
                    mcpToolContext,
                    "menu_view",
                    Map.of("id", menuId)));
            if (!menuRequestMatches(request, readBack)) {
                throw new IllegalStateException(
                        "menu_view 读回与已确认菜单方案不一致："
                                + JacksonUtil.toJson(readBack));
            }
            MenuVo menu = JacksonConfig.OBJECT_MAPPER.convertValue(
                    readBack,
                    MenuVo.class);
            return """
                    菜单已通过 MCP 审批创建，并完成 `menu_view` 读回校验。

                    执行顺序：`menu_list → %smenu_view`

                    %s
                    """.formatted(
                    matched == null ? "menu_create（已审批） → " : "",
                    menuRecord("用户事件外部看板菜单已创建", menu));
        } catch (Exception e) {
            log.error("执行添加菜单 MCP 演示失败: {}", e.getMessage(), e);
            return menuFailureResponse("菜单创建或读回", e);
        }
    }

    private Map<String, Object> menuDemoRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", MENU_DEMO_NAME);
        request.put("type", MenuType.EXTERNAL_APP.name());
        request.put("route", MenuType.EXTERNAL_APP.getRoute());
        request.put("level", MenuLevel.LEVEL_1.name());
        request.put("parentId", 0);
        request.put("params", MENU_DEMO_URL);
        request.put("superscript", "演示");
        request.put("source", MENU_DEMO_SOURCE);
        request.put("createRootPath", false);
        return request;
    }

    private boolean menuRequestMatches(Map<String, Object> request,
                                       Map<String, Object> actual) {
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        return String.valueOf(request.get("name")).equals(
                String.valueOf(actual.get("name")))
                && String.valueOf(request.get("type")).equals(
                String.valueOf(actual.get("type")))
                && String.valueOf(request.get("route")).equals(
                String.valueOf(actual.get("route")))
                && String.valueOf(request.get("level")).equals(
                String.valueOf(actual.get("level")))
                && longValue(request.get("parentId"))
                == longValue(actual.get("parentId"))
                && String.valueOf(request.get("params")).equals(
                String.valueOf(actual.get("params")))
                && String.valueOf(request.get("superscript")).equals(
                String.valueOf(actual.get("superscript")))
                && String.valueOf(request.get("source")).equals(
                String.valueOf(actual.get("source")));
    }

    private Map<String, Object> toolResultObject(String result) {
        if (!StringUtils.hasText(result)) {
            return Map.of();
        }
        Map<String, Object> parsed = JacksonUtil.toMap(
                result,
                new TypeReference<Map<String, Object>>() {
                });
        Object data = parsed.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            dataMap.forEach((key, value) ->
                    normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return parsed;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entryValue) ->
                    normalized.put(String.valueOf(key), entryValue));
            result.add(normalized);
        }
        return result;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String callTool(McpToolContext mcpToolContext,
                            String toolName,
                            Map<String, Object> arguments) {
        if (mcpToolContext == null
                || mcpToolContext.toolCallbackProvider() == null
                || mcpToolContext.toolCallbackProvider().getToolCallbacks() == null) {
            throw new IllegalStateException("演示所需 MCP 工具上下文不可用");
        }
        ToolCallback callback = Arrays.stream(
                        mcpToolContext.toolCallbackProvider().getToolCallbacks())
                .filter(tool -> tool != null
                        && tool.getToolDefinition() != null
                        && toolName.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "演示所需 MCP 工具不可用：" + toolName));

        Map<String, Object> context = new LinkedHashMap<>();
        McpInvocationContext invocationContext =
                mcpToolContext.invocationContext();
        if (invocationContext != null) {
            context.put(McpInvocationContext.TOOL_CONTEXT_KEY, invocationContext);
        }
        ToolRuntimeContext runtimeContext =
                mcpToolContext.toolRuntimeContext();
        if (runtimeContext != null) {
            context.put(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext);
        }
        return callback.call(
                JacksonUtil.toJson(arguments),
                new ToolContext(context));
    }

    private String menuFailureResponse(String stage, Exception exception) {
        return """
                ```zenvis:notice
                {"title":"添加菜单演示失败","content":"失败阶段：%s\\n真实错误：%s\\n未生成菜单成功记录；若审批被拒绝，系统不会创建菜单。","level":"error"}
                ```
                """.formatted(
                escapeJson(stage),
                escapeJson(safeError(exception)));
    }

    private String describeToolResult(String result) {
        if (!StringUtils.hasText(result)) {
            return "工具未返回结果";
        }
        try {
            Map<String, Object> parsed = JacksonUtil.toMap(
                    result,
                    new TypeReference<Map<String, Object>>() {
                    });
            String status = String.valueOf(parsed.getOrDefault("status", ""));
            String message = String.valueOf(parsed.getOrDefault("message", ""));
            if (StringUtils.hasText(status) || StringUtils.hasText(message)) {
                return "status=" + status
                        + (StringUtils.hasText(message) ? "，" + message : "");
            }
        } catch (RuntimeException ignored) {
            // 使用受限的纯文本摘要。
        }
        return result.length() <= 500
                ? result : result.substring(0, 500) + "...";
    }

    private String safeError(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        String sanitized = message.replaceAll(
                "(?i)(password|passwd|token|secret|api[_-]?key)"
                        + "\\s*[:=]\\s*[^\\s,;]+",
                "$1=***");
        return sanitized.length() <= 1_000
                ? sanitized : sanitized.substring(0, 1_000) + "...";
    }

    private String applySinglePageLowCode(McpToolContext mcpToolContext) {
        try {
            applyConfigViaMcp(
                    mcpToolContext,
                    PAGE_CONFIG_TYPE,
                    "index.json",
                    USER_EVENT_PAGE_CONFIG);
            int parentId = configParentMenuIdViaMcp(mcpToolContext);
            MenuVo policyMenu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "page-policy-menu",
                    "用户事件单页面配置",
                    MenuType.POLICY_CONFIG,
                    PAGE_CONFIG_TYPE,
                    MenuLevel.LEVEL_2,
                    parentId
            );
            MenuVo pageMenu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "page-low-code-menu",
                    "用户事件单页面应用",
                    MenuType.LOW_CODE_PAGE,
                    PAGE_CONFIG_TYPE,
                    MenuLevel.LEVEL_1,
                    0
            );
            return """
                    用户事件低代码单页面已通过 MCP 审批写入系统并完成读回。

                    %s

                    %s

                    %s
                    """.formatted(
                    visualizationConfigRecord("用户事件单页面配置已写入", PAGE_CONFIG_TYPE, "index.json", "LOW_CODE_PAGE", PAGE_CONFIG_TYPE),
                    menuRecord("配置管理菜单已创建", policyMenu),
                    menuRecord("低代码页面菜单已创建", pageMenu)
            );
        } catch (Exception e) {
            log.error("执行低代码单页面 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("低代码单页面", e);
        }
    }

    private String applySinglePageHtml(McpToolContext mcpToolContext) {
        try {
            applyConfigViaMcp(
                    mcpToolContext,
                    "html-page",
                    HTML_PAGE_FILE,
                    USER_EVENT_PAGE_HTML);
            MenuVo menu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "page-html-menu",
                    "用户事件 HTML 单页面",
                    MenuType.HTML_PAGE,
                    HTML_PAGE_PATH,
                    MenuLevel.LEVEL_1,
                    0
            );
            return """
                    用户事件静态 HTML 单页面已通过 MCP 审批写入系统并完成读回。

                    %s

                    %s
                    """.formatted(
                    visualizationConfigRecord("用户事件 HTML 单页面已写入", "html-page", HTML_PAGE_FILE, "HTML_PAGE", HTML_PAGE_PATH),
                    menuRecord("HTML 页面菜单已创建", menu)
            );
        } catch (Exception e) {
            log.error("执行 HTML 单页面 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("HTML 单页面", e);
        }
    }

    private String applySidebarApp(McpToolContext mcpToolContext) {
        try {
            applyConfigViaMcp(
                    mcpToolContext,
                    APP_CONFIG_TYPE,
                    "site.json",
                    USER_EVENT_APP_SITE_CONFIG);
            applyConfigViaMcp(
                    mcpToolContext,
                    APP_CONFIG_TYPE,
                    "index.json",
                    USER_EVENT_APP_HOME_CONFIG);
            applyConfigViaMcp(
                    mcpToolContext,
                    APP_CONFIG_TYPE,
                    "manage.json",
                    USER_EVENT_PAGE_CONFIG);
            applyConfigViaMcp(
                    mcpToolContext,
                    APP_CONFIG_TYPE,
                    "trend.json",
                    USER_EVENT_APP_TREND_CONFIG);
            int parentId = configParentMenuIdViaMcp(mcpToolContext);
            MenuVo policyMenu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "app-policy-menu",
                    "用户事件应用配置",
                    MenuType.POLICY_CONFIG,
                    APP_CONFIG_TYPE,
                    MenuLevel.LEVEL_2,
                    parentId
            );
            MenuVo appMenu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "app-low-code-menu",
                    "用户事件侧边栏应用",
                    MenuType.LOW_CODE_APP,
                    APP_CONFIG_TYPE,
                    MenuLevel.LEVEL_1,
                    0
            );
            return """
                    用户事件侧边栏低代码应用已通过 MCP 审批写入系统并完成读回。

                    %s

                    %s

                    %s
                    """.formatted(
                    visualizationConfigRecord("用户事件侧边栏应用配置已写入", APP_CONFIG_TYPE, "site.json", "LOW_CODE_APP", APP_CONFIG_TYPE),
                    menuRecord("配置管理菜单已创建", policyMenu),
                    menuRecord("低代码应用菜单已创建", appMenu)
            );
        } catch (Exception e) {
            log.error("执行侧边栏应用 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("带侧边栏应用", e);
        }
    }

    private String applyDashboardLowCode(McpToolContext mcpToolContext) {
        try {
            applyConfigViaMcp(
                    mcpToolContext,
                    DASHBOARD_CONFIG_TYPE,
                    "index.json",
                    USER_EVENT_DASHBOARD_CONFIG);
            int parentId = configParentMenuIdViaMcp(mcpToolContext);
            MenuVo policyMenu = createOrGetMenuViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "dashboard-policy-menu",
                    "用户事件看板配置",
                    MenuType.POLICY_CONFIG,
                    DASHBOARD_CONFIG_TYPE,
                    MenuLevel.LEVEL_2,
                    parentId
            );
            DashboardVo dashboard = createOrGetDashboardViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "dashboard-low-code",
                    "用户事件低代码看板",
                    "user-event-low-code-dashboard",
                    DashboardType.LOW_CODE_PAGE,
                    DASHBOARD_CONFIG_TYPE,
                    null,
                    null
            );
            return """
                    用户事件低代码看板已通过 MCP 审批写入系统并完成读回。

                    %s

                    %s

                    %s
                    """.formatted(
                    visualizationConfigRecord("用户事件看板配置已写入", DASHBOARD_CONFIG_TYPE, "index.json", "LOW_CODE_PAGE", DASHBOARD_CONFIG_TYPE),
                    menuRecord("看板配置管理菜单已创建", policyMenu),
                    dashboardRecord("低代码看板已创建", dashboard)
            );
        } catch (Exception e) {
            log.error("执行低代码看板 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("低代码数据看板", e);
        }
    }

    private String applyDashboardHtml(McpToolContext mcpToolContext) {
        try {
            applyConfigViaMcp(
                    mcpToolContext,
                    "html-page",
                    HTML_DASHBOARD_FILE,
                    USER_EVENT_DASHBOARD_HTML);
            DashboardVo dashboard = createOrGetDashboardViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "dashboard-html",
                    "用户事件 HTML 看板",
                    "user-event-html-dashboard",
                    DashboardType.HTML_PAGE,
                    null,
                    HTML_DASHBOARD_PATH,
                    null
            );
            return """
                    用户事件 HTML 看板已通过 MCP 审批写入系统并完成读回。

                    %s

                    %s
                    """.formatted(
                    visualizationConfigRecord("用户事件 HTML 看板页面已写入", "html-page", HTML_DASHBOARD_FILE, "HTML_PAGE", HTML_DASHBOARD_PATH),
                    dashboardRecord("HTML 看板已创建", dashboard)
            );
        } catch (Exception e) {
            log.error("执行 HTML 看板 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("HTML 数据看板", e);
        }
    }

    private String applyDashboardLink(String url,
                                      McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(url)) {
            return buildDashboardLinkInfoStepsResponse();
        }
        try {
            DashboardVo dashboard = createOrGetDashboardViaMcp(
                    mcpToolContext,
                    SOURCE_PREFIX + "dashboard-link",
                    "用户事件外链看板",
                    "user-event-link-dashboard",
                    DashboardType.LINK,
                    null,
                    null,
                    url
            );
            return """
                    用户事件外链看板已通过 MCP 审批创建并完成读回。

                    %s
                    """.formatted(dashboardRecord("外链看板已创建", dashboard));
        } catch (Exception e) {
            log.error("执行外链看板 MCP 演示失败: {}", e.getMessage(), e);
            return visualizationApplyFailureResponse("外链数据看板", e);
        }
    }

    private void applyConfigViaMcp(McpToolContext mcpToolContext,
                                   String type,
                                   String fileName,
                                   String content) throws Exception {
        String treeResult = callTool(
                mcpToolContext,
                "config_tree",
                Map.of("type", type));
        boolean exists = configTreeContainsFile(treeResult, fileName);
        if (exists) {
            String current = decodeStringResult(callTool(
                    mcpToolContext,
                    "config_read",
                    Map.of("type", type, "fileName", fileName)));
            if (contentEquivalent(content, current)) {
                return;
            }
        } else {
            requireTrueResult(
                    "config_ensure_root",
                    callTool(
                            mcpToolContext,
                            "config_ensure_root",
                            Map.of("type", type)));
            requireTrueResult(
                    "config_add",
                    callTool(
                            mcpToolContext,
                            "config_add",
                            Map.of(
                                    "type", type,
                                    "configDto", Map.of(
                                            "fileName", fileName))));
        }
        requireTrueResult(
                "config_apply",
                callTool(
                        mcpToolContext,
                        "config_apply",
                        Map.of(
                                "type", type,
                                "configDto", Map.of(
                                        "fileName", fileName,
                                        "text", content))));
        String readBack = decodeStringResult(callTool(
                mcpToolContext,
                "config_read",
                Map.of("type", type, "fileName", fileName)));
        if (!contentEquivalent(content, readBack)) {
            throw new IllegalStateException(
                    "config_read 读回与已确认配置不一致："
                            + type + "/" + fileName);
        }
    }

    private boolean configTreeContainsFile(String result,
                                           String fileName) throws Exception {
        Object tree = JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                Object.class);
        if (tree instanceof Map<?, ?> wrapper
                && wrapper.containsKey("data")) {
            tree = wrapper.get("data");
        }
        return containsConfigFile(tree, fileName);
    }

    private boolean containsConfigFile(Object value, String fileName) {
        if (value instanceof Map<?, ?> map) {
            if (fileName.equals(String.valueOf(map.get("fileName")))) {
                return true;
            }
            return map.values().stream()
                    .anyMatch(child -> containsConfigFile(child, fileName));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .anyMatch(child -> containsConfigFile(child, fileName));
        }
        return false;
    }

    private void requireTrueResult(String toolName,
                                   String result) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node =
                JacksonConfig.OBJECT_MAPPER.readTree(result);
        if (node != null && ((node.isBoolean() && node.booleanValue())
                || (node.isTextual()
                && Boolean.parseBoolean(node.textValue())))) {
            return;
        }
        throw new IllegalStateException(
                toolName + " 未成功：" + describeToolResult(result));
    }

    private String decodeStringResult(String result) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node =
                JacksonConfig.OBJECT_MAPPER.readTree(result);
        return node != null && node.isTextual()
                ? node.textValue() : result;
    }

    private boolean contentEquivalent(String expected,
                                      String actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(expected)
                    .equals(JacksonConfig.OBJECT_MAPPER.readTree(actual));
        } catch (Exception ignored) {
            return expected.replace("\r\n", "\n")
                    .equals(actual.replace("\r\n", "\n"));
        }
    }

    private int configParentMenuIdViaMcp(
            McpToolContext mcpToolContext) {
        List<Map<String, Object>> options = listOfMaps(toolResultObject(callTool(
                mcpToolContext,
                "menu_parent_options",
                Map.of())).get("options"));
        Optional<Map<String, Object>> configured = options.stream()
                .filter(option -> "配置管理".equals(
                        String.valueOf(option.get("label"))))
                .findFirst();
        Map<String, Object> selected = configured.orElseGet(
                () -> options.stream().findFirst().orElse(Map.of()));
        return (int) longValue(selected.get("value"));
    }

    private MenuVo createOrGetMenuViaMcp(
            McpToolContext mcpToolContext,
            String source,
            String name,
            MenuType type,
            String params,
            MenuLevel level,
            int parentId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("type", type.name());
        request.put("route", type.getRoute());
        request.put("params", params);
        request.put("createRootPath", false);
        request.put("parentId", parentId);
        request.put("level", level.name());
        request.put("source", source);

        List<Map<String, Object>> rows = listOfMaps(toolResultObject(callTool(
                mcpToolContext,
                "menu_list",
                Map.of("request", Map.of(
                        "page", 1,
                        "per_page", 100,
                        "name", name)))).get("rows"));
        Optional<Map<String, Object>> existing = flattenRows(rows).stream()
                .filter(item -> name.equals(String.valueOf(item.get("name")))
                        || source.equals(String.valueOf(item.get("source"))))
                .findFirst();
        long id;
        if (existing.isPresent()) {
            if (!menuRequestMatches(request, existing.get())) {
                throw new IllegalStateException(
                        "已存在同名或同 source 但内容不同的菜单，禁止覆盖");
            }
            id = longValue(existing.get().get("id"));
        } else {
            String createResult = callTool(
                    mcpToolContext,
                    "menu_create",
                    Map.of("request", request));
            Map<String, Object> created = toolResultObject(createResult);
            id = longValue(created.get("id"));
            if (id <= 0) {
                throw new IllegalStateException(
                        "menu_create 未返回有效菜单 ID："
                                + describeToolResult(createResult));
            }
        }
        if (id <= 0) {
            throw new IllegalStateException(
                    "menu_list 返回的已有菜单缺少有效 ID");
        }
        Map<String, Object> readBack = toolResultObject(callTool(
                mcpToolContext,
                "menu_view",
                Map.of("id", id)));
        if (!menuRequestMatches(request, readBack)) {
            throw new IllegalStateException(
                    "menu_view 读回与演示配置不一致："
                            + JacksonUtil.toJson(readBack));
        }
        return JacksonConfig.OBJECT_MAPPER.convertValue(
                readBack,
                MenuVo.class);
    }

    private List<Map<String, Object>> flattenRows(
            List<Map<String, Object>> rows) {
        List<Map<String, Object>> flattened = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            flattened.add(row);
            flattened.addAll(flattenRows(
                    listOfMaps(row.get("children"))));
        }
        return flattened;
    }

    private DashboardVo createOrGetDashboardViaMcp(
            McpToolContext mcpToolContext,
            String source,
            String name,
            String code,
            DashboardType type,
            String configIndex,
            String htmlPath,
            String url) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("code", code);
        request.put("type", type.name());
        request.put("url", url);
        request.put("configIndex", configIndex);
        request.put("htmlPath", htmlPath);
        request.put("isDefault", false);
        request.put("source", source);

        List<Map<String, Object>> rows = listOfMaps(toolResultObject(callTool(
                mcpToolContext,
                "dashboard_list",
                Map.of("request", Map.of(
                        "page", 1,
                        "per_page", 100,
                        "name", name)))).get("rows"));
        Optional<Map<String, Object>> existing = rows.stream()
                .filter(item -> name.equals(String.valueOf(item.get("name")))
                        || code.equals(String.valueOf(item.get("code")))
                        || source.equals(String.valueOf(item.get("source"))))
                .findFirst();
        long id;
        if (existing.isPresent()) {
            if (!dashboardRequestMatches(request, existing.get())) {
                throw new IllegalStateException(
                        "已存在同名、同 code 或同 source 但内容不同的看板，"
                                + "禁止覆盖");
            }
            id = longValue(existing.get().get("id"));
        } else {
            String createResult = callTool(
                    mcpToolContext,
                    "dashboard_create",
                    Map.of("request", request));
            Map<String, Object> created = toolResultObject(createResult);
            id = longValue(created.get("id"));
            if (id <= 0) {
                throw new IllegalStateException(
                        "dashboard_create 未返回有效看板 ID："
                                + describeToolResult(createResult));
            }
        }
        if (id <= 0) {
            throw new IllegalStateException(
                    "dashboard_list 返回的已有看板缺少有效 ID");
        }
        Map<String, Object> readBack = toolResultObject(callTool(
                mcpToolContext,
                "dashboard_view",
                Map.of("id", id)));
        if (!dashboardRequestMatches(request, readBack)) {
            throw new IllegalStateException(
                    "dashboard_view 读回与演示配置不一致："
                            + JacksonUtil.toJson(readBack));
        }
        return JacksonConfig.OBJECT_MAPPER.convertValue(
                readBack,
                DashboardVo.class);
    }

    private boolean dashboardRequestMatches(
            Map<String, Object> request,
            Map<String, Object> actual) {
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        return valuesEqual(request.get("name"), actual.get("name"))
                && valuesEqual(request.get("code"), actual.get("code"))
                && valuesEqual(request.get("type"), actual.get("type"))
                && valuesEqual(request.get("url"), actual.get("url"))
                && valuesEqual(
                request.get("configIndex"),
                actual.get("configIndex"))
                && valuesEqual(
                request.get("htmlPath"),
                actual.get("htmlPath"))
                && valuesEqual(
                request.get("isDefault"),
                actual.get("isDefault"))
                && valuesEqual(
                request.get("source"),
                actual.get("source"));
    }

    private boolean valuesEqual(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    private String visualizationApplyFailureResponse(
            String stage,
            Exception exception) {
        return """
                ```zenvis:notice
                {"title":"执行数据可视化演示工作流失败","content":"失败阶段：%s\\n真实错误：%s\\n未生成配置、菜单或看板成功记录；审批被拒绝或读回不一致时不会伪造成功。","level":"error"}
                ```
                """.formatted(
                escapeJson(stage),
                escapeJson(safeError(exception)));
    }

    private String visualizationConfigRecord(String title, String configType, String fileName, String type, String configIndex) {
        return """
                ```zenvis:visualization-config-record
                {
                  "id": "%s:%s",
                  "title": "%s",
                  "name": "%s",
                  "configType": "%s",
                  "fileName": "%s",
                  "type": "%s",
                  "configIndex": "%s",
                  "entity": "%s",
                  "status": "applied"
                }
                ```
                """.formatted(
                escapeJson(configType),
                escapeJson(fileName),
                escapeJson(title),
                escapeJson(title),
                escapeJson(configType),
                escapeJson(fileName),
                escapeJson(type),
                escapeJson(configIndex),
                ENTITY
        );
    }

    private String menuRecord(String title, MenuVo menu) {
        return """
                ```zenvis:menu-config-record
                {
                  "id": "menu:%s",
                  "title": "%s",
                  "name": "%s",
                  "menuId": "%s",
                  "type": "%s",
                  "route": "%s",
                  "params": "%s",
                  "parentId": "%s",
                  "source": "%s",
                  "status": "created"
                }
                ```
                """.formatted(
                menu.getId(),
                escapeJson(title),
                escapeJson(menu.getName()),
                menu.getId(),
                menu.getType() == null ? "" : menu.getType().name(),
                escapeJson(menu.getRoute()),
                escapeJson(menu.getParams()),
                menu.getParentId(),
                escapeJson(menu.getSource())
        );
    }

    private String dashboardRecord(String title, DashboardVo dashboard) {
        return """
                ```zenvis:dashboard-config-record
                {
                  "id": "dashboard:%s",
                  "title": "%s",
                  "name": "%s",
                  "dashboardId": "%s",
                  "code": "%s",
                  "type": "%s",
                  "configIndex": "%s",
                  "htmlPath": "%s",
                  "url": "%s",
                  "source": "%s",
                  "status": "created"
                }
                ```
                """.formatted(
                dashboard.getId(),
                escapeJson(title),
                escapeJson(dashboard.getName()),
                dashboard.getId(),
                escapeJson(dashboard.getCode()),
                dashboard.getType() == null ? "" : dashboard.getType().name(),
                escapeJson(dashboard.getConfigIndex()),
                escapeJson(dashboard.getHtmlPath()),
                escapeJson(dashboard.getUrl()),
                escapeJson(dashboard.getSource())
        );
    }

    private String extractUrl(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String allMessagesText(ChatSession chatSession) {
        if (chatSession == null || !StringUtils.hasText(chatSession.getMessages())) {
            return "";
        }
        try {
            List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
            });
            StringBuilder builder = new StringBuilder();
            for (Message message : messages) {
                if (StringUtils.hasText(message.getContent())) {
                    builder.append(message.getContent()).append('\n');
                }
            }
            return builder.toString();
        } catch (Exception e) {
            log.warn("读取数据可视化演示会话失败: {}", e.getMessage(), e);
            return "";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
