package com.coolxer.controller.system;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.PasswordChangeDto;
import com.coolxer.model.system.dto.UserDto;
import com.coolxer.model.system.dto.UserSearchDto;
import com.coolxer.model.system.vo.UserVo;
import com.coolxer.service.system.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理
 */
@Tag(name = "用户管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/user")
public class UserController extends BaseController {

    @Autowired
    private UserService userService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@Valid @RequestBody UserDto userDto) {

        try {
            if (userService.create(userDto) != null) {
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
            userService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @RequestBody UserDto userDto) {
        try {
            if (userService.update(id, userDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{ids}/bulk-update"})
    public ResponseWrap<?> bulkUpdate(@PathVariable("ids") Long[] ids, @RequestBody UserDto userDto) {
        try {
            for (long id : ids) {
                userService.update(id, userDto);
            }
            return ResponseWrap.success("修改成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(UserSearchDto userSearchDto) {
        try {
            PageRowsVo<UserVo> pageDataVo = userService.getPageList(userSearchDto, getSessionUser());
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<UserVo> query(@PathVariable("id") Long id) {
        try {
            UserVo userVo = userService.info(id, getSessionUser());
            if (userVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(userVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 用户修改密码
     *
     * @param passwordChangeDto 参数
     * @return 结果
     */
    @PostMapping(value = "/update-password")
    @Operation(summary = "修改用户密码", description = "修改用户密码")
    public ResponseWrap<Void> updatePassword(@Valid @RequestBody PasswordChangeDto passwordChangeDto) {

        User opt = getSessionUser();
        userService.checkPassword(passwordChangeDto, opt);
        UserDto userDto = new UserDto();
        userDto.setPassword(passwordChangeDto.getPassword());
        userDto.setId(opt.getId());
        userService.updatePassword(userDto);
        return ResponseWrap.success();
    }


}
