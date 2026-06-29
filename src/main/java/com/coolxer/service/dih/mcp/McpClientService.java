package com.coolxer.service.dih.mcp;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
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

    boolean hasAvailableTools();

    String buildEnabledMcpPrompt();

    ToolCallbackProvider getToolCallbackProvider();

    List<McpSyncClient> getActiveClients();
}
