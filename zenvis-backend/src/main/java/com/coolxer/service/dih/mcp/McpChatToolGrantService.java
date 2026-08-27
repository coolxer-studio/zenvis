package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.dao.mysql.entity.McpChatToolGrantId;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpChatToolGrantRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class McpChatToolGrantService {

    private final McpChatToolGrantRepository repository;

    public boolean isGranted(McpInvocationContext context, String toolKey) {
        if (context == null || context.channel() != McpInvocationChannel.CHAT_AGENT
                || context.requesterUserId() == null || StringUtils.isBlank(context.chatId())
                || StringUtils.isBlank(toolKey)) {
            return false;
        }
        return repository.existsById(new McpChatToolGrantId(
                context.chatId(), context.requesterUserId(), toolKey));
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void grant(McpToolInvocation invocation, Integer grantedBy) {
        repository.upsert(
                invocation.getChatId(),
                invocation.getRequesterUserId(),
                invocation.getToolKey(),
                grantedBy,
                invocation.getRequestId()
        );
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void revokeChat(String chatId, Integer requesterUserId) {
        if (StringUtils.isBlank(chatId) || requesterUserId == null) {
            return;
        }
        repository.deleteByChat(chatId, requesterUserId);
    }
}
