package com.coolxer.commons.constant;

import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.User;
import org.apache.commons.lang3.BooleanUtils;

/**
 * 系统内置账号和角色常量。
 */
public final class SystemBuiltInConstants {

    private SystemBuiltInConstants() {
    }

    public static final String SUPER_ADMIN_EMAIL = "super@admin.com";

    public static final String SUPER_ADMIN_NAME = "超级管理员";

    public static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";

    public static boolean isSuperAdmin(User user) {
        return user != null && BooleanUtils.isTrue(user.getIsSuperAdmin());
    }

    public static boolean isSuperAdmin(Role role) {
        return role != null && BooleanUtils.isTrue(role.getIsSuperAdmin());
    }
}
