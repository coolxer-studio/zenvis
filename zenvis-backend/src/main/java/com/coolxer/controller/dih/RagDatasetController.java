package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.dto.RagDatasetDto;
import com.coolxer.model.dih.dto.RagDatasetSearchDto;
import com.coolxer.model.dih.vo.RagDatasetFileVo;
import com.coolxer.model.dih.vo.RagDatasetVo;
import com.coolxer.service.dih.rag.RagDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG数据集管理接口
 */
@Tag(name = "RAG数据集管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/dih/vectorstore/datasets")
public class RagDatasetController {

    @Autowired
    private RagDatasetService ragDatasetService;

    @PostMapping("/add")
    @Operation(summary = "创建数据集", description = "创建RAG数据集，支持名称、来源、标签、描述")
    public ResponseWrap<?> add(@RequestBody RagDatasetDto dto) {
        try {
            return ResponseWrap.success(ragDatasetService.create(dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/update")
    @Operation(summary = "更新数据集", description = "更新RAG数据集基础信息")
    public ResponseWrap<?> update(@PathVariable("id") Integer id, @RequestBody RagDatasetDto dto) {
        try {
            return ResponseWrap.success(ragDatasetService.update(id, dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据集", description = "删除RAG数据集及其MinIO文件，若被知识库引用则拒绝删除")
    public ResponseWrap<?> delete(@PathVariable("id") Integer id) {
        try {
            ragDatasetService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/list")
    @Operation(summary = "数据集分页列表", description = "分页查询RAG数据集")
    public ResponseWrap<PageRowsVo<RagDatasetVo>> list(RagDatasetSearchDto searchDto) {
        try {
            return ResponseWrap.success(ragDatasetService.getPageList(searchDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/options")
    @Operation(summary = "数据集选项", description = "获取全部数据集选项，用于知识库关联选择")
    public ResponseWrap<Map<String, Object>> options() {
        try {
            Map<String, Object> result = Map.of("options", ragDatasetService.options());
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "数据集详情", description = "查询RAG数据集详情")
    public ResponseWrap<RagDatasetVo> info(@PathVariable("id") Integer id) {
        try {
            return ResponseWrap.success(ragDatasetService.info(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/upload")
    @Operation(summary = "上传数据集文件", description = "上传markdown文件到数据集MinIO目录；relativePath 用于目录上传时保留层级")
    public ResponseWrap<?> upload(@PathVariable("id") Integer id,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "relativePath", required = false) String relativePath) {
        try {
            return ResponseWrap.success(ragDatasetService.uploadFile(id, file, relativePath));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/files")
    @Operation(summary = "数据集文件列表", description = "列出数据集MinIO目录下的文件")
    public ResponseWrap<Map<String, Object>> listFiles(@PathVariable("id") Integer id) {
        try {
            List<RagDatasetFileVo> files = ragDatasetService.listFiles(id);
            return ResponseWrap.success(Map.of("items", files, "total", files.size()));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/files/content")
    @Operation(summary = "读取数据集文件内容", description = "读取数据集MinIO目录下指定markdown文件的内容")
    public ResponseWrap<Map<String, String>> readFileContent(@PathVariable("id") Integer id,
                                                             @RequestParam("objectName") String objectName) {
        try {
            return ResponseWrap.success(Map.of("content", ragDatasetService.readFileContent(id, objectName)));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/{id}/files")
    @Operation(summary = "删除数据集文件", description = "删除数据集MinIO目录下的指定文件")
    public ResponseWrap<?> deleteFile(@PathVariable("id") Integer id,
                                      @RequestParam("objectName") String objectName) {
        try {
            return ResponseWrap.success(ragDatasetService.deleteFile(id, objectName));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }
}
