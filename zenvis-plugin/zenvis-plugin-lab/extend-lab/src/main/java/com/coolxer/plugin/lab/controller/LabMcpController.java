package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.service.LabCapabilityService;
import com.coolxer.plugin.lab.service.LabNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class LabMcpController {

    private static final Logger log = LoggerFactory.getLogger(LabMcpController.class);

    public static final String PLUGIN_API_PREFIX = "/api/v1/plugin/com.coolxer.plugin.lab";
    public static final String MCP_SSE_ENDPOINT = PLUGIN_API_PREFIX + "/mcp/sse";
    public static final String MCP_MESSAGE_ENDPOINT = PLUGIN_API_PREFIX + "/mcp/message";
    public static final String CAPABILITY_TOOL_NAME = "lab_capability_catalog";
    public static final String NOTE_SEARCH_TOOL_NAME = "lab_note_search";
    public static final int NOTE_SEARCH_DEFAULT_LIMIT = 20;
    public static final int NOTE_SEARCH_MAX_LIMIT = 50;
    public static final String EXTENSION_VERSION = "1.0.1";

    private final LabCapabilityService capabilityService;
    private final LabNoteService noteService;
    private final McpJsonMapper jsonMapper;
    private final List<HttpMessageConverter<?>> messageConverters;
    private final WebMvcSseServerTransportProvider transportProvider;
    private final McpSyncServer mcpServer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LabMcpController(LabCapabilityService capabilityService, LabNoteService noteService) {
        this.capabilityService = capabilityService;
        this.noteService = noteService;
        this.jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        this.messageConverters = List.of(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter(),
                new ByteArrayHttpMessageConverter());
        this.transportProvider = WebMvcSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .baseUrl("")
                .sseEndpoint(MCP_SSE_ENDPOINT)
                .messageEndpoint(MCP_MESSAGE_ENDPOINT)
                .keepAliveInterval(Duration.ofSeconds(20))
                .build();
        this.mcpServer = McpServer.sync(transportProvider)
                .serverInfo("lab-extension", EXTENSION_VERSION)
                .instructions("插件能力实验室的只读工具：查看能力目录、按关键字和状态检索实验室笔记。不会修改数据。")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tool(capabilityToolDefinition(), (exchange, arguments) -> capabilityCatalog())
                .tool(noteSearchToolDefinition(), (exchange, arguments) -> searchNotes(arguments))
                .build();
    }

    @GetMapping(path = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void mcpSse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        delegateToMcpRouter(request, response);
    }

    @PostMapping(path = "/mcp/message")
    public void mcpMessage(HttpServletRequest request, HttpServletResponse response) throws Exception {
        delegateToMcpRouter(request, response);
    }

    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            mcpServer.closeGracefully();
        }
    }

    List<McpSchema.Tool> mcpTools() {
        return mcpServer.listTools();
    }

    private void delegateToMcpRouter(HttpServletRequest request, HttpServletResponse response) throws Exception {
        ServerRequest serverRequest = ServerRequest.create(request, messageConverters);
        HandlerFunction<ServerResponse> handler = transportProvider.getRouterFunction()
                .route(serverRequest)
                .orElseThrow(() -> new IllegalStateException("Lab MCP 路由不匹配: " + request.getRequestURI()));
        try {
            handler.handle(serverRequest).writeTo(request, response, () -> messageConverters);
        } catch (Exception exception) {
            if (response.isCommitted()) {
                log.debug("Lab MCP 客户端在响应提交后断开: {}", exception.getMessage());
                return;
            }
            throw exception;
        }
    }

    private McpSchema.Tool capabilityToolDefinition() {
        return McpSchema.Tool.builder()
                .name(CAPABILITY_TOOL_NAME)
                .title("插件能力实验室能力目录")
                .description("返回插件能力矩阵、演示入口和只读边界；无参数。")
                .inputSchema(new McpSchema.JsonSchema(
                        "object", Map.of(), List.of(), false, null, null))
                .annotations(readOnlyAnnotations("插件能力实验室能力目录"))
                .build();
    }

    private McpSchema.Tool noteSearchToolDefinition() {
        Map<String, Object> keyword = stringProperty("标题或正文关键字，最多 100 个字符", 100);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", "string");
        status.put("description", "可选状态：draft、published、archived");
        status.put("enum", List.of("draft", "published", "archived"));
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回条数，1 到 50");
        limit.put("minimum", 1);
        limit.put("maximum", NOTE_SEARCH_MAX_LIMIT);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", keyword);
        properties.put("status", status);
        properties.put("limit", limit);
        return McpSchema.Tool.builder()
                .name(NOTE_SEARCH_TOOL_NAME)
                .title("检索实验室笔记")
                .description("只读检索插件 MySQL 中的合成笔记，按更新时间倒序返回；不会创建、修改或删除数据。")
                .inputSchema(new McpSchema.JsonSchema(
                        "object", properties, List.of(), false, null, null))
                .annotations(readOnlyAnnotations("检索实验室笔记"))
                .build();
    }

    private McpSchema.CallToolResult capabilityCatalog() {
        return serialize(capabilityService.list(), "序列化能力目录失败");
    }

    private McpSchema.CallToolResult searchNotes(Map<String, Object> arguments) {
        try {
            String keyword = arguments == null ? null : text(arguments.get("keyword"));
            String status = arguments == null ? null : text(arguments.get("status"));
            int limit = noteSearchLimit(arguments == null ? null : arguments.get("limit"));
            PageRows<LabNote> page = noteService.search(1, limit, keyword, status);
            return serialize(page, "序列化笔记检索结果失败");
        } catch (RuntimeException exception) {
            return new McpSchema.CallToolResult(exception.getMessage(), true);
        }
    }

    McpSchema.CallToolResult serialize(Object value, String failureMessage) {
        try {
            return new McpSchema.CallToolResult(jsonMapper.writeValueAsString(value), false);
        } catch (Exception exception) {
            return new McpSchema.CallToolResult(failureMessage, true);
        }
    }

    private McpSchema.ToolAnnotations readOnlyAnnotations(String title) {
        return new McpSchema.ToolAnnotations(title, true, false, true, false, false);
    }

    private Map<String, Object> stringProperty(String description, int maxLength) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        property.put("maxLength", maxLength);
        return property;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static int noteSearchLimit(Object value) {
        if (value == null) {
            return NOTE_SEARCH_DEFAULT_LIMIT;
        }
        int limit;
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            limit = number.intValue();
            if (!Double.isFinite(numeric) || numeric != limit) {
                throw new IllegalArgumentException("limit 必须是 1 到 50 的整数");
            }
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                limit = Integer.parseInt(text.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("limit 必须是 1 到 50 的整数");
            }
        } else {
            throw new IllegalArgumentException("limit 必须是 1 到 50 的整数");
        }
        if (limit < 1 || limit > NOTE_SEARCH_MAX_LIMIT) {
            throw new IllegalArgumentException("limit 必须是 1 到 50 的整数");
        }
        return limit;
    }
}
