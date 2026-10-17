package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.dto.RagKnowledgeBaseDto;
import com.coolxer.model.dih.dto.RagKnowledgeBaseSearchDto;
import com.coolxer.model.dih.vo.RagKnowledgeBaseVo;
import com.coolxer.service.dih.rag.RagKnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * RAG知识库管理接口
 */
@Tag(name = "RAG知识库管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/dih/vectorstore/knowledge-bases")
public class RagKnowledgeBaseController {

    private static final long KB_LOG_STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    @Autowired
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Value("${app.ai.vectorstore.management.enabled:false}")
    private boolean vectorStoreManagementEnabled;

    @PostMapping("/add")
    @Operation(summary = "创建知识库", description = "创建RAG知识库并关联数据集")
    public ResponseWrap<?> add(@RequestBody RagKnowledgeBaseDto dto) {
        ensureManagementEnabled();
        try {
            return ResponseWrap.success(ragKnowledgeBaseService.create(dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/update")
    @Operation(summary = "更新知识库", description = "更新RAG知识库基础信息和关联数据集")
    public ResponseWrap<?> update(@PathVariable("id") Integer id, @RequestBody RagKnowledgeBaseDto dto) {
        ensureManagementEnabled();
        try {
            return ResponseWrap.success(ragKnowledgeBaseService.update(id, dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "删除RAG知识库并清理Redis向量数据")
    public ResponseWrap<?> delete(@PathVariable("id") Integer id) {
        ensureManagementEnabled();
        try {
            ragKnowledgeBaseService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/list")
    @Operation(summary = "知识库分页列表", description = "分页查询RAG知识库")
    public ResponseWrap<PageRowsVo<RagKnowledgeBaseVo>> list(RagKnowledgeBaseSearchDto searchDto) {
        ensureManagementEnabled();
        try {
            return ResponseWrap.success(ragKnowledgeBaseService.getPageList(searchDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "知识库详情", description = "查询RAG知识库详情")
    public ResponseWrap<RagKnowledgeBaseVo> info(@PathVariable("id") Integer id) {
        ensureManagementEnabled();
        try {
            return ResponseWrap.success(ragKnowledgeBaseService.info(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/build")
    @Operation(summary = "构建知识库", description = "异步触发知识库向量化构建，通过日志接口查看进度")
    public ResponseWrap<?> build(@PathVariable("id") Integer id) {
        ensureManagementEnabled();
        try {
            ragKnowledgeBaseService.build(id);
            return ResponseWrap.success("构建任务已提交");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "构建日志", description = "流式获取知识库构建日志")
    public ResponseEntity<StreamingResponseBody> handleLog(@PathVariable("id") Integer id,
                                                           HttpServletRequest request) {
        ensureManagementEnabled();
        StreamingResponseBody stream = out -> {
            request.getAsyncContext().setTimeout(KB_LOG_STREAM_TIMEOUT_MILLIS);
            while (true) {
                String logInfo = ragKnowledgeBaseService.getLogs(id);
                if (logInfo != null) {
                    out.write((logInfo + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                if (StringUtils.contains(logInfo, "完成......") || StringUtils.contains(logInfo, "失败......")) {
                    break;
                }
            }
            out.close();
        };
        return new ResponseEntity<>(stream, HttpStatus.OK);
    }

    private void ensureManagementEnabled() {
        if (!vectorStoreManagementEnabled) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "RAG文档管理接口未启用");
        }
    }
}
