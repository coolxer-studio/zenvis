package com.coolxer.controller.system;


import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.CommonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理
 */
@Tag(name = "菜单管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/menu")
public class MenuController extends BaseController {

    @Autowired
    private MenuService menuService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@Valid @RequestBody MenuDto menuDto) {

        try {
            if (menuService.create(menuDto) != null) {
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
            menuService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            menuService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @Valid @RequestBody MenuDto menuDto) {
        try {
            if (menuService.update(id, menuDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/update-order"})
    public ResponseWrap<?> updateOrder(@RequestBody MenuOrderRowDto menuOrderRowDto) {
        try {
            if (menuService.updateOrder(menuOrderRowDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{ids}/bulk-update"})
    public ResponseWrap<?> bulkUpdate(@PathVariable("ids") Long[] ids, @RequestBody MenuDto menuDto) {
        try {
            for (long id : ids) {
                menuService.update(id, menuDto);
            }
            return ResponseWrap.success("修改成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(MenuSearchDto menuSearchDto) {
        try {
            PageRowsVo<MenuVo> pageDataVo = menuService.getPageList(menuSearchDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<MenuVo> query(@PathVariable("id") Long id) {
        try {
            MenuVo menuVo = menuService.info(id);
            if (menuVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(menuVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 获取一级目录列表
     */
    @GetMapping("/parent-menu/list")
    public ResponseWrap<?> listParentMenu() {
        try {
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            result.put("options", CommonUtil.createOptions(
                    menuService.findAllParentMenu(),
                    item -> item.getName(),
                    item -> String.valueOf(item.getId())
            ));
            return ResponseWrap.success(result);
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
                    Arrays.asList(MenuType.values()),
                    action -> action.getDescription(),
                    action -> action.name()
            ));
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 获取级别列表
     */
    @GetMapping("/level/list")
    public ResponseWrap<?> listLevel() {
        try {
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            result.put("options", CommonUtil.createOptions(
                    Arrays.asList(MenuLevel.values()),
                    action -> action.getDescription(),
                    action -> action.name()
            ));
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }


}
