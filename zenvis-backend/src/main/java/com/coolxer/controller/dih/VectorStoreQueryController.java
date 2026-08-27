package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.dto.PageDto;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.service.dih.rag.RagDocumentManagementService;
import com.coolxer.service.dih.rag.RagDocumentManagementService.RagDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件文档 RAG 管理接口。
 */
@RestController
@RequestMapping("/api/v1/dih/vectorstore")
public class VectorStoreQueryController {

    @Autowired
    private RagDocumentManagementService ragDocumentManagementService;

    @Value("${app.ai.vectorstore.management.enabled:false}")
    private boolean vectorStoreManagementEnabled;

    @GetMapping("/documents")
    public ResponseWrap<List<RagDocumentVo>> getAllDocuments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "source", required = false) String source) {
        ensureManagementEnabled();
        return ResponseWrap.success(ragDocumentManagementService.listDocuments(keyword, source).stream()
                .map(RagDocumentVo::from)
                .toList());
    }

    @GetMapping("/documents/list")
    public ResponseWrap<PageRowsVo<RagDocumentVo>> listDocuments(PageDto pageDto,
                                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                                 @RequestParam(value = "source", required = false) String source) {
        ensureManagementEnabled();
        List<RagDocumentVo> documents = ragDocumentManagementService.listDocuments(keyword, source).stream()
                .map(RagDocumentVo::from)
                .toList();
        return ResponseWrap.success(page(documents, pageDto));
    }

    @GetMapping("/document/{documentId}")
    public ResponseWrap<RagDocumentVo> getDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        return ResponseWrap.success(RagDocumentVo.from(ragDocumentManagementService.getDocumentById(documentId)));
    }

    @DeleteMapping("/document/{documentId}")
    public ResponseWrap<SingleValueVo> deleteDocumentById(@PathVariable String documentId) {
        ensureManagementEnabled();
        boolean success = ragDocumentManagementService.deleteDocumentById(documentId);
        if (success) {
            return ResponseWrap.success(new SingleValueVo("文档删除成功: " + documentId));
        }
        return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "文档删除失败: " + documentId);
    }

    @DeleteMapping("/documents")
    public ResponseWrap<SingleValueVo> deleteDocumentsByIds(@RequestParam List<String> documentIds) {
        ensureManagementEnabled();
        boolean success = ragDocumentManagementService.deleteDocumentsByIds(documentIds);
        if (success) {
            return ResponseWrap.success(new SingleValueVo("文档删除成功，共删除 " + documentIds.size() + " 个文档"));
        }
        return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "文档删除失败");
    }

    @PostMapping("/search")
    public ResponseWrap<List<RagDocumentVo>> similaritySearch(@RequestParam("query") String query,
                                                              @RequestParam(defaultValue = "5") int topK,
                                                              @RequestParam(value = "source", required = false) String source) {
        ensureManagementEnabled();
        return ResponseWrap.success(ragDocumentManagementService.searchDocuments(query, topK, source).stream()
                .map(RagDocumentVo::from)
                .toList());
    }

    @GetMapping("/search")
    public ResponseWrap<PageRowsVo<RagDocumentVo>> similaritySearchForPage(PageDto pageDto,
                                                                           @RequestParam("query") String query,
                                                                           @RequestParam(defaultValue = "5") int topK,
                                                                           @RequestParam(value = "source", required = false) String source) {
        ensureManagementEnabled();
        List<RagDocumentVo> documents = ragDocumentManagementService.searchDocuments(query, topK, source).stream()
                .map(RagDocumentVo::from)
                .toList();
        return ResponseWrap.success(page(documents, pageDto));
    }

    private void ensureManagementEnabled() {
        if (!vectorStoreManagementEnabled) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "RAG文档管理接口未启用");
        }
    }

    private PageRowsVo<RagDocumentVo> page(List<RagDocumentVo> documents, PageDto pageDto) {
        int page = Math.max(pageDto.getPage(), 1);
        int perPage = Math.max(pageDto.getPerPage(), 1);
        int fromIndex = Math.min((page - 1) * perPage, documents.size());
        int toIndex = Math.min(fromIndex + perPage, documents.size());
        return new PageRowsVo<>(documents.subList(fromIndex, toIndex), documents.size());
    }

    public record RagDocumentVo(String id, String text, Object metadata, String source) {
        private static RagDocumentVo from(RagDocument document) {
            if (document == null) {
                return null;
            }
            return new RagDocumentVo(document.id(), document.text(), document.metadata(), document.source());
        }
    }
}
