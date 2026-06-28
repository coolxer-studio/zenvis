package com.coolxer.service.dih.agent;

import com.coolxer.configuration.ai.AiEmbeddingProperties;
import com.coolxer.model.policy.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.agent.nl2sql.connector.bo.ColumnInfoBO;
import com.coolxer.service.dih.agent.nl2sql.connector.bo.TableInfoBO;
import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import com.coolxer.service.dih.agent.nl2sql.service.base.BaseVectorStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("redisVectorManagementService")
public class RedisVectorManagementService extends BaseVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(RedisVectorManagementService.class);

    // Embedding API 单次请求文本数量限制
    private static final int BATCH_SIZE = 25;

    @Autowired(required = false)
    @Qualifier("redisVectorStoreForAgent")
    private RedisVectorStore redisVectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiEmbeddingProperties embeddingProperties;

    @Override
    protected EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    @Override
    public boolean isEmbeddingEnabled() {
        return embeddingProperties.isEnabled();
    }

    @Override
    public List<Document> searchWithVectorType(SearchRequest searchRequestDTO) {
        if (skipEmbeddingOperation("searchWithVectorType")) {
            return List.of();
        }

        log.debug("Searching with vectorType: {}, query: {}, topK: {}", searchRequestDTO.getVectorType(),
                searchRequestDTO.getQuery(), searchRequestDTO.getTopK());

        if (searchRequestDTO.getTopK() <= 0) {
            searchRequestDTO.setTopK(5);
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("vectorType", searchRequestDTO.getVectorType()).build();

        List<Document> results = redisVectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(searchRequestDTO.getQuery())
                .topK(searchRequestDTO.getTopK())
                .filterExpression(expression)
                .build());

        log.info("Search completed. Found {} documents for vectorType: {}", results.size(),
                searchRequestDTO.getVectorType());
        return results;
    }

    @Override
    public List<Document> searchWithFilter(SearchRequest searchRequestDTO) {
        if (skipEmbeddingOperation("searchWithFilter")) {
            return List.of();
        }

        log.debug("Searching with custom filter: vectorType={}, query={}, topK={}", searchRequestDTO.getVectorType(),
                searchRequestDTO.getQuery(), searchRequestDTO.getTopK());

        // 这里需要根据实际情况解析 filterFormatted 字段，转换为 FilterExpressionBuilder 的表达式
        // 后续考虑如何实现，先设计成与前面一样的
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("vectorType", searchRequestDTO.getVectorType()).build();

        List<Document> results = redisVectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(searchRequestDTO.getQuery())
                .topK(searchRequestDTO.getTopK())
                .filterExpression(expression)
                .build());

        if (results == null) {
            results = new ArrayList<>();
        }

        log.info("Search with filter completed. Found {} documents", results.size());
        return results;
    }


    @Override
    public List<Document> searchTableByNameAndVectorType(SearchRequest searchRequestDTO) {
        if (skipEmbeddingOperation("searchTableByNameAndVectorType")) {
            return List.of();
        }

        log.debug("Searching table by name and vectorType: name={}, vectorType={}, topK={}", searchRequestDTO.getName(),
                searchRequestDTO.getVectorType(), searchRequestDTO.getTopK());

        if (searchRequestDTO.getTopK() <= 0) {
            searchRequestDTO.setTopK(5);
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("vectorType", searchRequestDTO.getVectorType()).build();

        List<Document> results = redisVectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(searchRequestDTO.getName())
                .topK(searchRequestDTO.getTopK())
                .filterExpression(expression)
                .build());

        if (results == null) {
            results = new ArrayList<>();
        }

        log.info("Search by name completed. Found {} documents for name: {}", results.size(),
                searchRequestDTO.getName());
        return results;
    }

    /**
     * TODO NOTE: 测试使用，暂不考虑其他，通过接口进行初始化进去
     * <p>
     * 初始化数据库DDL 到redis vector store
     * 项目应从配置文件中获取然后解析并加入redis vector store
     */
    public void schema() {
        if (skipEmbeddingOperation("schema")) {
            return;
        }

        List<TableInfoBO> tableInfoBOS = Lists.newArrayList();
        List<ColumnInfoBO> columnInfoBOS = Lists.newArrayList();
        readSchemaInfoFromCfg(tableInfoBOS, columnInfoBOS);
        log.info("Loaded tables and columns from configuration, table: {}, column: {}", tableInfoBOS, columnInfoBOS);

        // 幂等操作，删除已有的document
        List<String> documentIdsToDelete = Lists.newArrayList();
        tableInfoBOS.forEach(t -> {
            documentIdsToDelete.add(t.getName());
        });
        columnInfoBOS.forEach(c -> {
            documentIdsToDelete.add(c.getTableName() + ":" + c.getName());
        });
        deleteDocumentsByIds(documentIdsToDelete);

        List<Document> tableDocuments = tableInfoBOS.stream().map(this::convertTableToDocument).collect(Collectors.toList());
        addDocumentsInBatches(tableDocuments);

        tableInfoBOS.forEach(tableInfoBO -> {
            List<ColumnInfoBO> relatedColumns = columnInfoBOS.stream()
                    .filter(columnInfoBO -> columnInfoBO.getTableName().equals(tableInfoBO.getName()))
                    .toList();
            List<Document> columnDocuments = relatedColumns.stream()
                    .map(c -> this.convertToDocument(tableInfoBO, c))
                    .collect(Collectors.toList());
            addDocumentsInBatches(columnDocuments);
        });
    }

    /**
     * 分批添加文档到向量存储
     *
     * @param documents 文档列表
     */
    private void addDocumentsInBatches(List<Document> documents) {
        if (skipEmbeddingOperation("addDocumentsInBatches")) {
            return;
        }

        // 将文档列表分批处理，每批不超过BATCH_SIZE个
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, endIndex);
            redisVectorStore.add(batch);
        }
    }

    private void readSchemaInfoFromCfg(List<TableInfoBO> tableInfoBOS, List<ColumnInfoBO> columnInfoBOS) {
        List<ConfigVo> configFileTree = configService.getConfigFileTree("meta");

        // 递归处理所有配置文件的内容
        List<ConfigFileVo> fileCfgVos = Lists.newArrayList();
        if (configFileTree != null && !configFileTree.isEmpty()) {
            for (ConfigVo configVo : configFileTree) {
                fileCfgVos.addAll(processConfigVoRecursively(configVo));
            }
        }

        // TODO heyjd 暂时按照结构写死，后续考虑扩展
        for (ConfigFileVo fileCfgVo : fileCfgVos) {
            if (StringUtils.isBlank(fileCfgVo.getContent())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(fileCfgVo.content);
                // 解析table dto 信息
                if (root.has("entity")) {
                    JsonNode entityJsonNode = root.get("entity");
                    if (entityJsonNode.isArray()) {
                        for (JsonNode entityNode : entityJsonNode) {

                            TableInfoBO.TableInfoBOBuilder builder = TableInfoBO.builder()
                                    .name(Optional.ofNullable(entityNode.get("table_name")).map(JsonNode::asText).orElse(""))
                                    .schema("zenvis")
                                    .description(Optional.ofNullable(entityNode.get("description")).map(JsonNode::asText).orElse(""));

                            tableInfoBOS.add(builder.build());
                        }
                    }
                }

                // 解析出column 信息
                if (root.has("attribute")) {
                    JsonNode entityJsonNode = root.get("attribute");
                    if (entityJsonNode.isArray()) {
                        for (JsonNode entityNode : entityJsonNode) {

                            ColumnInfoBO.ColumnInfoBOBuilder builder = ColumnInfoBO.builder()
                                    .name(Optional.ofNullable(entityNode.get("column_name")).map(JsonNode::asText).orElse(""))
                                    .description(Optional.ofNullable(entityNode.get("description")).map(JsonNode::asText).orElse(""))
                                    .type(Optional.ofNullable(entityNode.get("column_type")).map(JsonNode::asText).orElse(""))
                                    .tableName(Optional.ofNullable(entityNode.get("entity")).map(JsonNode::asText).orElse(""));

                            columnInfoBOS.add(builder.build());
                        }
                    }
                }

            } catch (JsonProcessingException e) {
                log.warn("Failed to parse JSON: {}", fileCfgVo.getConfigVo(), e);
            }
        }
    }

    /**
     * 递归处理ConfigVo节点，读取文件内容并创建Document对象
     *
     * @param configVo 配置节点
     * @return Document列表
     */
    private List<ConfigFileVo> processConfigVoRecursively(ConfigVo configVo) {
        List<ConfigFileVo> res = Lists.newArrayList();

        if (configVo == null) {
            return res;
        }

        // 如果是文件（不是目录），读取文件内容并返回
        if (Boolean.FALSE.equals(configVo.getIsDir()) && configVo.getPath() != null) {
            try {
                String content = Files.readString(Paths.get(configVo.getPath()));
                ConfigFileVo c = new ConfigFileVo()
                        .setConfigVo(configVo)
                        .setContent(content);
                res.add(c);
            } catch (IOException e) {
                log.warn("Failed to read file: {}", configVo.getPath(), e);
            }
        }

        // 递归处理子节点
        if (configVo.getNodes() != null && !configVo.getNodes().isEmpty()) {
            for (ConfigVo child : configVo.getNodes()) {
                res.addAll(processConfigVoRecursively(child));
            }
        }

        return res;
    }

    public Document convertToDocument(TableInfoBO tableInfoBO, ColumnInfoBO columnInfoBO) {
        String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
        Map<String, Object> metadata = Map.of("name", columnInfoBO.getName(),
                "tableName", tableInfoBO.getName(),
                "description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""),
                "type", columnInfoBO.getType(),
                "primary", columnInfoBO.isPrimary(),
                "notnull", columnInfoBO.isNotnull(),
                "vectorType", "column",
                "source", "clickhouse");
        if (columnInfoBO.getSamples() != null) {
            metadata.put("samples", columnInfoBO.getSamples());
        }
        String documentId = tableInfoBO.getName() + ":" + columnInfoBO.getName();
        return new Document(documentId, text, metadata);
    }

    public Document convertTableToDocument(TableInfoBO tableInfoBO) {
        String text = Optional.ofNullable(tableInfoBO.getDescription()).orElse(tableInfoBO.getName());
        Map<String, Object> metadata = Map.of("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""),
                "name", tableInfoBO.getName(),
                "description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""),
                "foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""),
                "primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKey()).orElse(""),
                "vectorType", "table",
                "source", "clickhouse");
        return new Document(tableInfoBO.getName(), text, metadata);
    }

    /**
     * 根据文档ID查询文档
     *
     * @param documentId 文档ID
     * @return 匹配的文档，未找到则返回null
     */
    public Document getDocumentById(String documentId) {
        return getAllDocuments().stream()
                .filter(doc -> documentId.equals(doc.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有文档
     *
     * @return 文档列表
     */
    public List<Document> getAllDocuments() {
        if (skipEmbeddingOperation("getAllDocuments")) {
            return List.of();
        }

        try {
            List<Document> results = redisVectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("*")
                            .topK(10000)
                            .build()
            );
            log.info("Retrieved {} documents from vector store", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error retrieving all documents", e);
            return List.of();
        }
    }

    private boolean skipEmbeddingOperation(String operation) {
        if (!embeddingProperties.isEnabled()) {
            log.info("Skip vector store operation {} because app.ai.embedding.enabled=false.", operation);
            return true;
        }
        if (redisVectorStore == null) {
            log.warn("Skip vector store operation {} because Redis vector store is not available.", operation);
            return true;
        }
        return false;
    }

    /**
     * 根据文档ID删除文档
     *
     * @param documentId 要删除的文档ID
     * @return 删除是否成功
     */
    public boolean deleteDocumentById(String documentId) {
        if (skipEmbeddingOperation("deleteDocumentById")) {
            return false;
        }
        try {
            redisVectorStore.delete(List.of(documentId));
            log.info("Successfully deleted document with ID: {}", documentId);
            return true;
        } catch (Exception e) {
            log.error("Error deleting document with ID: {}", documentId, e);
            return false;
        }
    }

    /**
     * 根据多个文档ID删除文档
     *
     * @param documentIds 要删除的文档ID列表
     * @return 删除是否成功
     */
    public boolean deleteDocumentsByIds(List<String> documentIds) {
        if (skipEmbeddingOperation("deleteDocumentsByIds")) {
            return false;
        }
        try {
            redisVectorStore.delete(documentIds);
            log.info("Successfully deleted {} documents", documentIds.size());
            return true;
        } catch (Exception e) {
            log.error("Error deleting {} documents", documentIds.size(), e);
            return false;
        }
    }

    @Data
    @Accessors(chain = true)
    static class ConfigFileVo {
        private ConfigVo configVo;
        private String content;
    }
}
