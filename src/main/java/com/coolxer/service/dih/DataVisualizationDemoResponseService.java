package com.coolxer.service.dih;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatMessagePart;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

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
            "请添加一个用户事件数据应用菜单。";

    private static final String ENTITY = "user_event";
    private static final String ENTITY_LABEL = "用户事件数据";
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
    private static final String MENU_DEMO_NAME = "用户事件数据入口";
    private static final String MENU_DEMO_SOURCE = SOURCE_PREFIX + "menu-single-page-entry";
    private static final Set<String> REQUIRED_ATTRIBUTES = Set.of(
            "event_id", "procid", "user", "event_type",
            "reliability", "detail", "tags", "server_time");
    private static final String DECISION_ACTIONS = "[\"apply_config\",\"abandon\",\"revise\"]";
    private static final int DEMO_STREAM_CHUNK_SIZE = 512;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(5);

    private static final String TEMPLATE_ROOT = "demo/data-visualization/";
    private static final String CHART_AMIS_CONFIG = loadTemplate("chart-amis.json");
    private static final String USER_EVENT_PAGE_CONFIG = loadTemplate("user-event-page.json");
    private static final String USER_EVENT_APP_SITE_CONFIG = loadTemplate("user-event-app-site.json");
    private static final String USER_EVENT_APP_HOME_CONFIG = loadTemplate("user-event-app-home.json");
    private static final String USER_EVENT_APP_TREND_CONFIG = loadTemplate("user-event-app-trend.json");
    private static final String USER_EVENT_DASHBOARD_CONFIG = loadTemplate("user-event-dashboard.json");
    private static final String USER_EVENT_PAGE_HTML = loadTemplate("user-event-page.html");
    private static final String USER_EVENT_DASHBOARD_HTML = loadTemplate("user-event-dashboard.html");

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
            return Optional.of(streamResponse(addChartLibraryResponse(chatSession)));
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
            return Optional.of(streamAction(
                    () -> buildChartPreviewResponse(prompt, mcpToolContext)));
        }
        if (isSinglePageInfoSubmitted(prompt)) {
            return Optional.of(streamAction(() -> buildValidatedArtifact(
                    "单页面应用配置生成",
                    mcpToolContext,
                    () -> buildSinglePageConfigResponse(prompt))));
        }
        if (isSidebarAppInfoSubmitted(prompt)) {
            return Optional.of(streamAction(() -> buildValidatedArtifact(
                    "侧边栏应用配置生成",
                    mcpToolContext,
                    this::buildSidebarAppConfigResponse)));
        }
        if (isDashboardInfoSubmitted(prompt)) {
            return Optional.of(streamAction(() -> buildValidatedArtifact(
                    "数据看板配置生成",
                    mcpToolContext,
                    () -> buildDashboardConfigResponse(prompt))));
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
                    () -> buildMenuConfirmationResponse(chatSession, mcpToolContext)));
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
                {"title":"元数据配置提醒","content":"该演示基于 user_event 用户事件数据实体。如果当前环境尚未生成用户事件元数据，请先通过数据接入智能体的用户事件数据接入示例添加元数据配置。","level":"warning"}
                ```

                """ + response;
    }

    private boolean metadataAvailable() {
        try {
            return configService.fileExistsInConfigPath("meta", "user_event.json");
        } catch (Exception e) {
            return false;
        }
    }

    private String buildValidatedArtifact(String stage,
                                          McpToolContext mcpToolContext,
                                          Supplier<String> artifactBuilder) {
        try {
            validateUserEventMeta(mcpToolContext);
            validateUserEventRead(mcpToolContext);
            return artifactBuilder.get();
        } catch (Exception e) {
            log.error("{}失败: {}", stage, e.getMessage(), e);
            return visualizationDataFailureResponse(stage, e);
        }
    }

    private void validateUserEventMeta(McpToolContext mcpToolContext) {
        Map<String, Object> entityResult = toolResultObject(callTool(
                mcpToolContext,
                "retrieval_list_display_entity",
                Map.of()));
        List<Map<String, Object>> entities = listOfMaps(
                firstValue(entityResult, "entityList", "entity_list"));
        boolean entityExists = entities.stream().anyMatch(entity ->
                ENTITY.equals(String.valueOf(entity.get("name"))));
        if (!entityExists) {
            throw new IllegalStateException(
                    "Meta 中不存在 user_event 实体，请先运行数据接入智能体的用户事件数据示例");
        }

        Map<String, Object> attributeResult = toolResultObject(callTool(
                mcpToolContext,
                "retrieval_list_display_attribute",
                Map.of("entity", ENTITY)));
        List<Map<String, Object>> attributes = listOfMaps(
                firstValue(attributeResult, "attributeList", "attribute_list"));
        Set<String> actualNames = attributes.stream()
                .map(attribute -> String.valueOf(attribute.get("name")))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new java.util.LinkedHashSet<>(REQUIRED_ATTRIBUTES);
        missing.removeAll(actualNames);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "user_event Meta 缺少演示所需字段：" + String.join("、", missing));
        }
    }

    private void validateUserEventRead(McpToolContext mcpToolContext) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entities", List.of(ENTITY));
        request.put("time_range", Map.of("preset", "TODAY"));
        request.put("time_field", "server_time");
        request.put("comparison", "PREVIOUS_PERIOD");
        request.put("criteria_list", List.of());
        request.put("criteria_logic", "and");
        Map<String, Object> response = toolResultObject(callTool(
                mcpToolContext,
                "entity_overview",
                Map.of("request", request)));
        if (!(response.get("result") instanceof Map<?, ?>)) {
            throw new IllegalStateException(
                    "entity_overview 未返回真实查询结果");
        }
    }

    private Map<String, Object> chartQueryRequest(String prompt) {
        String selected = selectedAnswerText(prompt);
        String source = StringUtils.hasText(selected)
                ? selected + "\n" + prompt : prompt;
        String preset;
        String granularity;
        if (source.contains("近 7 天") || source.contains("近7天")) {
            preset = "LAST_7_DAYS";
            granularity = "DAY";
        } else if (source.contains("今天")) {
            preset = "TODAY";
            granularity = "HOUR";
        } else {
            preset = "LAST_24_HOURS";
            granularity = "HOUR";
        }
        if (source.contains("按天")) {
            granularity = "DAY";
        } else if (source.contains("按小时")) {
            granularity = "HOUR";
        }
        boolean grouped = !source.contains("总上报量趋势")
                && !source.contains("仅展示总上报量");
        String chartHint = source.contains("柱状图") ? "BAR" : "LINE";

        List<Map<String, Object>> dimensions = new ArrayList<>();
        Map<String, Object> timeDimension = new LinkedHashMap<>();
        timeDimension.put("name", "event_time");
        timeDimension.put("field", "server_time");
        timeDimension.put("label", "上报时间");
        timeDimension.put("kind", "TIME");
        timeDimension.put("granularity", granularity);
        timeDimension.put("include_null", false);
        dimensions.add(timeDimension);
        if (grouped) {
            Map<String, Object> eventTypeDimension = new LinkedHashMap<>();
            eventTypeDimension.put("name", "event_type");
            eventTypeDimension.put("field", "event_type");
            eventTypeDimension.put("label", "事件类型");
            eventTypeDimension.put("kind", "FIELD");
            eventTypeDimension.put("include_null", false);
            dimensions.add(eventTypeDimension);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", ENTITY);
        request.put("dimensions", dimensions);
        request.put("metrics", List.of(Map.of(
                "name", "event_count",
                "operation", "COUNT",
                "label", "事件数")));
        request.put("time_range", Map.of("preset", preset));
        request.put("time_field", "server_time");
        request.put("criteria_list", List.of());
        request.put("criteria_logic", "and");
        request.put("order_by", Map.of(
                "field", "event_time",
                "direction", "asc"));
        request.put("limit", 100);
        request.put("chart_hint", chartHint);
        return request;
    }

    private Map<String, Object> chartAmisConfig(
            Map<String, Object> queryRequest) {
        String rendered = CHART_AMIS_CONFIG.replace(
                "\"__QUERY_REQUEST__\"",
                JacksonUtil.toJson(queryRequest));
        return JacksonUtil.toMap(
                rendered,
                new TypeReference<Map<String, Object>>() {
                });
    }

    private Optional<Map<String, Object>> latestChartPreview(
            ChatSession chatSession) {
        if (chatSession == null
                || !StringUtils.hasText(chatSession.getMessages())) {
            return Optional.empty();
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    chatSession.getMessages(),
                    new TypeReference<List<Message>>() {
                    });
            for (int messageIndex = messages.size() - 1;
                 messageIndex >= 0; messageIndex--) {
                Message message = messages.get(messageIndex);
                List<ChatMessagePart> parts = message.getParts();
                if (parts == null || parts.isEmpty()) {
                    parts = new ChatMessagePartParser().parse(
                            message.getContent(), MessageType.TEXT);
                }
                for (int partIndex = parts.size() - 1;
                     partIndex >= 0; partIndex--) {
                    ChatMessagePart part = parts.get(partIndex);
                    Map<String, Object> metadata = part.getMetadata();
                    Object planId = metadata == null
                            ? null : metadata.get("planId");
                    if (!"visualization-chart-preview".equals(part.getType())
                            || metadata == null
                            || !"success".equals(
                            String.valueOf(metadata.get("validationStatus")))
                            || !(planId instanceof String planIdText)
                            || !StringUtils.hasText(planIdText)
                            || mapOf(metadata.get("echartsOption")).isEmpty()) {
                        continue;
                    }
                    return Optional.of(new LinkedHashMap<>(metadata));
                }
            }
        } catch (Exception e) {
            log.warn("读取上一轮用户事件临时图表失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    private MenuTarget resolveMenuTarget(ChatSession chatSession,
                                         McpToolContext mcpToolContext)
            throws Exception {
        String lowCodeTree = callTool(
                mcpToolContext,
                "config_tree",
                Map.of("type", PAGE_CONFIG_TYPE));
        String htmlTree = callTool(
                mcpToolContext,
                "config_tree",
                Map.of("type", "html-page"));
        boolean lowCodeExists =
                configTreeContainsFile(lowCodeTree, "index.json");
        boolean htmlExists =
                configTreeContainsFile(htmlTree, HTML_PAGE_FILE);
        boolean preferHtml = prefersHtmlSinglePage(chatSession);
        MenuTarget target;
        if (preferHtml && htmlExists) {
            target = new MenuTarget(
                    MenuType.HTML_PAGE,
                    HTML_PAGE_PATH,
                    "html-page",
                    HTML_PAGE_FILE);
        } else if (lowCodeExists) {
            target = new MenuTarget(
                    MenuType.LOW_CODE_PAGE,
                    PAGE_CONFIG_TYPE,
                    PAGE_CONFIG_TYPE,
                    "index.json");
        } else if (htmlExists) {
            target = new MenuTarget(
                    MenuType.HTML_PAGE,
                    HTML_PAGE_PATH,
                    "html-page",
                    HTML_PAGE_FILE);
        } else {
            throw new IllegalStateException(
                    "尚未找到已应用的用户事件单页面，请先运行并应用“单页面应用”演示");
        }
        String content = decodeStringResult(callTool(
                mcpToolContext,
                "config_read",
                Map.of(
                        "type", target.configType(),
                        "fileName", target.fileName())));
        if (!StringUtils.hasText(content)
                || !content.contains("user_event")
                || content.contains("/entity/user-event/")) {
            throw new IllegalStateException(
                    "目标单页面未使用 user_event 真实数据接口，请先重新应用最新单页面演示");
        }
        return target;
    }

    private boolean prefersHtmlSinglePage(ChatSession chatSession) {
        String history = allMessagesText(chatSession);
        int scenarioIndex =
                history.lastIndexOf("\"demoScenario\":\"single_page\"");
        if (scenarioIndex < 0) {
            scenarioIndex = history.lastIndexOf(
                    "\"demoScenario\": \"single_page\"");
        }
        if (scenarioIndex < 0) {
            return false;
        }
        String scope = history.substring(scenarioIndex);
        return scope.contains("\"implementation\":\"html\"")
                || scope.contains("\"implementation\": \"html\"");
    }

    private String visualizationDataFailureResponse(String stage,
                                                    Exception exception) {
        return """
                ```zenvis:notice
                {"title":"用户事件真实数据演示已阻止","content":"失败阶段：%s\\n真实错误：%s\\n未使用示例数据，也未生成可应用的成功结果。请先通过数据接入智能体创建 user_event Meta 并启动数据推送服务。","level":"error"}
                ```
                """.formatted(
                escapeJson(stage),
                escapeJson(safeError(exception)));
    }

    private static String loadTemplate(String fileName) {
        String path = TEMPLATE_ROOT + fileName;
        try (InputStream stream = DataVisualizationDemoResponseService.class
                .getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("演示模板不存在：" + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
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
                  "content": "请选择低代码或静态 HTML 实现方式，两种方式都会实时读取 user_event 数据。",
                  "submitLabel": "生成看板配置",
                  "steps": [
                    {
                      "id": "implementation",
                      "title": "实现方式",
                      "required": true,
                      "description": "低代码和静态 HTML 均生成系统内配置，不使用外链或演示数据。",
                      "suggestions": [
                        {"label": "低代码看板", "value": "使用低代码 amis 页面实现数据看板"},
                        {"label": "静态 HTML 看板", "value": "使用静态 HTML 页面实现数据看板"}
                      ],
                      "placeholder": "例如：低代码看板"
                    },
                    {
                      "id": "metrics",
                      "title": "看板指标",
                      "required": false,
                      "description": "确认看板展示指标。",
                      "suggestions": [
                        {"label": "完整运营概览", "value": "展示累计和今日事件、活跃用户、平均可信度、最新事件、类型分布、趋势、可信度直方图和最新记录"},
                        {"label": "趋势优先", "value": "重点展示近 24 小时分类型上报趋势"},
                        {"label": "分布优先", "value": "重点展示事件类型分布和可信度区间"}
                      ],
                      "placeholder": "也可以补充指标名称和布局要求"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildMenuConfirmationResponse(ChatSession chatSession,
                                                 McpToolContext mcpToolContext) {
        try {
            validateUserEventMeta(mcpToolContext);
            MenuTarget menuTarget = resolveMenuTarget(chatSession, mcpToolContext);
            Map<String, Object> typeResult = toolResultObject(callTool(
                    mcpToolContext,
                    "menu_type_options",
                    Map.of()));
            List<Map<String, Object>> typeOptions = listOfMaps(typeResult.get("options"));
            boolean targetTypeAvailable = typeOptions.stream().anyMatch(option ->
                    menuTarget.type().name().equals(
                            String.valueOf(option.get("value"))));
            if (!targetTypeAvailable) {
                throw new IllegalStateException(
                        "menu_type_options 未返回 "
                                + menuTarget.type().name() + " 菜单类型");
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
            Map<String, Object> request = menuDemoRequest(menuTarget);
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
                            "tool", "retrieval_list_display_entity",
                            "request", Map.of(),
                            "status", "success",
                            "resultSummary", "已确认实体 " + ENTITY),
                    Map.of(
                            "tool", "retrieval_list_display_attribute",
                            "request", Map.of("entity", ENTITY),
                            "status", "success",
                            "resultSummary", "已确认用户事件数据字段"),
                    Map.of(
                            "tool", "config_read",
                            "request", Map.of(
                                    "type", menuTarget.configType(),
                                    "fileName", menuTarget.fileName()),
                            "status", "success",
                            "resultSummary", "已确认目标单页面配置使用 user_event 真实接口"),
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
                    - 类型：%s
                    - 层级：LEVEL_1，parentId=0
                    - 目标单页面：%s

                    ```zenvis:confirm
                    %s
                    ```
                    """.formatted(
                    MENU_DEMO_NAME,
                    MENU_DEMO_NAME,
                    menuTarget.type().name(),
                    menuTarget.params(),
                    JacksonUtil.toJson(card));
        } catch (Exception e) {
            log.error("查询添加菜单演示所需 MCP 信息失败: {}", e.getMessage(), e);
            return menuFailureResponse("菜单方案查询", e);
        }
    }

    private String buildChartPreviewResponse(String prompt,
                                             McpToolContext mcpToolContext) {
        try {
            validateUserEventMeta(mcpToolContext);
            Map<String, Object> request = chartQueryRequest(prompt);
            Map<String, Object> analytics = toolResultObject(callTool(
                    mcpToolContext,
                    "entity_aggregate",
                    Map.of("request", request)));
            Map<String, Object> echarts = mapOf(analytics.get("echarts"));
            Map<String, Object> option = mapOf(echarts.get("option"));
            if (option.isEmpty()) {
                throw new IllegalStateException(
                        "entity_aggregate 未返回 echarts.option");
            }
            Map<String, Object> queryMeta = mapOf(analytics.get("meta"));
            List<Map<String, Object>> rows =
                    listOfMaps(mapOf(analytics.get("result")).get("rows"));
            String chartType = String.valueOf(
                    echarts.getOrDefault("chart_type",
                            prompt.contains("柱状图") ? "bar" : "line"));
            boolean grouped = prompt.contains("event_type")
                    || prompt.contains("事件类型");
            String planId = "demo-user-event-" + UUID.randomUUID();
            String title = grouped
                    ? "用户事件数据分类型趋势"
                    : "用户事件数据上报趋势";
            String content = rows.isEmpty()
                    ? "真实查询已完成，当前时间范围内暂无用户事件数据。"
                    : "已按 server_time 聚合真实 user_event 数据，共返回 "
                    + rows.size() + " 个聚合结果。";
            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(Map.of("field", "server_time", "role", "time"));
            if (grouped) {
                fields.add(Map.of("field", "event_type", "role", "dimension"));
            }
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("id", planId + "-preview");
            preview.put("title", title);
            preview.put("content", content);
            preview.put("action", ACTION_ADD_CHART_LIBRARY);
            preview.put("planId", planId);
            preview.put("entity", ENTITY);
            preview.put("entities", List.of(ENTITY));
            preview.put("fields", fields);
            preview.put("query", Map.of(
                    "tool", "entity_aggregate",
                    "request", request));
            preview.put("queryMeta", queryMeta);
            preview.put("chartType", chartType);
            preview.put("api", "/zenvis/api/v1/entity/aggregate/query");
            preview.put("echartsOption", option);
            preview.put("echarts", Map.of(
                    "chart_type", chartType,
                    "option", option));
            preview.put("amisConfig", chartAmisConfig(request));
            preview.put("queriedAt", Instant.now().toString());
            preview.put("validationStatus", "success");

            return """
                    已通过 Meta 校验和 `entity_aggregate` 真实查询生成临时图表，未使用演示数据。

                    ```zenvis:visualization-chart-preview
                    %s
                    ```
                    """.formatted(JacksonUtil.toJson(preview));
        } catch (Exception e) {
            log.error("生成用户事件真实数据临时图表失败: {}", e.getMessage(), e);
            return visualizationDataFailureResponse("临时图表生成", e);
        }
    }

    private String addChartLibraryResponse(ChatSession chatSession) {
        Optional<Map<String, Object>> latest = latestChartPreview(chatSession);
        if (latest.isEmpty()) {
            return """
                    ```zenvis:notice
                    {"title":"未找到可加入图表库的真实图表","content":"当前会话中没有 validationStatus=success 的用户事件临时图表，请先重新生成图表。","level":"warning"}
                    ```
                    """;
        }
        Map<String, Object> preview = latest.get();
        Map<String, Object> record = new LinkedHashMap<>(preview);
        String planId = String.valueOf(preview.get("planId"));
        String chartName = String.valueOf(
                preview.getOrDefault("title", "用户事件数据图表"));
        record.remove("action");
        record.put("id", "demo-chart:" + planId);
        record.put("title", "图表库记录已创建");
        record.put("name", chartName);
        record.put("description", String.valueOf(
                preview.getOrDefault("content", "用户事件真实数据临时图表")));
        record.put("status", "temporary");
        record.put("source", "demo");
        record.put("addedAt", Instant.now().toString());
        record.put("config", preview.get("amisConfig"));
        return """
                已将上一轮真实查询图表快照加入本次会话图表库，未重新查询或替换数据。

                ```zenvis:visualization-chart-record
                %s
                ```
                """.formatted(JacksonUtil.toJson(record));
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
        String selected = selectedAnswerText(prompt);
        String source = StringUtils.hasText(selected) ? selected : prompt;
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
            return applyMenuDemo(chatSession, mcpToolContext);
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
                    菜单演示将复用已经成功应用的用户事件单页面，已重新校验目标配置和系统菜单能力。

                    %s
                    """.formatted(buildMenuConfirmationResponse(
                    chatSession, mcpToolContext).trim());
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
            if (scope.contains("\"dashboardType\":\"html\"") || scope.contains("\"dashboardType\": \"html\"")) {
                dashboardType = "使用静态 HTML 页面实现数据看板";
            } else {
                dashboardType = "使用低代码 amis 页面实现数据看板";
            }
            return """
                    已根据补充信息更新用户事件数据看板配置，请再次确认后续处理。

                    %s
                    """.formatted(buildDashboardConfigResponse("""
                    {"answers":[{"value":"%s"}]}
                    """.formatted(dashboardType)).trim());
        }
        return """
                ```zenvis:notice
                {"title":"未找到待更新配置","content":"没有找到上一轮数据可视化演示确认卡，请重新选择示例并生成配置。","level":"warning"}
                ```
                """;
    }

    private String applyMenuDemo(ChatSession chatSession,
                                 McpToolContext mcpToolContext) {
        try {
            MenuTarget menuTarget =
                    resolveMenuTarget(chatSession, mcpToolContext);
            Map<String, Object> request = menuDemoRequest(menuTarget);
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
                    menuRecord("用户事件数据入口菜单已创建", menu));
        } catch (Exception e) {
            log.error("执行添加菜单 MCP 演示失败: {}", e.getMessage(), e);
            return menuFailureResponse("菜单创建或读回", e);
        }
    }

    private Map<String, Object> menuDemoRequest(MenuTarget target) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", MENU_DEMO_NAME);
        request.put("type", target.type().name());
        request.put("route", target.type().getRoute());
        request.put("level", MenuLevel.LEVEL_1.name());
        request.put("parentId", 0);
        request.put("params", target.params());
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

    private Object firstValue(Map<String, Object> source,
                              String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> mapOf(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, entryValue) ->
                normalized.put(String.valueOf(key), entryValue));
        return normalized;
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

    private record MenuTarget(
            MenuType type,
            String params,
            String configType,
            String fileName) {
    }
}
