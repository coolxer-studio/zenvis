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

import com.coolxer.service.dih.agent.nl2sql.connector.config.DbConfig;
import com.coolxer.service.dih.agent.dto.schema.ColumnDTO;
import com.coolxer.service.dih.agent.dto.schema.SchemaDTO;
import com.coolxer.service.dih.agent.dto.schema.TableDTO;
import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import com.google.gson.Gson;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schema 服务基类
 */
public abstract class BaseSchemaService {

	protected final DbConfig dbConfig;

	protected final Gson gson;

	protected final BaseVectorStoreService vectorStoreService;

	public BaseSchemaService(DbConfig dbConfig, Gson gson, BaseVectorStoreService vectorStoreService) {
		this.dbConfig = dbConfig;
		this.gson = gson;
		this.vectorStoreService = vectorStoreService;
	}

	/**
	 * 添加表文档（抽象方法，子类实现）
	 */
	protected abstract void addTableDocument(List<Document> tableDocuments, String tableName, String vectorType);

	/**
	 * 添加列文档（抽象方法，子类实现）
	 */
	protected abstract void addColumnsDocument(Map<String, Document> weightedColumns, String columnName,
			String vectorType);

	/**
	 * 混合 RAG 检索
	 */
	public SchemaDTO mixRag(String query, List<String> keywords) {
		SchemaDTO schemaDTO = new SchemaDTO();
		extractDatabaseName(schemaDTO);
		buildSchemaFromDocuments(getColumnDocumentsByKeywords(keywords), getTableDocuments(query), schemaDTO);
		return schemaDTO;
	}

	/**
	 * Agent 混合 RAG 检索
	 */
	public SchemaDTO mixRagForAgent(String query, String agentId, List<String> keywords) {
		SchemaDTO schemaDTO = new SchemaDTO();
		extractDatabaseName(schemaDTO);
		buildSchemaFromDocuments(
				getColumnDocumentsByKeywordsForAgent(agentId, keywords),
				getTableDocumentsForAgent(query, agentId),
				schemaDTO
		);
		return schemaDTO;
	}

	/**
	 * 从文档构建 Schema
	 */
	public void buildSchemaFromDocuments(List<List<Document>> columnDocumentsByKeywords,
			List<Document> tableDocuments, SchemaDTO schemaDTO) {
		if (tableDocuments == null || tableDocuments.isEmpty()) {
			return;
		}

		Map<String, Document> weightedColumns = selectWeightedColumns(columnDocumentsByKeywords, 100);
		List<TableDTO> tableDTOList = buildTableListFromDocuments(tableDocuments);
		attachColumnsToTables(weightedColumns, tableDTOList);

		schemaDTO.setTable(tableDTOList);
		schemaDTO.setTableCount(tableDTOList.size());
	}

	/**
	 * 获取表文档
	 */
	public List<Document> getTableDocuments(String query) {
		return getTableDocuments(query, "table");
	}

	/**
	 * 获取表文档
	 */
	public List<Document> getTableDocuments(String query, String vectorType) {
		List<Document> tableDocuments = new ArrayList<>();
		addTableDocument(tableDocuments, query, vectorType);
		return tableDocuments;
	}

	/**
	 * Agent 获取表文档
	 */
	public List<Document> getTableDocumentsForAgent(String query, String agentId) {
		return vectorStoreService.filterDocumentsForAgent(getTableDocuments(query), agentId);
	}

	/**
	 * 按关键词获取列文档
	 */
	public List<List<Document>> getColumnDocumentsByKeywords(List<String> keywords) {
		return getColumnDocumentsByKeywords(keywords, "column");
	}

	/**
	 * 按关键词获取列文档
	 */
	public List<List<Document>> getColumnDocumentsByKeywords(List<String> keywords, String vectorType) {
		if (keywords == null || keywords.isEmpty()) {
			return new ArrayList<>();
		}
		return keywords.stream()
				.map(keyword -> {
					Map<String, Document> columnMap = new HashMap<>();
					addColumnsDocument(columnMap, keyword, vectorType);
					return new ArrayList<>(columnMap.values());
				})
				.collect(Collectors.toList());
	}

	/**
	 * Agent 按关键词获取列文档
	 */
	public List<List<Document>> getColumnDocumentsByKeywordsForAgent(String agentId, List<String> keywords) {
		List<List<Document>> documentsByKeywords = getColumnDocumentsByKeywords(keywords);
		if (documentsByKeywords == null || documentsByKeywords.isEmpty()) {
			return documentsByKeywords;
		}
		return documentsByKeywords.stream()
				.map(documents -> vectorStoreService.filterDocumentsForAgent(documents, agentId))
				.collect(Collectors.toList());
	}

	/**
	 * 提取数据库名称
	 */
	public void extractDatabaseName(SchemaDTO schemaDTO) {
		schemaDTO.setName(dbConfig.getSchema());
		schemaDTO.setDescription(dbConfig.getSchema());
	}

	/**
	 * 选择加权的列
	 */
	protected Map<String, Document> selectWeightedColumns(List<List<Document>> columnDocumentsByKeywords, int limit) {
		Map<String, Document> weightedColumns = new HashMap<>();
		if (columnDocumentsByKeywords == null) {
			return weightedColumns;
		}

		for (List<Document> docs : columnDocumentsByKeywords) {
			if (docs != null) {
				for (Document doc : docs) {
					String docId = doc.getId();
					if (docId != null && !weightedColumns.containsKey(docId)) {
						weightedColumns.put(docId, doc);
						if (weightedColumns.size() >= limit) {
							return weightedColumns;
						}
					}
				}
			}
		}
		return weightedColumns;
	}

	/**
	 * 从文档构建表列表
	 */
	protected List<TableDTO> buildTableListFromDocuments(List<Document> tableDocuments) {
		if (tableDocuments == null) {
			return new ArrayList<>();
		}

		return tableDocuments.stream()
				.map(doc -> {
					TableDTO tableDTO = new TableDTO();
					Map<String, Object> metadata = doc.getMetadata();
					tableDTO.setName((String) metadata.get("name"));
					tableDTO.setDescription((String) metadata.get("description"));
					return tableDTO;
				})
				.collect(Collectors.toList());
	}

	/**
	 * 处理列权重
	 */
	public void processColumnWeights(List<List<Document>> columnDocumentsByKeywords,
			List<Document> tableDocuments) {
		// 默认实现，子类可以重写
	}

	/**
	 * 提取外键关系
	 */
	protected Set<String> extractForeignKeyRelations(List<Document> tableDocuments) {
		Set<String> foreignKeys = new HashSet<>();
		if (tableDocuments != null) {
			for (Document doc : tableDocuments) {
				String fk = (String) doc.getMetadata().get("foreignKey");
				if (fk != null && !fk.isEmpty()) {
					foreignKeys.add(fk);
				}
			}
		}
		return foreignKeys;
	}

	/**
	 * 将列附加到表
	 */
	protected void attachColumnsToTables(Map<String, Document> weightedColumns, List<TableDTO> tableDTOList) {
		if (weightedColumns == null || tableDTOList == null) {
			return;
		}

		for (TableDTO tableDTO : tableDTOList) {
			List<ColumnDTO> columns = new ArrayList<>();
			for (Document doc : weightedColumns.values()) {
				String tableName = (String) doc.getMetadata().get("tableName");
				if (tableDTO.getName().equals(tableName)) {
					ColumnDTO columnDTO = new ColumnDTO();
					columnDTO.setName((String) doc.getMetadata().get("name"));
					columnDTO.setDescription((String) doc.getMetadata().get("description"));
					columnDTO.setType((String) doc.getMetadata().get("type"));
					columns.add(columnDTO);
				}
			}
			tableDTO.setColumn(columns);
		}
	}

	/**
	 * 获取表元数据
	 */
	protected Map<String, Object> getTableMetadata(String tableName) {
		return new HashMap<>();
	}

	/**
	 * 处理文档查询
	 */
	protected void handleDocumentQuery(List<Document> documents, String query, String vectorType,
			Function<String, SearchRequest> requestBuilder,
			Function<SearchRequest, List<Document>> searcher) {
		SearchRequest request = requestBuilder.apply(query);
		request.setVectorType(vectorType);
		List<Document> results = searcher.apply(request);
		if (results != null) {
			documents.addAll(results);
		}
	}

	/**
	 * 处理文档查询（Map版本）
	 */
	protected void handleDocumentQuery(Map<String, Document> documents, String query, String vectorType,
			Function<String, SearchRequest> requestBuilder,
			Function<SearchRequest, List<Document>> searcher) {
		SearchRequest request = requestBuilder.apply(query);
		request.setVectorType(vectorType);
		List<Document> results = searcher.apply(request);
		if (results != null) {
			for (Document doc : results) {
				String name = doc.getId();
				if (name != null) {
					documents.put(name, doc);
				}
			}
		}
	}

}
