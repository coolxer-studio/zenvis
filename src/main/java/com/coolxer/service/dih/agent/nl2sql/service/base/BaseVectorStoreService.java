/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coolxer.service.dih.agent.nl2sql.service.base;

import com.coolxer.service.dih.agent.nl2sql.connector.bo.ColumnInfoBO;
import com.coolxer.service.dih.agent.nl2sql.connector.bo.TableInfoBO;
import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储服务基类
 */
public abstract class BaseVectorStoreService {

	public BaseVectorStoreService() {
	}

	/**
	 * 获取嵌入模型
	 */
	protected abstract EmbeddingModel getEmbeddingModel();

	/**
	 * Embedding/RAG is disabled by default and enabled explicitly by configuration.
	 */
	public boolean isEmbeddingEnabled() {
		return true;
	}

	/**
	 * 获取嵌入向量（Double）
	 */
	public List<Double> embedDouble(String text) {
		if (!isEmbeddingEnabled()) {
			return new ArrayList<>();
		}
		float[] embedding = getEmbeddingModel().embed(text);
		return convertToDoubleList(embedding);
	}

	/**
	 * 获取嵌入向量（Float）
	 */
	public List<Float> embedFloat(String text) {
		if (!isEmbeddingEnabled()) {
			return new ArrayList<>();
		}
		float[] embedding = getEmbeddingModel().embed(text);
		return convertToFloatList(embedding);
	}

	/**
	 * 按向量类型搜索
	 */
	public abstract List<Document> searchWithVectorType(SearchRequest searchRequest);

	/**
	 * 按过滤条件搜索
	 */
	public abstract List<Document> searchWithFilter(SearchRequest searchRequest);

	/**
	 * 按名称和向量类型搜索表
	 */
	public List<Document> searchTableByNameAndVectorType(SearchRequest searchRequest) {
		// 子类可以重写此方法提供具体实现
		return new ArrayList<>();
	}

	/**
	 * 获取文档
	 */
	public List<Document> getDocuments(String query, String vectorType) {
		SearchRequest request = new SearchRequest();
		request.setQuery(query);
		request.setVectorType(vectorType);
		return searchWithVectorType(request);
	}

	/**
	 * 获取文档（Agent）
	 */
	public List<Document> getDocumentsForAgent(String query, String vectorType, String agentId) {
		return getDocuments(query, vectorType);
	}

	/**
	 * 转换为 Double 列表
	 */
	protected List<Double> convertToDoubleList(float[] arr) {
		List<Double> result = new ArrayList<>(arr.length);
		for (float v : arr) {
			result.add((double) v);
		}
		return result;
	}

	/**
	 * 转换为 Float 列表
	 */
	protected List<Float> convertToFloatList(float[] arr) {
		List<Float> result = new ArrayList<>(arr.length);
		for (float v : arr) {
			result.add(v);
		}
		return result;
	}

}
