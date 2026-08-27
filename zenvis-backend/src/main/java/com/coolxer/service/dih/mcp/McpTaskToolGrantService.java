package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.dao.mysql.entity.McpTaskToolGrantId;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpTaskToolGrantRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class McpTaskToolGrantService {

    private final McpTaskToolGrantRepository repository;

    public boolean isGranted(McpInvocationContext context, String toolKey) {
        if (context == null || context.channel() != McpInvocationChannel.BACKGROUND_AGENT
                || context.analysisTaskId() == null || StringUtils.isBlank(context.executionId())
                || StringUtils.isBlank(toolKey)) {
            return false;
        }
        return repository.existsById(new McpTaskToolGrantId(context.executionId(), toolKey));
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void grant(McpToolInvocation invocation, Integer grantedBy) {
        repository.upsert(
                invocation.getTaskExecutionId(),
                invocation.getToolKey(),
                invocation.getAnalysisTaskId(),
                invocation.getRequesterUserId(),
                grantedBy == null ? 1 : grantedBy,
                invocation.getRequestId()
        );
    }

    public void revokeExecution(String executionId) {
        if (StringUtils.isNotBlank(executionId)) {
            repository.deleteByIdExecutionId(executionId);
        }
    }
}
