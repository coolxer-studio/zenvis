package com.coolxer.service.dih.mcp;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
import com.coolxer.dao.mysql.entity.User;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

public interface McpClientService {

    PageRowsVo<McpServerVo> getPageList(McpServerSearchDto searchDto);

    McpServerVo create(McpServerDto dto);

    Boolean update(Integer id, McpServerDto dto);

    void delete(Integer id);

    McpServerVo info(Integer id);

    McpServerVo setEnabled(Integer id, boolean enabled);

    McpServerVo refresh(Integer id);

    List<McpServerVo> refreshAll();

    List<McpToolVo> listTools(Integer serverId);

    Object callTool(McpToolCallDto callDto);

    default Object callTool(McpToolCallDto callDto, User user) {
        return callTool(callDto);
    }

    boolean hasAvailableTools();

    boolean hasAvailableTools(List<String> serverCodes);

    String buildEnabledMcpPrompt();

    String buildEnabledMcpPrompt(List<String> serverCodes);

    ToolCallbackProvider getToolCallbackProvider();

    ToolCallbackProvider getToolCallbackProvider(List<String> serverCodes);

    List<McpSyncClient> getActiveClients();

    List<McpSyncClient> getActiveClients(List<String> serverCodes);
}
