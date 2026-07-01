/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable applicable warranties or limitations
 * under the License.
 */
package com.coolxer.service.dih.agent.nl2sql.service;

import com.coolxer.service.dih.logging.LlmLogHelper;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 服务封装类
 */
public class LlmService {

	private static final Logger log = LoggerFactory.getLogger(LlmService.class);

	private static final ThreadLocal<String> CURRENT_MODEL = new ThreadLocal<>();

	private static final ThreadLocal<ToolCallbackProvider> CURRENT_TOOL_CALLBACK_PROVIDER = new ThreadLocal<>();

	private static final ThreadLocal<String> CURRENT_TOOL_SYSTEM_PROMPT = new ThreadLocal<>();

	private final ChatClient chatClient;

	public LlmService(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	/**
	 * 设置当前线程使用的模型，为 null 时清除
	 */
	public void setModel(String model) {
		if (StringUtils.hasText(model)) {
			CURRENT_MODEL.set(model);
		}
		else {
			CURRENT_MODEL.remove();
		}
	}

	/**
	 * 清除当前线程的模型设置
	 */
	public void clearModel() {
		CURRENT_MODEL.remove();
	}

	public void setMcpToolContext(McpToolContext context) {
		if (context != null && context.hasTools()) {
			CURRENT_TOOL_CALLBACK_PROVIDER.set(context.toolCallbackProvider());
			CURRENT_TOOL_SYSTEM_PROMPT.set(context.systemPrompt());
		}
		else {
			clearMcpToolContext();
		}
	}

	public void clearMcpToolContext() {
		CURRENT_TOOL_CALLBACK_PROVIDER.remove();
		CURRENT_TOOL_SYSTEM_PROMPT.remove();
	}

	/**
	 * 获取当前线程的模型，为 null 时使用默认模型
	 */
	private String currentModel() {
		return CURRENT_MODEL.get();
	}

	/**
	 * 构建同步调用的运行时模型选项（禁用流式相关参数）
	 */
	private OpenAiChatOptions buildSyncModelOptions() {
		String model = currentModel();
		if (model == null) {
			return null;
		}
		return OpenAiChatOptions.builder()
				.model(model)
				.build();
	}

	/**
	 * 构建流式调用的运行时模型选项
	 */
	private OpenAiChatOptions buildStreamModelOptions() {
		String model = currentModel();
		if (model == null) {
			return null;
		}
		return OpenAiChatOptions.builder()
				.model(model)
				.build();
	}

	/**
	 * 同步调用 LLM
	 */
	public String call(String prompt) {
		String scene = "LlmService.call";
		String requestId = LlmLogHelper.newRequestId();
		long startedAtNanos = System.nanoTime();
		OpenAiChatOptions options = buildSyncModelOptions();
		var spec = chatClient.prompt().user(prompt);
		if (StringUtils.hasText(currentToolSystemPrompt())) {
			spec = spec.system(currentToolSystemPrompt());
		}
		if (options != null) {
			spec = spec.options(options);
		}
		if (currentToolCallbackProvider() != null) {
			spec = spec.toolCallbacks(currentToolCallbackProvider());
		}
		LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(null, prompt, false));
		try {
			String response = spec.call().content();
			LlmLogHelper.logResponse(log, requestId, scene, response, startedAtNanos);
			return response;
		}
		catch (RuntimeException e) {
			LlmLogHelper.logError(log, requestId, scene, null, startedAtNanos, e);
			throw e;
		}
	}

	/**
	 * 带系统提示的同步调用
	 */
	public String callWithSystemPrompt(String systemPrompt, String userPrompt) {
		String scene = "LlmService.callWithSystemPrompt";
		String requestId = LlmLogHelper.newRequestId();
		long startedAtNanos = System.nanoTime();
		OpenAiChatOptions options = buildSyncModelOptions();
		var spec = chatClient.prompt().system(appendToolSystemPrompt(systemPrompt)).user(userPrompt);
		if (options != null) {
			spec = spec.options(options);
		}
		if (currentToolCallbackProvider() != null) {
			spec = spec.toolCallbacks(currentToolCallbackProvider());
		}
		LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(systemPrompt, userPrompt, false));
		try {
			String response = spec.call().content();
			LlmLogHelper.logResponse(log, requestId, scene, response, startedAtNanos);
			return response;
		}
		catch (RuntimeException e) {
			LlmLogHelper.logError(log, requestId, scene, null, startedAtNanos, e);
			throw e;
		}
	}

	/**
	 * 流式调用 LLM
	 */
	public Flux<ChatResponse> streamCall(String prompt) {
		String scene = "LlmService.streamCall";
		String requestId = LlmLogHelper.newRequestId();
		long startedAtNanos = System.nanoTime();
		OpenAiChatOptions options = buildStreamModelOptions();
		var spec = chatClient.prompt().user(prompt);
		if (StringUtils.hasText(currentToolSystemPrompt())) {
			spec = spec.system(currentToolSystemPrompt());
		}
		if (options != null) {
			spec = spec.options(options);
		}
		if (currentToolCallbackProvider() != null) {
			spec = spec.toolCallbacks(currentToolCallbackProvider());
		}
		LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(null, prompt, true));
		return LlmLogHelper.logChatResponseStream(log, requestId, scene, spec.stream().chatResponse(), startedAtNanos);
	}

	/**
	 * 带系统提示的流式调用
	 */
	public Flux<ChatResponse> streamCallWithSystemPrompt(String systemPrompt, String userPrompt) {
		String scene = "LlmService.streamCallWithSystemPrompt";
		String requestId = LlmLogHelper.newRequestId();
		long startedAtNanos = System.nanoTime();
		OpenAiChatOptions options = buildStreamModelOptions();
		var spec = chatClient.prompt().system(appendToolSystemPrompt(systemPrompt)).user(userPrompt);
		if (options != null) {
			spec = spec.options(options);
		}
		if (currentToolCallbackProvider() != null) {
			spec = spec.toolCallbacks(currentToolCallbackProvider());
		}
		LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(systemPrompt, userPrompt, true));
		return LlmLogHelper.logChatResponseStream(log, requestId, scene, spec.stream().chatResponse(), startedAtNanos);
	}

	private ToolCallbackProvider currentToolCallbackProvider() {
		return CURRENT_TOOL_CALLBACK_PROVIDER.get();
	}

	private String currentToolSystemPrompt() {
		return CURRENT_TOOL_SYSTEM_PROMPT.get();
	}

	private String appendToolSystemPrompt(String systemPrompt) {
		String toolSystemPrompt = currentToolSystemPrompt();
		if (!StringUtils.hasText(toolSystemPrompt)) {
			return systemPrompt;
		}
		if (!StringUtils.hasText(systemPrompt)) {
			return toolSystemPrompt;
		}
		return systemPrompt + "\n\n" + toolSystemPrompt;
	}

	private Map<String, Object> buildLogRequest(String systemPrompt, String userPrompt, boolean stream) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", currentModel());
		request.put("stream", stream);
		request.put("system_prompt", systemPrompt);
		request.put("user_prompt", userPrompt);
		return request;
	}

}
