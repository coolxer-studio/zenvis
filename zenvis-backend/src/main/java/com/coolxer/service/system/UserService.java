package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.PasswordChangeDto;
import com.coolxer.model.system.dto.UserDto;
import com.coolxer.model.system.dto.UserSearchDto;
import com.coolxer.model.system.vo.UserVo;


/**
 * 用户接口
 */
public interface UserService {

    /**
     * 创建用户
     *
     * @param userDto 传输实体
     */
    User create(UserDto userDto);

    /**
     * 修改用户
     *
     * @param id      用户id
     * @param userDto 用户传输实体
     */
    Boolean update(Long id, UserDto userDto);

    /**
     * 删除用户
     *
     * @param id 用户id
     */
    void delete(Long id);

    /**
     * 用户详情
     *
     * @param id 用户id
     * @return 结果
     */
    UserVo info(Long id, User currentUser);

    /**
     * 获取用户列表
     *
     * @param userSearchDto 搜索参数
     * @return 用户列表
     */
    PageRowsVo<UserVo> getPageList(UserSearchDto userSearchDto, User currentUser);

    /**
     * 修改密码校验
     *
     * @param passwordChangeDto 修改密码传输实体
     * @param user              当前用户信息
     */
    void checkPassword(PasswordChangeDto passwordChangeDto, User user);

    /**
     * 修改密码
     *
     * @param userDto 用户传输实体
     */
    void updatePassword(UserDto userDto);

}
