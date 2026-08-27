package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import com.coolxer.dao.mysql.repository.McpToolPolicyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolPolicyServiceTest {

    private McpToolPolicyConfigRepository repository;
    private McpToolPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(McpToolPolicyConfigRepository.class);
        service = new McpToolPolicyService(repository);
        when(repository.save(any(McpToolPolicyConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void unknownRiskDefaultsToAsk() {
        when(repository.findByToolKey("external::1::unknown")).thenReturn(Optional.empty());

        McpToolPolicyConfig saved = service.register(descriptor(null, null, null));

        assertThat(saved.getDefaultPolicy()).isEqualTo(McpApprovalPolicy.ASK);
        assertThat(saved.effectivePolicy()).isEqualTo(McpApprovalPolicy.ASK);
    }

    @Test
    void configuredPolicyOverridesAndCanReturnToDefault() {
        McpToolPolicyConfig config = new McpToolPolicyConfig()
                .setToolKey("external::1::query")
                .setDefaultPolicy(McpApprovalPolicy.ALLOW)
                .setConfiguredPolicy(McpApprovalPolicy.DENY);
        when(repository.findByToolKey(config.getToolKey())).thenReturn(Optional.of(config));

        assertThat(service.effectivePolicy(config.getToolKey(), McpApprovalPolicy.ASK))
                .isEqualTo(McpApprovalPolicy.DENY);
        service.update(config.getToolKey(), null);
        assertThat(config.effectivePolicy()).isEqualTo(McpApprovalPolicy.ALLOW);
    }

    @Test
    void readOnlyExternalDescriptorKeepsAllowDefault() {
        when(repository.findByToolKey("external::1::query")).thenReturn(Optional.empty());

        McpToolPolicyConfig saved = service.register(descriptor(true, false, McpApprovalPolicy.ALLOW));

        assertThat(saved.getReadOnlyHint()).isTrue();
        assertThat(saved.getDefaultPolicy()).isEqualTo(McpApprovalPolicy.ALLOW);
    }

    @Test
    void serverRefreshMarksMissingToolsUnavailableAndRediscoveryRestoresThem() {
        McpToolPolicyConfig config = new McpToolPolicyConfig()
                .setToolKey("external::1::query")
                .setSourceType(McpToolSourceType.EXTERNAL)
                .setServerId(1)
                .setToolName("query")
                .setDefaultPolicy(McpApprovalPolicy.ALLOW)
                .setAvailable(true);
        when(repository.findByServerId(1)).thenReturn(List.of(config));
        when(repository.findByToolKey(config.getToolKey())).thenReturn(Optional.of(config));

        service.markServerUnavailable(1);
        assertThat(config.getAvailable()).isFalse();
        service.register(descriptor(true, false, McpApprovalPolicy.ALLOW));
        assertThat(config.getAvailable()).isTrue();
    }

    private McpToolDescriptor descriptor(Boolean readOnly, Boolean destructive, McpApprovalPolicy policy) {
        String name = readOnly == null ? "unknown" : "query";
        return new McpToolDescriptor(
                McpToolDescriptor.externalKey(1, name),
                McpToolSourceType.EXTERNAL,
                1,
                "server",
                "Server",
                name,
                "server_" + name,
                name,
                "description",
                readOnly,
                destructive,
                readOnly == null ? com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN
                        : com.coolxer.commons.enums.McpToolRiskLevel.LOW,
                policy
        );
    }
}
