package com.coolxer.service.dih.mcp;

import com.coolxer.configuration.ai.AiEmbeddingProperties;
import com.coolxer.service.dih.rag.VectorStoreDelegate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 为绑定了 RAG 知识库的 Skill 动态构建 rag_search 工具回调。
 * 知识库范围在服务端按 Skill 绑定关系固定，模型无法越权检索其它知识库。
 */
@Slf4j
@Service
public class SkillRagToolFactory {

    public static final String TOOL_NAME = "rag_search";

    private static final int DEFAULT_TOP_K = 6;

    private static final int MAX_TOP_K = 10;

    /**
     * knowledge_base_id 不是 Redis 索引的可过滤字段，需放大召回后内存过滤。
     */
    private static final int FETCH_MULTIPLIER = 5;

    private static final int MAX_RESULT_CHARS = 6000;

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "query":{"type":"string","description":"检索问题或关键词"},
            "top_k":{"type":"integer","description":"返回结果数量，默认 6，最大 10"}},
            "required":["query"]}""";

    private final VectorStoreDelegate vectorStoreDelegate;

    private final AiEmbeddingProperties embeddingProperties;

    private final ObjectMapper objectMapper;

    public SkillRagToolFactory(VectorStoreDelegate vectorStoreDelegate,
                               AiEmbeddingProperties embeddingProperties,
                               ObjectMapper objectMapper) {
        this.vectorStoreDelegate = vectorStoreDelegate;
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建绑定指定知识库集合的 rag_search 工具回调。
     */
    public ToolCallback create(List<Integer> knowledgeBaseIds) {
        Set<Integer> kbIds = new LinkedHashSet<>(knowledgeBaseIds);
        ToolDefinition definition = ToolDefinition.builder()
                .name(TOOL_NAME)
                .description("检索当前 Skill 绑定的知识库（knowledge_base_id: " + kbIds + "），"
                        + "返回与用户问题最相关的知识片段；当回答需要知识库资料时调用")
                .inputSchema(INPUT_SCHEMA)
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return search(toolInput, kbIds);
            }
        };
    }

    private String search(String toolInput, Set<Integer> kbIds) {
        try {
            JsonNode params = objectMapper.readTree(toolInput == null || toolInput.isBlank() ? "{}" : toolInput);
            String query = params.path("query").asText("").trim();
            if (query.isEmpty()) {
                return "参数 query 不能为空";
            }
            int topK = params.path("top_k").isInt()
                    ? Math.max(1, Math.min(params.path("top_k").asInt(), MAX_TOP_K))
                    : DEFAULT_TOP_K;
            if (!embeddingProperties.isEnabled()) {
                return "知识库检索不可用：embedding 未启用";
            }
            List<Document> documents = vectorStoreDelegate.getVectorStore("redis")
                    .similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(topK * FETCH_MULTIPLIER)
                            .build());
            if (documents == null || documents.isEmpty()) {
                return "未检索到相关知识库内容";
            }
            List<Document> matched = documents.stream()
                    .filter(Objects::nonNull)
                    .filter(document -> kbIds.contains(toInteger(document.getMetadata().get("knowledge_base_id"))))
                    .limit(topK)
                    .toList();
            if (matched.isEmpty()) {
                return "未检索到相关知识库内容";
            }
            StringBuilder result = new StringBuilder();
            int index = 1;
            for (Document document : matched) {
                String text = document.getText() == null ? "" : document.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                Object kbName = document.getMetadata().getOrDefault("knowledge_base_name", "unknown");
                Object fileName = document.getMetadata().getOrDefault("file_name", "unknown");
                String chunk = "\n\n[资料" + index++ + "][知识库=" + kbName + ", 文件=" + fileName + "]\n" + text;
                if (result.length() + chunk.length() > MAX_RESULT_CHARS) {
                    break;
                }
                result.append(chunk);
            }
            return result.length() == 0 ? "未检索到相关知识库内容" : result.toString().trim();
        } catch (Exception e) {
            log.warn("Skill RAG 检索失败, kbIds={}", kbIds, e);
            return "知识库检索失败: " + e.getMessage();
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
