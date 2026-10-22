package com.coolxer.service.dih.agentrun;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并 MCP 工具与 Run 内部控制工具，并以工具名去重。
 */
public class CompositeToolCallbackProvider implements ToolCallbackProvider {

    private final List<ToolCallbackProvider> delegates;

    public CompositeToolCallbackProvider(ToolCallbackProvider... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        Map<String, ToolCallback> unique = new LinkedHashMap<>();
        for (ToolCallbackProvider delegate : delegates) {
            if (delegate == null || delegate.getToolCallbacks() == null) {
                continue;
            }
            for (ToolCallback callback : delegate.getToolCallbacks()) {
                if (callback == null || callback.getToolDefinition() == null) {
                    continue;
                }
                unique.putIfAbsent(callback.getToolDefinition().name(), callback);
            }
        }
        return new ArrayList<>(unique.values()).toArray(ToolCallback[]::new);
    }
}
