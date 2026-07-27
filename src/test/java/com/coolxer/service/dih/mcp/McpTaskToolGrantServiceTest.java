package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.entity.McpTaskToolGrantId;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpTaskToolGrantRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpTaskToolGrantServiceTest {

    private final McpTaskToolGrantRepository repository = mock(McpTaskToolGrantRepository.class);
    private final McpTaskToolGrantService service = new McpTaskToolGrantService(repository);

    @Test
    void grantLookupUsesExactExecutionAndTool() {
        McpTaskToolGrantId id = new McpTaskToolGrantId("execution-1", "local::write_demo");
        when(repository.existsById(id)).thenReturn(true);

        assertThat(service.isGranted(taskContext("execution-1"), "local::write_demo")).isTrue();
        assertThat(service.isGranted(taskContext("execution-2"), "local::write_demo")).isFalse();
        assertThat(service.isGranted(taskContext("execution-1"), "local::other")).isFalse();
    }

    @Test
    void ordinaryBackgroundInvocationCannotUseTaskGrant() {
        assertThat(service.isGranted(
                McpInvocationContext.background("agent_data_analysis"), "local::write_demo"
        )).isFalse();

        verify(repository, never()).existsById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void grantAndExecutionCleanupUsePersistentRepository() {
        McpToolInvocation invocation = new McpToolInvocation()
                .setAnalysisTaskId(7)
                .setTaskExecutionId("execution-1")
                .setRequesterUserId(42)
                .setToolKey("local::write_demo")
                .setRequestId("request-1");

        service.grant(invocation, 1);
        service.revokeExecution("execution-1");

        verify(repository).upsert(
                "execution-1", "local::write_demo", 7, 42, 1, "request-1"
        );
        verify(repository).deleteByIdExecutionId("execution-1");
    }

    private McpInvocationContext taskContext(String executionId) {
        return McpInvocationContext.backgroundTask(
                7,
                executionId,
                42,
                AnalysisTaskApprovalMode.MANUAL,
                event -> { },
                () -> false
        );
    }
}
