package com.coolxer.controller.system;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
import com.coolxer.model.system.dto.PluginUpgradeDto;
import com.coolxer.model.system.vo.PluginVo;
import com.coolxer.service.system.PluginService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 插件管理
 */
@Tag(name = "插件管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/plugin")
public class PluginController extends BaseController {

    @Autowired
    private PluginService pluginService;

    @PostMapping({"/upload"})
    public ResponseWrap<PluginVo> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            PluginVo pluginVo = pluginService.uploadFile(file);
            return ResponseWrap.success(pluginVo);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/icon/base64"})
    public ResponseWrap<SingleValueVo> base64Icon(@RequestParam("file") MultipartFile file) {
        try {
            SingleValueVo singleValueVo = new SingleValueVo(pluginService.base64Icon(file));
            return ResponseWrap.success(singleValueVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody PluginDto pluginDto) {

        try {
            if (pluginService.isPackageExist(pluginDto.getPackageName())) {
                return ResponseWrap.fail(ResultCodeEnum.PLUGIN_IS_EXIST);
            }
            if (pluginService.create(pluginDto) != null) {
                return ResponseWrap.success("创建成功");
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            pluginService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            pluginService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(PluginSearchDto pluginSearchDto) {
        try {
            PageRowsVo<PluginVo> pageDataVo = pluginService.getPageList(pluginSearchDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<PluginVo> query(@PathVariable("id") Long id) {
        try {
            PluginVo pluginVo = pluginService.info(id);
            if (pluginVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(pluginVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/readme"})
    public ResponseWrap<SingleValueVo> readme(@PathVariable("id") Long id) {
        try {
            String readmeMarkdownText = pluginService.readme(id);
            if (readmeMarkdownText == null) {
                return ResponseWrap.fail();
            } else {
                SingleValueVo singleValueVo = new SingleValueVo(readmeMarkdownText);
                return ResponseWrap.success(singleValueVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/doc/tree"})
    public ResponseWrap<List<FileTreeNodeVo>> docTree(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.docTree(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/doc/view"})
    public ResponseWrap<SingleValueVo> docView(@PathVariable("id") Long id, @RequestParam(value = "file") String file) {
        try {
            String docMarkdownText = pluginService.readDocFile(id, file);
            if (docMarkdownText == null) {
                return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
            }
            docMarkdownText = docMarkdownText.replaceAll("%", "%25");
            SingleValueVo singleValueVo = new SingleValueVo(docMarkdownText);
            return ResponseWrap.success(singleValueVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @RequestMapping({"/{id}/export"})
    public void export(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
            pluginService.export(id, response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<StreamingResponseBody> handleLog(@PathVariable("id") Long id) {
        StreamingResponseBody stream = out -> {
            while (true) {
                String logInfo = pluginService.getLogs(id);
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
        return new ResponseEntity(stream, HttpStatus.OK);
    }

    @PostMapping({"/{id}/install"})
    public ResponseWrap<PluginVo> install(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.install(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/upgrade"})
    public ResponseWrap<PluginVo> upgrade(@PathVariable("id") Long id,
                                          @RequestBody PluginUpgradeDto upgradeDto) {
        try {
            return ResponseWrap.success(pluginService.upgrade(id, upgradeDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/upgrade/recover"})
    public ResponseWrap<PluginVo> recoverUpgrade(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.recoverUpgrade(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/uninstall"})
    public ResponseWrap<PluginVo> uninstall(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.uninstall(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

}
