package com.coolxer.service.dih;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.model.policy.dto.ConfigDto;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DataVisualizationDemoResponseService {

    public static final String USER_EVENT_VISUALIZATION_DEMO_TITLE = "用户事件数据可视化演示";

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
    private final MenuService menuService;
    private final DashboardService dashboardService;

    public DataVisualizationDemoResponseService(ConfigService configService,
                                                MenuService menuService,
                                                DashboardService dashboardService) {
        this.configService = configService;
        this.menuService = menuService;
        this.dashboardService = dashboardService;
    }

    public static boolean isUserEventVisualizationDemoPrompt(String prompt) {
        return isChartRequirement(prompt)
                || isSinglePageRequirement(prompt)
                || isSidebarAppRequirement(prompt)
                || isDashboardRequirement(prompt);
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
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
            return Optional.of(streamResponse(reviseLatestVisualizationConfig(chatSession)));
        }
        if (isApplyVisualizationConfigPrompt(prompt)) {
            return Optional.of(streamResponse(applyLatestVisualizationConfig(chatSession)));
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
        return Optional.empty();
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
                && (prompt.contains("# 用户事件数据可视化：临时图表")
                || (prompt.contains("用户事件") && prompt.contains("临时") && prompt.contains("图表")));
    }

    private static boolean isSinglePageRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (prompt.contains("# 用户事件数据可视化：单页面应用")
                || (prompt.contains("用户事件") && prompt.contains("单页面") && prompt.contains("应用")));
    }

    private static boolean isSidebarAppRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (prompt.contains("# 用户事件数据可视化：带侧边栏应用")
                || (prompt.contains("用户事件") && prompt.contains("侧边栏") && prompt.contains("应用")));
    }

    private static boolean isDashboardRequirement(String prompt) {
        return StringUtils.hasText(prompt)
                && (prompt.contains("# 用户事件数据可视化：数据看板")
                || (prompt.contains("用户事件") && prompt.contains("看板")));
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
                  "config": %s
                }
                ```
                """.formatted(ENTITY, CHART_AMIS_CONFIG.trim());
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
                    {"title":"是否写入用户事件 HTML 单页面","content":"确认后会写入 html-page_config/user-event-page.html，并创建 HTML 页面类型菜单。","action":"%s","actions":%s,"demoScenario":"single_page","implementation":"html"}
                    ```
                    """.formatted(USER_EVENT_PAGE_HTML.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
        }
        return """
                已生成用户事件低代码单页面配置，请确认后写入系统。

                ```zenvis:low-code-page-config
                %s
                ```

                ```zenvis:confirm
                {"title":"是否写入用户事件低代码单页面","content":"确认后会创建 user-event-page_config/index.json，并创建配置管理菜单和低代码页面菜单。","action":"%s","actions":%s,"demoScenario":"single_page","implementation":"low_code"}
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
                {"title":"是否写入用户事件侧边栏应用","content":"确认后会创建 user-event-app_config/site.json 及子页面配置，并创建配置管理菜单和低代码应用菜单。","action":"%s","actions":%s,"demoScenario":"sidebar_app","implementation":"low_code_app"}
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
                    {"title":"是否创建用户事件外链看板","content":"确认后会创建 LINK 类型看板，外链地址：%s","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"link","url":"%s"}
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
                    {"title":"是否写入用户事件 HTML 看板","content":"确认后会写入 html-page_config/user-event-dashboard.html，并创建 HTML_PAGE 类型看板。","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"html"}
                    ```
                    """.formatted(USER_EVENT_DASHBOARD_HTML.trim(), ACTION_APPLY_CONFIG, DECISION_ACTIONS);
        }
        return """
                已生成用户事件低代码看板配置，请确认后写入系统并创建看板。

                ```zenvis:low-code-page-config
                %s
                ```

                ```zenvis:confirm
                {"title":"是否写入用户事件低代码看板","content":"确认后会创建 user-event-dashboard_config/index.json、配置管理菜单和 LOW_CODE_PAGE 类型看板。","action":"%s","actions":%s,"demoScenario":"dashboard","dashboardType":"low_code"}
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

    private String applyLatestVisualizationConfig(ChatSession chatSession) {
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
        if (singlePageIndex >= sidebarIndex && singlePageIndex >= dashboardIndex && singlePageIndex >= 0) {
            String scope = history.substring(singlePageIndex);
            return scope.contains("\"implementation\":\"html\"") || scope.contains("\"implementation\": \"html\"")
                    ? applySinglePageHtml()
                    : applySinglePageLowCode();
        }
        if (sidebarIndex >= singlePageIndex && sidebarIndex >= dashboardIndex && sidebarIndex >= 0) {
            return applySidebarApp();
        }
        if (dashboardIndex >= 0) {
            String scope = history.substring(dashboardIndex);
            if (scope.contains("\"dashboardType\":\"link\"") || scope.contains("\"dashboardType\": \"link\"")) {
                return applyDashboardLink(extractUrl(scope));
            }
            if (scope.contains("\"dashboardType\":\"html\"") || scope.contains("\"dashboardType\": \"html\"")) {
                return applyDashboardHtml();
            }
            return applyDashboardLowCode();
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

    private String reviseLatestVisualizationConfig(ChatSession chatSession) {
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
        if (singlePageIndex >= sidebarIndex && singlePageIndex >= dashboardIndex && singlePageIndex >= 0) {
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
        if (sidebarIndex >= singlePageIndex && sidebarIndex >= dashboardIndex && sidebarIndex >= 0) {
            return """
                    已根据补充信息更新用户事件侧边栏应用配置，请再次确认后续处理。

                    %s
                    """.formatted(buildSidebarAppConfigResponse().trim());
        }
        if (dashboardIndex >= 0) {
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

    private String applySinglePageLowCode() {
        writeConfig(PAGE_CONFIG_TYPE, "index.json", USER_EVENT_PAGE_CONFIG);
        MenuVo policyMenu = createOrGetMenu(
                SOURCE_PREFIX + "page-policy-menu",
                "用户事件单页面配置",
                MenuType.POLICY_CONFIG,
                PAGE_CONFIG_TYPE
        );
        MenuVo pageMenu = createOrGetMenu(
                SOURCE_PREFIX + "page-low-code-menu",
                "用户事件单页面应用",
                MenuType.LOW_CODE_PAGE,
                PAGE_CONFIG_TYPE
        );
        return """
                用户事件低代码单页面已写入系统。

                %s

                %s

                %s
                """.formatted(
                visualizationConfigRecord("用户事件单页面配置已写入", PAGE_CONFIG_TYPE, "index.json", "LOW_CODE_PAGE", PAGE_CONFIG_TYPE),
                menuRecord("配置管理菜单已创建", policyMenu),
                menuRecord("低代码页面菜单已创建", pageMenu)
        );
    }

    private String applySinglePageHtml() {
        writeConfig("html-page", HTML_PAGE_FILE, USER_EVENT_PAGE_HTML);
        MenuVo menu = createOrGetMenu(
                SOURCE_PREFIX + "page-html-menu",
                "用户事件 HTML 单页面",
                MenuType.HTML_PAGE,
                HTML_PAGE_PATH
        );
        return """
                用户事件静态 HTML 单页面已写入系统。

                %s

                %s
                """.formatted(
                visualizationConfigRecord("用户事件 HTML 单页面已写入", "html-page", HTML_PAGE_FILE, "HTML_PAGE", HTML_PAGE_PATH),
                menuRecord("HTML 页面菜单已创建", menu)
        );
    }

    private String applySidebarApp() {
        writeConfig(APP_CONFIG_TYPE, "site.json", USER_EVENT_APP_SITE_CONFIG);
        writeConfig(APP_CONFIG_TYPE, "index.json", USER_EVENT_APP_HOME_CONFIG);
        writeConfig(APP_CONFIG_TYPE, "manage.json", USER_EVENT_PAGE_CONFIG);
        writeConfig(APP_CONFIG_TYPE, "trend.json", USER_EVENT_APP_TREND_CONFIG);
        MenuVo policyMenu = createOrGetMenu(
                SOURCE_PREFIX + "app-policy-menu",
                "用户事件应用配置",
                MenuType.POLICY_CONFIG,
                APP_CONFIG_TYPE
        );
        MenuVo appMenu = createOrGetMenu(
                SOURCE_PREFIX + "app-low-code-menu",
                "用户事件侧边栏应用",
                MenuType.LOW_CODE_APP,
                APP_CONFIG_TYPE
        );
        return """
                用户事件侧边栏低代码应用已写入系统。

                %s

                %s

                %s
                """.formatted(
                visualizationConfigRecord("用户事件侧边栏应用配置已写入", APP_CONFIG_TYPE, "site.json", "LOW_CODE_APP", APP_CONFIG_TYPE),
                menuRecord("配置管理菜单已创建", policyMenu),
                menuRecord("低代码应用菜单已创建", appMenu)
        );
    }

    private String applyDashboardLowCode() {
        writeConfig(DASHBOARD_CONFIG_TYPE, "index.json", USER_EVENT_DASHBOARD_CONFIG);
        MenuVo policyMenu = createOrGetMenu(
                SOURCE_PREFIX + "dashboard-policy-menu",
                "用户事件看板配置",
                MenuType.POLICY_CONFIG,
                DASHBOARD_CONFIG_TYPE
        );
        DashboardVo dashboard = createOrGetDashboard(
                SOURCE_PREFIX + "dashboard-low-code",
                "用户事件低代码看板",
                "user-event-low-code-dashboard",
                DashboardType.LOW_CODE_PAGE,
                DASHBOARD_CONFIG_TYPE,
                null,
                null
        );
        return """
                用户事件低代码看板已写入系统。

                %s

                %s

                %s
                """.formatted(
                visualizationConfigRecord("用户事件看板配置已写入", DASHBOARD_CONFIG_TYPE, "index.json", "LOW_CODE_PAGE", DASHBOARD_CONFIG_TYPE),
                menuRecord("看板配置管理菜单已创建", policyMenu),
                dashboardRecord("低代码看板已创建", dashboard)
        );
    }

    private String applyDashboardHtml() {
        writeConfig("html-page", HTML_DASHBOARD_FILE, USER_EVENT_DASHBOARD_HTML);
        DashboardVo dashboard = createOrGetDashboard(
                SOURCE_PREFIX + "dashboard-html",
                "用户事件 HTML 看板",
                "user-event-html-dashboard",
                DashboardType.HTML_PAGE,
                null,
                HTML_DASHBOARD_PATH,
                null
        );
        return """
                用户事件 HTML 看板已写入系统。

                %s

                %s
                """.formatted(
                visualizationConfigRecord("用户事件 HTML 看板页面已写入", "html-page", HTML_DASHBOARD_FILE, "HTML_PAGE", HTML_DASHBOARD_PATH),
                dashboardRecord("HTML 看板已创建", dashboard)
        );
    }

    private String applyDashboardLink(String url) {
        if (!StringUtils.hasText(url)) {
            return buildDashboardLinkInfoStepsResponse();
        }
        DashboardVo dashboard = createOrGetDashboard(
                SOURCE_PREFIX + "dashboard-link",
                "用户事件外链看板",
                "user-event-link-dashboard",
                DashboardType.LINK,
                null,
                null,
                url
        );
        return """
                用户事件外链看板已创建。

                %s
                """.formatted(dashboardRecord("外链看板已创建", dashboard));
    }

    private void writeConfig(String type, String fileName, String text) {
        try {
            configService.ensureRootPath(type);
            if (!configService.fileExistsInConfigPath(type, fileName)) {
                configService.addFile(type, fileName);
            }
            ConfigDto configDto = new ConfigDto();
            configDto.setFileName(fileName);
            configDto.setText(text);
            configService.modifyConfig(type, configDto);
        } catch (Exception e) {
            log.error("写入数据可视化演示配置失败 type={}, fileName={}: {}", type, fileName, e.getMessage(), e);
            throw e;
        }
    }

    private MenuVo createOrGetMenu(String source, String name, MenuType type, String params) {
        MenuDto dto = buildMenuDto(source, name, type, params);
        try {
            List<Menu> existing = menuService.findBySource(source);
            if (existing != null && !existing.isEmpty()) {
                Menu menu = existing.get(0);
                if (menuNeedsSync(menu, dto)) {
                    boolean updated = menuService.update(menu.getId().longValue(), dto);
                    if (updated) {
                        MenuVo latest = menuService.info(menu.getId().longValue());
                        if (latest != null) {
                            return latest;
                        }
                    }
                }
                return new MenuVo(menu);
            }
        } catch (Exception e) {
            log.warn("查询演示菜单失败 source={}: {}", source, e.getMessage(), e);
        }
        return new MenuVo(menuService.create(dto));
    }

    private MenuDto buildMenuDto(String source, String name, MenuType type, String params) {
        MenuDto dto = new MenuDto();
        dto.setName(name);
        dto.setType(type);
        dto.setParams(params);
        dto.setSource(source);
        if (type == MenuType.POLICY_CONFIG) {
            dto.setLevel(MenuLevel.LEVEL_2);
            dto.setParentId(findParentMenuIdByName("配置管理"));
        } else {
            dto.setLevel(MenuLevel.LEVEL_1);
            dto.setParentId(0);
        }
        dto.setCreateRootPath(false);
        return dto;
    }

    private boolean menuNeedsSync(Menu menu, MenuDto dto) {
        return !dto.getName().equals(menu.getName())
                || dto.getType() != menu.getType()
                || !dto.getParams().equals(menu.getParams())
                || !dto.getParentId().equals(menu.getParentId())
                || dto.getLevel() != menu.getLevel();
    }

    private int findParentMenuIdByName(String name) {
        try {
            return menuService.findAllParentMenu().stream()
                    .filter(menu -> name.equals(menu.getName()))
                    .map(Menu::getId)
                    .filter(id -> id != null)
                    .findFirst()
                    .orElseGet(() -> menuService.findAllParentMenu().stream()
                            .min(Comparator.comparing(Menu::getOrderNumber, Comparator.nullsLast(Integer::compareTo)))
                            .map(Menu::getId)
                            .orElse(0));
        } catch (Exception e) {
            return 0;
        }
    }

    private DashboardVo createOrGetDashboard(String source,
                                             String name,
                                             String code,
                                             DashboardType type,
                                             String configIndex,
                                             String htmlPath,
                                             String url) {
        try {
            List<DashboardVo> existing = dashboardService.findAll();
            if (existing != null) {
                Optional<DashboardVo> matched = existing.stream()
                        .filter(item -> source.equals(item.getSource()) || code.equals(item.getCode()))
                        .findFirst();
                if (matched.isPresent()) {
                    return matched.get();
                }
            }
        } catch (Exception e) {
            log.warn("查询演示看板失败 source={}: {}", source, e.getMessage(), e);
        }
        DashboardDto dto = new DashboardDto();
        dto.setName(name);
        dto.setCode(code);
        dto.setType(type);
        dto.setConfigIndex(configIndex);
        dto.setHtmlPath(htmlPath);
        dto.setUrl(url);
        dto.setSource(source);
        Dashboard dashboard = dashboardService.create(dto);
        return new DashboardVo(dashboard);
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
