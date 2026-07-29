package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import com.coolxer.dao.mysql.repository.McpToolPolicyConfigRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.vo.McpToolPolicyVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class McpToolPolicyService {

    private final McpToolPolicyConfigRepository repository;

    public McpToolPolicyService(McpToolPolicyConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public synchronized McpToolPolicyConfig register(McpToolDescriptor descriptor) {
        McpToolPolicyConfig config = repository.findByToolKey(descriptor.toolKey())
                .orElseGet(McpToolPolicyConfig::new);
        config.setToolKey(descriptor.toolKey())
                .setSourceType(descriptor.sourceType())
                .setServerId(descriptor.serverId())
                .setServerCode(descriptor.serverCode())
                .setServerName(descriptor.serverName())
                .setToolName(descriptor.toolName())
                .setAiToolName(descriptor.aiToolName())
                .setTitle(descriptor.title())
                .setDescription(descriptor.description())
                .setReadOnlyHint(descriptor.readOnlyHint())
                .setDestructiveHint(descriptor.destructiveHint())
                .setRiskLevel(descriptor.riskLevel() == null
                        ? com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN : descriptor.riskLevel())
                .setDefaultPolicy(descriptor.defaultPolicy() == null ? McpApprovalPolicy.ASK : descriptor.defaultPolicy())
                .setAvailable(true)
                .setLastSeenTime(new Date());
        return repository.save(config);
    }

    public McpApprovalPolicy effectivePolicy(McpToolDescriptor descriptor) {
        return register(descriptor).effectivePolicy();
    }

    public McpApprovalPolicy effectivePolicy(String toolKey, McpApprovalPolicy fallback) {
        return repository.findByToolKey(toolKey)
                .map(McpToolPolicyConfig::effectivePolicy)
                .orElse(fallback == null ? McpApprovalPolicy.ASK : fallback);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public McpToolPolicyVo update(String toolKey, McpApprovalPolicy configuredPolicy) {
        McpToolPolicyConfig config = repository.findByToolKey(toolKey)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP工具不存在"));
        config.setConfiguredPolicy(configuredPolicy);
        return new McpToolPolicyVo(repository.save(config));
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public List<McpToolPolicyVo> bulkUpdate(List<String> toolKeys, McpApprovalPolicy configuredPolicy) {
        if (toolKeys == null || toolKeys.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return toolKeys.stream().distinct().map(key -> update(key, configuredPolicy)).toList();
    }

    public PageRowsVo<McpToolPolicyVo> list(int page,
                                            int perPage,
                                            String keyword,
                                            McpToolSourceType sourceType,
                                            McpApprovalPolicy effectivePolicy,
                                            Boolean available) {
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        List<McpToolPolicyVo> rows = repository.findAll().stream()
                .filter(row -> sourceType == null || row.getSourceType() == sourceType)
                .filter(row -> available == null || available.equals(row.getAvailable()))
                .filter(row -> effectivePolicy == null || row.effectivePolicy() == effectivePolicy)
                .filter(row -> normalizedKeyword.isEmpty()
                        || contains(row.getToolName(), normalizedKeyword)
                        || contains(row.getAiToolName(), normalizedKeyword)
                        || contains(row.getServerName(), normalizedKeyword)
                        || contains(row.getDescription(), normalizedKeyword))
                .sorted(Comparator.comparing(McpToolPolicyConfig::getSourceType)
                        .thenComparing(row -> StringUtils.defaultString(row.getServerName()))
                        .thenComparing(McpToolPolicyConfig::getToolName))
                .map(McpToolPolicyVo::new)
                .toList();
        int safePage = Math.max(page, 1);
        int safePerPage = Math.max(perPage, 1);
        int from = Math.min((safePage - 1) * safePerPage, rows.size());
        int to = Math.min(from + safePerPage, rows.size());
        return new PageRowsVo<>(rows.subList(from, to), rows.size());
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void markServerUnavailable(Integer serverId) {
        if (serverId == null) {
            return;
        }
        repository.findByServerId(serverId).forEach(config -> {
            config.setAvailable(false);
            repository.save(config);
        });
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void deleteServerPolicies(Integer serverId) {
        if (serverId != null) {
            repository.deleteAll(repository.findByServerId(serverId));
        }
    }

    public boolean isDenied(String toolKey, McpApprovalPolicy fallback) {
        return effectivePolicy(toolKey, fallback) == McpApprovalPolicy.DENY;
    }

    public McpApprovalPolicy effectivePolicyByAiToolName(String aiToolName, McpApprovalPolicy fallback) {
        return repository.findAll().stream()
                .filter(row -> StringUtils.equals(aiToolName, row.getAiToolName()))
                .findFirst()
                .map(McpToolPolicyConfig::effectivePolicy)
                .orElse(fallback == null ? McpApprovalPolicy.ASK : fallback);
    }

    private static boolean contains(String value, String keyword) {
        return StringUtils.defaultString(value).toLowerCase(Locale.ROOT).contains(keyword);
    }
}
