package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * 数据库驱动的 MCP 客户端注册表。
 */
@Slf4j
@Service
public class McpClientServiceImpl implements McpClientService {

    private static final String DEFAULT_SSE_ENDPOINT = "/sse";
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int MAX_PROMPT_CHARS = 8000;

    private final McpServerConfigRepository mcpServerConfigRepository;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Integer, ClientHolder> clients = new ConcurrentHashMap<>();
    private final String clientVersion;
    private final boolean allowPrivateServerUrls;
    private final McpApprovalService approvalService;
    private final McpToolPolicyService policyService;

    @Autowired
    public McpClientServiceImpl(McpServerConfigRepository mcpServerConfigRepository,
                                ObjectMapper objectMapper,
                                McpApprovalService approvalService,
                                McpToolPolicyService policyService,
                                @Value("${spring.ai.mcp.server.version:1.0.0}") String clientVersion,
                                @Value("${app.ai.mcp.allow-private-server-urls:true}") boolean allowPrivateServerUrls) {
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.objectMapper = objectMapper;
        this.approvalService = approvalService;
        this.policyService = policyService;
        this.clientVersion = clientVersion;
        this.allowPrivateServerUrls = allowPrivateServerUrls;
    }

    McpClientServiceImpl(McpServerConfigRepository mcpServerConfigRepository,
                         ObjectMapper objectMapper,
                         String clientVersion,
                         boolean allowPrivateServerUrls,
                         boolean ignoredAllowDestructiveToolCalls) {
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.objectMapper = objectMapper;
        this.approvalService = null;
        this.policyService = null;
        this.clientVersion = clientVersion;
        this.allowPrivateServerUrls = allowPrivateServerUrls;
    }

    @PostConstruct
    public void init() {
        refreshEnabledServers();
    }

    @PreDestroy
    public void destroy() {
        clients.values().forEach(ClientHolder::close);
        clients.clear();
    }

    @Override
    public PageRowsVo<McpServerVo> getPageList(McpServerSearchDto searchDto) {
        McpServerSearchDto condition = searchDto == null ? new McpServerSearchDto() : searchDto;
        try {
            Pageable pageable = PageRequest.of(Math.max(condition.getPage(), 1) - 1, Math.max(condition.getPerPage(), 1));
            Page<McpServerConfig> page = mcpServerConfigRepository.findByPage(
                    pageable,
                    blankToNull(condition.getKeyword()),
                    condition.getEnabled(),
                    condition.getConnected()
            );
            return new PageRowsVo<>(
                    page.getContent().stream().map(config -> new McpServerVo(config, toolCount(config.getId()))).toList(),
                    page.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询MCP服务失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public McpServerVo create(McpServerDto dto) {
        checkCreateOrUpdate(dto);
        String code = normalizeCode(dto.getCode());
        if (mcpServerConfigRepository.findByCode(code).isPresent()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务标识已存在");
        }

        McpServerConfig config = new McpServerConfig();
        config.updateFromDto(dto);
        config.setCode(code);
        applyDefaults(config);
        validateHeaders(config.getHeaders());
        McpServerConfig saved = mcpServerConfigRepository.save(config);
        if (Boolean.TRUE.equals(saved.getEnabled())) {
            return refresh(saved.getId());
        }
        return new McpServerVo(saved, 0);
    }

    @Override
    public Boolean update(Integer id, McpServerDto dto) {
        checkCreateOrUpdate(dto);
        McpServerConfig config = getConfig(id);
        String code = normalizeCode(dto.getCode());
        Optional<McpServerConfig> sameCode = mcpServerConfigRepository.findByCode(code);
        if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务标识已存在");
        }

        config.updateFromDto(dto);
        config.setCode(code);
        applyDefaults(config);
        validateHeaders(config.getHeaders());
        mcpServerConfigRepository.save(config);
        closeClient(id);
        if (Boolean.TRUE.equals(config.getEnabled())) {
            refresh(id);
        } else if (policyService != null) {
            policyService.markServerUnavailable(id);
        }
        return true;
    }

    @Override
    public void delete(Integer id) {
        closeClient(id);
        if (policyService != null) {
            policyService.deleteServerPolicies(id);
        }
        if (mcpServerConfigRepository.findById(id).isPresent()) {
            mcpServerConfigRepository.deleteById(id);
        }
    }

    @Override
    public McpServerVo info(Integer id) {
        McpServerConfig config = getConfig(id);
        return new McpServerVo(config, toolCount(id));
    }

    @Override
    public McpServerVo setEnabled(Integer id, boolean enabled) {
        McpServerConfig config = getConfig(id);
        config.setEnabled(enabled);
        if (!enabled) {
            config.setConnected(false);
            config.setLastError(null);
            closeClient(id);
            if (policyService != null) {
                policyService.markServerUnavailable(id);
            }
            return new McpServerVo(mcpServerConfigRepository.save(config), 0);
        }
        mcpServerConfigRepository.save(config);
        return refresh(id);
    }

    @Override
    public McpServerVo refresh(Integer id) {
        McpServerConfig config = getConfig(id);
        closeClient(id);
        if (policyService != null) {
            // Tools removed by a server refresh stay discoverable in policy history but are no longer injectable.
            policyService.markServerUnavailable(id);
        }

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            config.setConnected(false);
            config.setLastError("MCP服务未启用");
            McpServerConfig saved = mcpServerConfigRepository.save(config);
            return new McpServerVo(saved, 0);
        }

        try {
            McpSyncClient client = createClient(config);
            McpSchema.InitializeResult initializeResult = client.initialize();
            List<McpSchema.Tool> tools = safeListTools(client);
            clients.put(id, new ClientHolder(config.getId(), config.getCode(), config.getName(), client, tools));
            registerExternalTools(config, tools);

            config.setConnected(true);
            config.setLastError(null);
            config.setLastConnectedTime(new Date());
            McpServerConfig saved = mcpServerConfigRepository.save(config);
            log.info("MCP服务连接成功: id={}, code={}, server={}, tools={}",
                    saved.getId(), saved.getCode(), resolveServerName(initializeResult), tools.size());
            return new McpServerVo(saved, tools.size());
        } catch (Exception e) {
            String errorMessage = resolveErrorMessage(e);
            config.setConnected(false);
            config.setLastError(errorMessage);
            McpServerConfig saved = mcpServerConfigRepository.save(config);
            closeClient(id);
            if (policyService != null) {
                policyService.markServerUnavailable(id);
            }
            log.warn("MCP服务连接失败: id={}, code={}, error={}", id, config.getCode(), errorMessage, e);
            return new McpServerVo(saved, 0);
        }
    }

    @Override
    public List<McpServerVo> refreshAll() {
        return mcpServerConfigRepository.findByEnabledTrueOrderByIdAsc().stream()
                .map(config -> refresh(config.getId()))
                .toList();
    }

    @Override
    public List<McpToolVo> listTools(Integer serverId) {
        if (serverId != null) {
            ClientHolder holder = clients.get(serverId);
            return holder == null ? List.of() : holder.toToolVos();
        }
        return clients.values().stream()
                .sorted((left, right) -> left.getServerId().compareTo(right.getServerId()))
                .flatMap(holder -> holder.toToolVos().stream())
                .toList();
    }

    @Override
    public Object callTool(McpToolCallDto callDto) {
        return callTool(callDto, null);
    }

    @Override
    public Object callTool(McpToolCallDto callDto, User user) {
        if (callDto == null || StringUtils.isBlank(callDto.getName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        ClientHolder holder = resolveHolder(callDto);
        McpSchema.Tool tool = holder.findTool(callDto.getName());
        if (tool == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP工具不存在");
        }
        Map<String, Object> arguments = callDto.getArguments() == null ? Map.of() : callDto.getArguments();
        if (approvalService != null) {
            McpToolDescriptor descriptor = externalDescriptor(holder, tool);
            String input = summarizeAsJson(arguments);
            McpApprovalService.ManualGate gate = approvalService.prepareManual(
                    descriptor, input, user, callDto.getApprovalRequestId());
            if (!gate.executable()) {
                return new com.coolxer.model.dih.vo.McpToolCallResultVo(
                        gate.invocation().getRequestId(), gate.invocation().getStatus(), null,
                        gate.invocation().getErrorSummary());
            }
            return approvalService.completeManual(gate.invocation().getRequestId(),
                    () -> holder.getClient().callTool(new McpSchema.CallToolRequest(callDto.getName(), arguments)));
        }
        long startedAt = System.nanoTime();
        log.info("MCP工具测试调用开始: serverCode={}, tool={}, arguments={}",
                holder.getServerCode(), callDto.getName(), summarizeObject(arguments));
        try {
            Object result = holder.getClient().callTool(new McpSchema.CallToolRequest(callDto.getName(), arguments));
            log.info("MCP工具测试调用成功: serverCode={}, tool={}, durationMs={}, result={}",
                    holder.getServerCode(), callDto.getName(), elapsedMillis(startedAt), summarizeObject(result));
            return result;
        } catch (RuntimeException e) {
            log.warn("MCP工具测试调用失败: serverCode={}, tool={}, durationMs={}, error={}",
                    holder.getServerCode(), callDto.getName(), elapsedMillis(startedAt), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean hasAvailableTools() {
        return hasAvailableTools(null);
    }

    @Override
    public boolean hasAvailableTools(List<String> serverCodes) {
        return activeHolders(serverCodes).stream().anyMatch(holder -> !holder.getTools().isEmpty());
    }

    @Override
    public String buildEnabledMcpPrompt() {
        return buildEnabledMcpPrompt(null);
    }

    @Override
    public String buildEnabledMcpPrompt(List<String> serverCodes) {
        StringBuilder prompt = new StringBuilder();
        List<ClientHolder> holders = activeHolders(serverCodes);

        for (ClientHolder holder : holders) {
            StringBuilder block = new StringBuilder();
            block.append("### MCP服务：").append(holder.getServerName())
                    .append(" (").append(holder.getServerCode()).append(")\n");
            for (McpSchema.Tool tool : holder.getTools()) {
                McpToolDescriptor descriptor = externalDescriptor(holder, tool);
                McpApprovalPolicy effectivePolicy = policyService == null
                        ? descriptor.defaultPolicy()
                        : policyService.effectivePolicy(descriptor.toolKey(), descriptor.defaultPolicy());
                if (effectivePolicy == McpApprovalPolicy.DENY) {
                    continue;
                }
                block.append("- ").append(holder.aiToolName(tool))
                        .append("：")
                        .append(StringUtils.defaultIfBlank(tool.description(), StringUtils.defaultIfBlank(tool.title(), tool.name())))
                        .append(effectivePolicy == McpApprovalPolicy.ASK ? "（调用前需要用户审批）" : "")
                        .append("\n");
            }
            int remain = MAX_PROMPT_CHARS - prompt.length();
            if (remain <= 0) {
                break;
            }
            if (block.length() > remain) {
                prompt.append(block, 0, remain);
                break;
            }
            prompt.append(block).append("\n");
        }
        return prompt.toString().trim();
    }

    @Override
    public ToolCallbackProvider getToolCallbackProvider() {
        return getToolCallbackProvider(null);
    }

    @Override
    public ToolCallbackProvider getToolCallbackProvider(List<String> serverCodes) {
        ToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(getActiveClients(serverCodes))
                .toolNamePrefixGenerator((connectionInfo, tool) -> {
                    String prefix = connectionInfo.clientInfo() == null
                            ? "mcp"
                            : StringUtils.defaultIfBlank(connectionInfo.clientInfo().name(), "mcp");
                    return formatAiToolName(prefix, tool.name());
                })
                .build();
        if (approvalService == null || policyService == null) {
            return provider;
        }
        Map<String, McpToolDescriptor> descriptors = new LinkedHashMap<>();
        for (ClientHolder holder : activeHolders(serverCodes)) {
            for (McpSchema.Tool tool : holder.getTools()) {
                McpToolDescriptor descriptor = externalDescriptor(holder, tool);
                descriptors.put(descriptor.aiToolName(), descriptor);
            }
        }
        return new McpApprovalToolCallbackProvider(provider, descriptors, approvalService, policyService);
    }

    @Override
    public List<McpSyncClient> getActiveClients() {
        return getActiveClients(null);
    }

    @Override
    public List<McpSyncClient> getActiveClients(List<String> serverCodes) {
        return activeHolders(serverCodes).stream()
                .map(ClientHolder::getClient)
                .toList();
    }

    private List<ClientHolder> activeHolders(List<String> serverCodes) {
        Set<String> scope = normalizeServerCodes(serverCodes);
        return clients.values().stream()
                .filter(holder -> scope.isEmpty() || scope.contains(holder.getServerCode()))
                .sorted((left, right) -> left.getServerId().compareTo(right.getServerId()))
                .toList();
    }

    private Set<String> normalizeServerCodes(List<String> serverCodes) {
        if (serverCodes == null || serverCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String serverCode : serverCodes) {
            String value = normalizeCodeLenient(serverCode);
            if (StringUtils.isNotBlank(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void refreshEnabledServers() {
        try {
            List<McpServerConfig> enabledConfigs = mcpServerConfigRepository.findByEnabledTrueOrderByIdAsc();
            for (McpServerConfig config : enabledConfigs) {
                refresh(config.getId());
            }
            log.info("MCP客户端初始化完成，启用服务数: {}, 可用服务数: {}", enabledConfigs.size(), clients.size());
        } catch (Exception e) {
            log.warn("MCP客户端初始化失败，后端将继续启动: {}", e.getMessage(), e);
        }
    }

    private McpSyncClient createClient(McpServerConfig config) {
        validateBaseUrl(config.getBaseUrl());
        Map<String, String> headers = parseHeaders(config.getHeaders());
        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(config.getBaseUrl())
                .sseEndpoint(config.getSseEndpoint())
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()));
        if (!headers.isEmpty()) {
            transportBuilder.httpRequestCustomizer((builder, method, endpoint, body, context) ->
                    headers.forEach(builder::header));
        }
        HttpClientSseClientTransport transport = transportBuilder.build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation(config.getCode(), config.getName(), clientVersion))
                .requestTimeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
                .build();
    }

    private List<McpSchema.Tool> safeListTools(McpSyncClient client) {
        McpSchema.ListToolsResult result = client.listTools();
        if (result == null || result.tools() == null) {
            return List.of();
        }
        return result.tools();
    }

    private Map<String, String> parseHeaders(String headers) {
        if (StringUtils.isBlank(headers)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(headers, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Map<String, String> parsed = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && value != null && StringUtils.isNotBlank(value.toString())) {
                    parsed.put(key, value.toString());
                }
            });
            return parsed;
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "请求头必须是JSON对象");
        }
    }

    private void validateHeaders(String headers) {
        parseHeaders(headers);
    }

    private ClientHolder resolveHolder(McpToolCallDto callDto) {
        if (callDto.getServerId() != null) {
            ClientHolder holder = clients.get(callDto.getServerId());
            if (holder != null) {
                return holder;
            }
        }
        if (StringUtils.isNotBlank(callDto.getServerCode())) {
            return clients.values().stream()
                    .filter(holder -> callDto.getServerCode().equals(holder.getServerCode()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务未连接"));
        }
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务未连接");
    }

    private McpServerConfig getConfig(Integer id) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return mcpServerConfigRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务不存在"));
    }

    private void closeClient(Integer id) {
        ClientHolder holder = clients.remove(id);
        if (holder != null) {
            holder.close();
        }
    }

    private int toolCount(Integer id) {
        ClientHolder holder = clients.get(id);
        return holder == null ? 0 : holder.getTools().size();
    }

    private void checkCreateOrUpdate(McpServerDto dto) {
        if (dto == null
                || StringUtils.isBlank(dto.getCode())
                || StringUtils.isBlank(dto.getName())
                || StringUtils.isBlank(dto.getBaseUrl())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        validateBaseUrl(dto.getBaseUrl());
    }

    private void validateBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(StringUtils.trimToEmpty(baseUrl));
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务地址仅支持 http/https");
            }
            if (StringUtils.isBlank(host)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务地址缺少host");
            }
            if (!allowPrivateServerUrls && isPrivateOrLocalHost(host)) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "MCP服务地址不允许指向本机或内网地址");
            }
        } catch (Exception e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务地址格式不正确");
        }
    }

    private static void applyDefaults(McpServerConfig config) {
        config.setCode(normalizeCode(config.getCode()));
        config.setName(StringUtils.trim(config.getName()));
        config.setBaseUrl(StringUtils.removeEnd(StringUtils.trim(config.getBaseUrl()), "/"));
        config.setSseEndpoint(normalizeEndpoint(config.getSseEndpoint()));
        config.setEnabled(config.getEnabled() == null || config.getEnabled());
        config.setRequestTimeoutSeconds(defaultPositive(config.getRequestTimeoutSeconds(), DEFAULT_REQUEST_TIMEOUT_SECONDS));
        config.setConnectTimeoutSeconds(defaultPositive(config.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS));
    }

    private static String normalizeCode(String code) {
        String normalized = StringUtils.trimToEmpty(code)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (StringUtils.isBlank(normalized)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (normalized.length() > 64) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务标识不能超过64个字符");
        }
        return normalized;
    }

    private static String normalizeCodeLenient(String code) {
        return StringUtils.trimToEmpty(code)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String normalizeEndpoint(String endpoint) {
        String normalized = StringUtils.defaultIfBlank(StringUtils.trim(endpoint), DEFAULT_SSE_ENDPOINT);
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static int defaultPositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String resolveErrorMessage(Exception e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }

    private static String resolveServerName(McpSchema.InitializeResult initializeResult) {
        if (initializeResult == null || initializeResult.serverInfo() == null) {
            return "";
        }
        McpSchema.Implementation serverInfo = initializeResult.serverInfo();
        return StringUtils.defaultIfBlank(serverInfo.title(), serverInfo.name());
    }

    private static boolean isPrivateOrLocalHost(String host) throws Exception {
        String normalizedHost = StringUtils.trimToEmpty(host).toLowerCase();
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            return true;
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return true;
            }
            String hostAddress = address.getHostAddress();
            if (hostAddress != null) {
                String normalizedAddress = hostAddress.toLowerCase();
                if (normalizedAddress.startsWith("fc") || normalizedAddress.startsWith("fd")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatAiToolName(String serverCode, String toolName) {
        String formatted = McpToolUtils.format(serverCode + "_" + toolName);
        if (formatted.length() <= 64) {
            return formatted;
        }
        String hash = crc32Hex(formatted);
        int prefixLength = 64 - hash.length() - 1;
        return formatted.substring(0, Math.max(prefixLength, 0)) + "_" + hash;
    }

    private static String crc32Hex(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Long.toHexString(crc32.getValue());
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String summarizeObject(Object value) {
        if (value == null) {
            return "";
        }
        String raw;
        try {
            raw = objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            raw = String.valueOf(value);
        }
        String normalized = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private String summarizeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void registerExternalTools(McpServerConfig config, List<McpSchema.Tool> tools) {
        if (policyService == null || tools == null) {
            return;
        }
        ClientHolder holder = new ClientHolder(config.getId(), config.getCode(), config.getName(), null, tools);
        tools.forEach(tool -> policyService.register(externalDescriptor(holder, tool)));
    }

    private McpToolDescriptor externalDescriptor(ClientHolder holder, McpSchema.Tool tool) {
        McpSchema.ToolAnnotations annotations = tool.annotations();
        Boolean readOnly = annotations == null ? null : annotations.readOnlyHint();
        Boolean destructive = annotations == null ? null : annotations.destructiveHint();
        McpApprovalPolicy defaultPolicy = Boolean.TRUE.equals(readOnly)
                ? McpApprovalPolicy.ALLOW : McpApprovalPolicy.ASK;
        return new McpToolDescriptor(
                McpToolDescriptor.externalKey(holder.getServerId(), tool.name()),
                McpToolSourceType.EXTERNAL,
                holder.getServerId(),
                holder.getServerCode(),
                holder.getServerName(),
                tool.name(),
                holder.aiToolName(tool),
                tool.title(),
                tool.description(),
                readOnly,
                destructive,
                Boolean.TRUE.equals(readOnly)
                        ? com.coolxer.commons.enums.McpToolRiskLevel.LOW
                        : com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN,
                defaultPolicy
        );
    }

    @Data
    @AllArgsConstructor
    private class ClientHolder {
        private Integer serverId;
        private String serverCode;
        private String serverName;
        private McpSyncClient client;
        private List<McpSchema.Tool> tools;

        private List<McpToolVo> toToolVos() {
            List<McpToolVo> rows = new ArrayList<>();
            for (McpSchema.Tool tool : tools) {
                McpSchema.ToolAnnotations annotations = tool.annotations();
                String toolKey = McpToolDescriptor.externalKey(serverId, tool.name());
                McpApprovalPolicy defaultPolicy = annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint())
                        ? McpApprovalPolicy.ALLOW : McpApprovalPolicy.ASK;
                McpApprovalPolicy effectivePolicy = policyService == null
                        ? defaultPolicy : policyService.effectivePolicy(toolKey, defaultPolicy);
                McpToolPolicyConfig storedPolicy = policyService == null ? null
                        : policyService.register(externalDescriptor(this, tool));
                rows.add(McpToolVo.builder()
                        .serverId(serverId)
                        .serverCode(serverCode)
                        .serverName(serverName)
                        .name(tool.name())
                        .aiToolName(aiToolName(tool))
                        .title(tool.title())
                        .description(tool.description())
                        .inputSchema(tool.inputSchema())
                        .outputSchema(tool.outputSchema())
                        .readOnlyHint(annotations == null ? null : annotations.readOnlyHint())
                        .destructiveHint(annotations == null ? null : annotations.destructiveHint())
                        .idempotentHint(annotations == null ? null : annotations.idempotentHint())
                        .openWorldHint(annotations == null ? null : annotations.openWorldHint())
                        .toolKey(toolKey)
                        .riskLevel(Boolean.TRUE.equals(annotations == null ? null : annotations.readOnlyHint())
                                ? com.coolxer.commons.enums.McpToolRiskLevel.LOW
                                : com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN)
                        .defaultApprovalPolicy(defaultPolicy)
                        .configuredApprovalPolicy(storedPolicy == null ? null : storedPolicy.getConfiguredPolicy())
                        .effectiveApprovalPolicy(effectivePolicy)
                        .available(true)
                        .build());
            }
            return rows;
        }

        private String aiToolName(McpSchema.Tool tool) {
            return formatAiToolName(serverCode, tool.name());
        }

        private McpSchema.Tool findTool(String toolName) {
            if (StringUtils.isBlank(toolName) || tools == null) {
                return null;
            }
            return tools.stream()
                    .filter(tool -> toolName.equals(tool.name()))
                    .findFirst()
                    .orElse(null);
        }

        private void close() {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                log.debug("关闭MCP客户端失败: serverCode={}", serverCode, e);
            }
        }
    }
}
