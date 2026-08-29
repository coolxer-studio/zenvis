package com.coolxer.plugin.controller;

import com.coolxer.plugin.servicer.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody UserDto userDto) {
        try {
            if (userService.add(userDto)) {
                return ResponseWrap.success("添加成功"+userDto.getName());
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(UserSearchDto userSearchDto) {
        try {
            PageRowsVo<UserVo> pageDataVo = userService.getPageList(userSearchDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
        }

    }
}
