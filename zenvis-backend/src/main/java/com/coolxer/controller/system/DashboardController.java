package com.coolxer.controller.system;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.service.system.DashboardService;
import com.coolxer.utils.CommonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板管理
 */
@Tag(name = "看板管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/dashboard")
public class DashboardController extends BaseController {

    @Autowired
    private DashboardService dashboardService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody DashboardDto dashboardDto) {

        try {
            if (dashboardService.create(dashboardDto) != null) {
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
            dashboardService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            dashboardService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @RequestBody DashboardDto dashboardDto) {
        try {
            if (dashboardService.update(id, dashboardDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{ids}/bulk-update"})
    public ResponseWrap<?> bulkUpdate(@PathVariable("ids") Long[] ids, @RequestBody DashboardDto dashboardDto) {
        try {
            if (dashboardService.bulkUpdate(Arrays.asList(ids), dashboardDto)) {
                return ResponseWrap.success("修改成功");
            }
            return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(DashboardSearchDto dashboardSearchDto) {
        try {
            PageRowsVo<DashboardVo> pageDataVo = dashboardService.getPageList(dashboardSearchDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<DashboardVo> query(@PathVariable("id") Long id) {
        try {
            DashboardVo dashboardVo = dashboardService.info(id);
            if (dashboardVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(dashboardVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 获取类型列表
     */
    @GetMapping("/type/list")
    public ResponseWrap<?> listType() {
        try {
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            result.put("options", CommonUtil.createOptions(
                    Arrays.asList(DashboardType.values()),
                    action -> action.getDescription(),
                    action -> action.name()
            ));
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 看板列表
     *
     * @return 结果
     */
    @PostMapping(value = "/list")
    @Operation(summary = "看板列表", description = "用户自定义看板列表")
    public ResponseWrap<List<DashboardVo>> list() {
        //返回数据
        return ResponseWrap.success(dashboardService.findAll());
    }

}
