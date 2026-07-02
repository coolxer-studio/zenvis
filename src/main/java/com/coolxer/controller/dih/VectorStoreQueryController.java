package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import com.coolxer.service.dih.agent.RedisVectorManagementService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储管理接口（内部测试使用）
 */
@RestController
@RequestMapping("/api/v1/dih/vectorstore")
public class VectorStoreQueryController {

    @Autowired
    private RedisVectorManagementService redisVectorManagementService;

    @Value("${app.ai.vectorstore.management.enabled:false}")
    private boolean vectorStoreManagementEnabled;

    @GetMapping("/documents")
    public List<Document> getAllDocuments() {
        ensureManagementEnabled();
        return redisVectorManagementService.getAllDocuments();
    }

    @GetMapping("/document/{documentId}")
    public Document getDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        return redisVectorManagementService.getDocumentById(documentId);
    }

    @DeleteMapping("/document/{documentId}")
    public String deleteDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        boolean success = redisVectorManagementService.deleteDocumentById(documentId);
        if (success) {
            return "文档删除成功: " + documentId;
        } else {
            return "文档删除失败: " + documentId;
        }
    }

    @DeleteMapping("/documents")
    public String deleteDocumentsByIds(@RequestParam List<String> documentIds) {
        ensureManagementEnabled();
        boolean success = redisVectorManagementService.deleteDocumentsByIds(documentIds);
        if (success) {
            return "文档删除成功，共删除 " + documentIds.size() + " 个文档";
        } else {
            return "文档删除失败";
        }
    }

    @PostMapping("/build-schema")
    public String buildSchema() {
        ensureManagementEnabled();
        if (!redisVectorManagementService.isEmbeddingEnabled()) {
            return "embedding is disabled, skip build schema";
        }
        redisVectorManagementService.schema();
        return "success";
    }

    @PostMapping("/search")
    public List<Document> similaritySearch(@RequestParam("query") String query,
                                   @RequestParam(defaultValue = "5") int topK,
                                   @RequestParam("vectorType") String vectorType) {
        ensureManagementEnabled();
        SearchRequest req = new SearchRequest();
        req.setQuery(query);
        req.setTopK(topK);
        req.setVectorType(vectorType);
        List<Document> docs = redisVectorManagementService.searchWithVectorType(req);
        return docs;
    }

    /**
     * 删除inspectAgent使用的所有RAG数据（table/column/evidence）
     */
    @DeleteMapping("/agent-documents")
    public String deleteAllAgentDocuments() {
        ensureManagementEnabled();
        List<Document> allDocs = redisVectorManagementService.getAllDocuments();
        if (allDocs.isEmpty()) {
            return "当前没有需要删除的文档";
        }
        List<String> documentIds = allDocs.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
        boolean success = redisVectorManagementService.deleteDocumentsByIds(documentIds);
        if (success) {
            return "删除成功，共删除 " + documentIds.size() + " 个文档";
        } else {
            return "删除失败";
        }
    }

    private void ensureManagementEnabled() {
        if (!vectorStoreManagementEnabled) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "VectorStore管理接口未启用");
        }
    }
}
