package com.coolxer.model.system.vo;

import cn.hutool.core.lang.tree.Tree;
import lombok.Data;

import java.util.List;

/**
 * 登录
 */
@Data
public class LoginVo {

    /**
     * 权限列表
     */
    private List<Tree<String>> permission;

    /**
     * 用户信息
     */
    private UserVo user;

}
