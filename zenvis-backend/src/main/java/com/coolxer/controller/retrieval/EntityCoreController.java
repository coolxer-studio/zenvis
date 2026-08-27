package com.coolxer.controller.retrieval;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.EntityCsvService;
import com.coolxer.utils.CommonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体的通用接口-默认生成
 */
@Tag(name = "实体的通用接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/entity/{entity}/")
public class EntityCoreController extends BaseController {

    @Autowired
    private EntityCoreService entityCoreService;

    @Autowired
    private EntityCsvService entityCsvService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@PathVariable("entity") String entity, @RequestBody Map<String, Object> mapDto) {
        try {
            if (entityCoreService.add(entity, mapDto)) {
                return ResponseWrap.success("添加成功");
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        try {
            entityCoreService.delete(entity, id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("entity") String entity, @PathVariable("ids") List<String> ids) {
        try {
            entityCoreService.deleteALL(entity, ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("entity") String entity, @PathVariable("id") String id, @RequestBody Map<String, Object> mapDto) {
        try {
            if (entityCoreService.update(entity, id, mapDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{ids}/bulk_update"})
    public ResponseWrap<?> bulkUpdate(@PathVariable("entity") String entity, @PathVariable("ids") String[] ids, @RequestBody Map<String, Object> mapDto) {
        try {
            entityCoreService.updateALL(entity, Arrays.asList(ids), mapDto);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
        return ResponseWrap.success("修改成功");
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(@PathVariable("entity") String entity, @RequestParam Map<String, Object> mapDto) {
        try {
            PageRowsVo<Map<String, Object>> pageDataVo = entityCoreService.getPageList(entity, mapDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseWrap<?> importCsv(
            @PathVariable("entity") String entity,
            @RequestParam("file") MultipartFile file) {
        try {
            EntityCsvService.ImportResult result = entityCsvService.importCsv(entity, file);
            return ResponseWrap.success("成功导入 " + result.imported() + " 条记录", Map.of(
                    "value", result.filename(),
                    "filename", result.filename(),
                    "imported", result.imported()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseWrap.fail(400, e.getMessage());
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping(value = "/import-template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<?> importTemplate(@PathVariable("entity") String entity) {
        try {
            return csvResponse(entityCsvService.importTemplate(entity), entity + "-template.csv");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ResponseWrap.fail(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ResponseWrap.fail(e));
        }
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<?> exportCsv(
            @PathVariable("entity") String entity,
            @RequestParam Map<String, Object> mapDto) {
        try {
            EntityCsvService.ExportResult result = entityCsvService.exportCsv(entity, mapDto);
            return ResponseEntity.ok()
                    .header("Content-Disposition", attachmentFilename(entity + ".csv"))
                    .header("X-Exported-Rows", String.valueOf(result.exported()))
                    .header("X-Export-Truncated", String.valueOf(result.truncated()))
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(result.content());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ResponseWrap.fail(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ResponseWrap.fail(e));
        }
    }

    private ResponseEntity<byte[]> csvResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", attachmentFilename(filename))
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    private String attachmentFilename(String filename) {
        return "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<?> query(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        try {
            Map<String, Object> mapVo = entityCoreService.getOne(entity, id);
            if (mapVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(mapVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }


    /**
     * 获取状态列表
     */
    @GetMapping("/{attribute}/mapping")
    public ResponseWrap<?> mapping(@PathVariable("entity") String entity, @PathVariable("attribute") String attribute) {
        try {
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            Map<String, Object> attributeMapping = entityCoreService.getAttributeMapping(entity, attribute);
            if (attributeMapping != null) {
                List<Map.Entry<String, Object>> entryList = attributeMapping.entrySet().stream().toList();
                result.put("options", CommonUtil.createOptions(
                        entryList,
                        entry -> entry.getKey(),
                        entry -> entry.getValue().toString()
                ));
            }
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{attribute}/list"})
    public ResponseWrap<?> listAttributeValue(@PathVariable("entity") String entity, @PathVariable("attribute") String attribute) {
        try {
            List<String> attributeValues = entityCoreService.getDistinctAttributes(entity, attribute);
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            result.put("options", attributeValues.stream()
                    .map(item -> {
                        Map<String, String> option = new HashMap<>(2);
                        option.put("label", item);
                        option.put("value", item);
                        return option;
                    }).toList()
            );
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }


    @GetMapping({"/{attribute}/auto-complete"})
    public ResponseWrap<?> autoCompleteId(@PathVariable("entity") String entity, @PathVariable("attribute") String attribute, @RequestParam(value = "term", required = false) String term) {
        try {
            List<String> similarIds = entityCoreService.getSimilarAttributes(entity, attribute, term);
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            result.put("options", similarIds.stream()
                    .map(item -> {
                        Map<String, String> option = new HashMap<>(2);
                        option.put("label", item);
                        option.put("value", item);
                        return option;
                    }).toList()
            );
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

}
