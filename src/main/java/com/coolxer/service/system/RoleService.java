package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.RoleDto;
import com.coolxer.model.system.dto.RoleSearchDto;
import com.coolxer.model.system.vo.RoleVo;

import java.util.List;

/**
 * 角色接口
 */
public interface RoleService {


    /**
     * 查询全部列表
     *
     * @return 结果
     */
    List<RoleVo> findAll(User currentUser);

    /**
     * 创建角色
     *
     * @param roleDto 传输实体
     */
    Role create(RoleDto roleDto);

    /**
     * 修改角色
     *
     * @param id      角色id
     * @param roleDto 用户传输实体
     */
    Boolean update(Long id, RoleDto roleDto);

    /**
     * 删除角色
     *
     * @param id 角色id
     */
    void delete(Long id);

    /**
     * 批量删除
     */
    void deleteByIds(List<Long> ids);

    /**
     * 角色详情
     *
     * @param id 角色id
     * @return 结果
     */
    RoleVo info(Long id, User currentUser);

    /**
     * 获取角色列表
     *
     * @param roleSearchDto 搜索参数
     * @return 角色列表
     */
    PageRowsVo<RoleVo> getPageList(RoleSearchDto roleSearchDto, User currentUser);


}
