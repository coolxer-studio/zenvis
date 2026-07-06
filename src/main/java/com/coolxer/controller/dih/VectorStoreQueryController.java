package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.dto.PageDto;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.service.dih.agent.RedisVectorManagementService;
import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public ResponseWrap<List<VectorStoreDocumentVo>> getAllDocuments() {
        ensureManagementEnabled();
        return ResponseWrap.success(redisVectorManagementService.getAllDocuments().stream()
                .map(VectorStoreDocumentVo::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/documents/list")
    public ResponseWrap<PageRowsVo<VectorStoreDocumentVo>> listDocuments(PageDto pageDto,
                                                                         @RequestParam(value = "keyword", required = false) String keyword,
                                                                         @RequestParam(value = "vectorType", required = false) String vectorType) {
        ensureManagementEnabled();
        List<VectorStoreDocumentVo> documents = redisVectorManagementService.getAllDocuments().stream()
                .map(VectorStoreDocumentVo::from)
                .filter(document -> matchVectorType(document, vectorType))
                .filter(document -> matchKeyword(document, keyword))
                .collect(Collectors.toList());
        return ResponseWrap.success(page(documents, pageDto));
    }

    @GetMapping("/document/{documentId}")
    public ResponseWrap<VectorStoreDocumentVo> getDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        return ResponseWrap.success(VectorStoreDocumentVo.from(redisVectorManagementService.getDocumentById(documentId)));
    }

    @DeleteMapping("/document/{documentId}")
    public ResponseWrap<SingleValueVo> deleteDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        boolean success = redisVectorManagementService.deleteDocumentById(documentId);
        if (success) {
            return ResponseWrap.success(new SingleValueVo("文档删除成功: " + documentId));
        } else {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "文档删除失败: " + documentId);
        }
    }

    @DeleteMapping("/documents")
    public ResponseWrap<SingleValueVo> deleteDocumentsByIds(@RequestParam List<String> documentIds) {
        ensureManagementEnabled();
        boolean success = redisVectorManagementService.deleteDocumentsByIds(documentIds);
        if (success) {
            return ResponseWrap.success(new SingleValueVo("文档删除成功，共删除 " + documentIds.size() + " 个文档"));
        } else {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "文档删除失败");
        }
    }

    @PostMapping("/build-schema")
    public ResponseWrap<SingleValueVo> buildSchema() {
        ensureManagementEnabled();
        if (!redisVectorManagementService.isEmbeddingEnabled()) {
            return ResponseWrap.success(new SingleValueVo("embedding is disabled, skip build schema"));
        }
        redisVectorManagementService.schema();
        return ResponseWrap.success(new SingleValueVo("success"));
    }

    @PostMapping("/search")
    public ResponseWrap<List<VectorStoreDocumentVo>> similaritySearch(@RequestParam("query") String query,
                                                                      @RequestParam(defaultValue = "5") int topK,
                                                                      @RequestParam("vectorType") String vectorType) {
        ensureManagementEnabled();
        SearchRequest req = new SearchRequest();
        req.setQuery(query);
        req.setTopK(topK);
        req.setVectorType(vectorType);
        List<Document> docs = redisVectorManagementService.searchWithVectorType(req);
        return ResponseWrap.success(docs.stream().map(VectorStoreDocumentVo::from).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    public ResponseWrap<PageRowsVo<VectorStoreDocumentVo>> similaritySearchForPage(PageDto pageDto,
                                                                                   @RequestParam("query") String query,
                                                                                   @RequestParam(defaultValue = "5") int topK,
                                                                                   @RequestParam("vectorType") String vectorType) {
        ensureManagementEnabled();
        SearchRequest req = new SearchRequest();
        req.setQuery(query);
        req.setTopK(topK);
        req.setVectorType(vectorType);
        List<VectorStoreDocumentVo> docs = redisVectorManagementService.searchWithVectorType(req).stream()
                .map(VectorStoreDocumentVo::from)
                .collect(Collectors.toList());
        return ResponseWrap.success(page(docs, pageDto));
    }

    /**
     * 删除inspectAgent使用的所有RAG数据（table/column/evidence）
     */
    @DeleteMapping("/agent-documents")
    public ResponseWrap<SingleValueVo> deleteAllAgentDocuments() {
        ensureManagementEnabled();
        List<Document> allDocs = redisVectorManagementService.getAllDocuments();
        if (allDocs.isEmpty()) {
            return ResponseWrap.success(new SingleValueVo("当前没有需要删除的文档"));
        }
        List<String> documentIds = allDocs.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
        boolean success = redisVectorManagementService.deleteDocumentsByIds(documentIds);
        if (success) {
            return ResponseWrap.success(new SingleValueVo("删除成功，共删除 " + documentIds.size() + " 个文档"));
        } else {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "删除失败");
        }
    }

    private void ensureManagementEnabled() {
        if (!vectorStoreManagementEnabled) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "VectorStore管理接口未启用");
        }
    }

    private PageRowsVo<VectorStoreDocumentVo> page(List<VectorStoreDocumentVo> documents, PageDto pageDto) {
        int page = Math.max(pageDto.getPage(), 1);
        int perPage = Math.max(pageDto.getPerPage(), 1);
        int fromIndex = Math.min((page - 1) * perPage, documents.size());
        int toIndex = Math.min(fromIndex + perPage, documents.size());
        return new PageRowsVo<>(documents.subList(fromIndex, toIndex), documents.size());
    }

    private boolean matchVectorType(VectorStoreDocumentVo document, String vectorType) {
        return vectorType == null || vectorType.isBlank()
                || vectorType.equalsIgnoreCase(document.vectorType());
    }

    private boolean matchKeyword(VectorStoreDocumentVo document, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(document.id(), normalizedKeyword)
                || containsIgnoreCase(document.text(), normalizedKeyword)
                || containsIgnoreCase(String.valueOf(document.metadata()), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    public record VectorStoreDocumentVo(String id,
                                        String text,
                                        Map<String, Object> metadata,
                                        String vectorType,
                                        String source,
                                        String schema,
                                        String tableName,
                                        String name,
                                        String description) {

        private static VectorStoreDocumentVo from(Document document) {
            if (document == null) {
                return null;
            }
            Map<String, Object> metadata = document.getMetadata();
            return new VectorStoreDocumentVo(
                    document.getId(),
                    document.getText(),
                    metadata,
                    metadataValue(metadata, "vectorType"),
                    metadataValue(metadata, "source"),
                    metadataValue(metadata, "schema"),
                    metadataValue(metadata, "tableName"),
                    metadataValue(metadata, "name"),
                    metadataValue(metadata, "description")
            );
        }

        private static String metadataValue(Map<String, Object> metadata, String key) {
            Object value = metadata == null ? null : metadata.get(key);
            return value == null ? "" : String.valueOf(value);
        }
    }
}
