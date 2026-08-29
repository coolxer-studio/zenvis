package com.coolxer.plugin.servicer;

import com.coolxer.plugin.controller.PageRowsVo;
import com.coolxer.plugin.controller.UserDto;
import com.coolxer.plugin.controller.UserSearchDto;
import com.coolxer.plugin.controller.UserVo;

public interface UserService {

    boolean add(UserDto userDto);

    PageRowsVo<UserVo> getPageList(UserSearchDto userSearchDto);
}
