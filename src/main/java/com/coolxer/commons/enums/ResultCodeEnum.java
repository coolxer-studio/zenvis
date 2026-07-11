package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 接口结果返回码枚举类
 */
@Getter
public enum ResultCodeEnum {

    /**
     * 成功
     */
    SUCCESS(0, "请求成功"),

    /**
     * 未知错误
     */
    UNKNOWN_ERROR(-1, "未知错误"),

    /**
     * 服务器内部错误
     */
    INNER_ERROR(1, "请求失败"),

    /**
     * 不支持的操作
     */
    NO_SUPPORTED(99, "不支持的操作！"),

    /**
     * 您没有权限进行此操作
     */
    NO_AUTHORITY(100, "您没有权限进行此操作！"),

    /**
     * 请重新登录
     */
    PLEASE_LOGIN(101, "请重新登录！"),

    /**
     * 验证码输入错误
     */
    ERROR_CODE(102, "验证码输入错误！"),

    /**
     * 验证码已失效
     */
    ERROR_CODE_INVALID(103, "验证码已失效！"),

    /**
     * 用户名或密码不正确
     */
    ERROR_EMAIL_OR_PASSWORD(104, "用户名或密码不正确！"),

    /**
     * 用户不存在
     */
    ERROR_USER_IS_NOT_EXIST(105, "用户不存在"),

    /**
     * 用户已过期
     */
    ERROR_USER_EXPIRED(107, "用户已过期！"),

    /**
     * 用户ID不能为空
     */
    ERROR_USER_ID_MUST_NOT_NULL(108, "用户ID不能为空！"),

    /**
     * 已存在，请勿重复创建
     */
    EMAIL_IS_EXIST(110, "邮箱已存在，请勿重复创建！"),

    /**
     * 请输入8~16位由大小写英文、数字、@!#$组成的密码
     */
    ERROR_PASSWORD_FORMAT(111, "请输入8~16位由大小写英文、数字、@!#$组成的密码！"),

    /**
     * 原密码错误
     */
    ERROR_PASSWORD(112, "原密码错误！"),

    /**
     * 新密码不能与旧密码相同
     */
    ERROR_NEW_PASSWORD_IS_SAME_AS_OLD(113, "新密码不能与旧密码相同!"),

    /**
     * 新密码不能与最近的三次密码相同
     */
    ERROR_PASSWORD_REPEAT_HISTORY(114, "新密码不能与最近的三次密码相同"),

    /**
     * 角色存在用户，不能删除角色
     */
    EXISTS_USER_ROLE(120, "删除失败，先删除用户再删除角色！"),

    /**
     * 角色名不能为空
     */
    ROLE_NAME_MUST_NOT_NULL(122, "角色名不能为空！"),

    /**
     * 权限不能为空
     */
    PERMISSION_MUST_NOT_NULL(124, "权限不能为空！"),

    /**
     * 角色ID不能为空
     */
    ROLE_ID_MUST_NOT_NULL(126, "角色ID不能为空！"),

    /**
     * 超级管理员用户不允许修改或删除
     */
    SUPER_ADMIN_USER_NOT_ALLOWED(127, "超级管理员用户不允许修改或删除！"),

    /**
     * 超级管理员角色不允许修改或删除
     */
    SUPER_ADMIN_ROLE_NOT_ALLOWED(134, "超级管理员角色不允许修改或删除！"),

    /**
     * 超级管理员角色不允许手动分配
     */
    SUPER_ADMIN_ROLE_ASSIGN_NOT_ALLOWED(137, "超级管理员角色不允许手动分配！"),

    /**
     * 无效的时间范围！
     */
    INVALID_TIME_RANGE(130, "无效的时间范围！"),

    /**
     * 无效的时间！
     */
    INVALID_TIME(131, "无效的时间！"),

    /**
     * 文件名无效
     */
    FILE_NAME_INVALID(128, "文件名无效"),

    /**
     * 文件写入失败
     */
    FILE_WRITE_FAIL(129, "文件写入失败！"),

    /**
     * 开始时间不能超过结束时间
     */
    START_TIME_CANNOT_EXCEED_END_TIME(132, "开始时间不能超过结束时间！"),

    /**
     * IP被锁定
     */
    IP_LOCKED(133, "IP被锁定！剩余时间："),

    /**
     * 开始时间不能为空
     */
    START_TIME_NOT_NULL(135, "开始时间不能为空！"),

    /**
     * 结束时间不能为空
     */
    END_TIME_NOT_NULL(136, "结束时间不能为空！"),

    /**
     * 邮箱不能为空
     */
    EMAIL_MUST_NOT_NULL(140, "邮箱不能为空"),

    /**
     * 用户名不能为空
     */
    USER_NAME_MUST_NOT_NULL(142, "用户名不能为空"),

    /**
     * 角色不能为空
     */
    ROLE_MUST_NOT_NULL(144, "角色不能为空"),

    /**
     * 密码不能为空
     */
    PASSWORD_MUST_NOT_NULL(146, "密码不能为空"),

    /**
     * 原密码不能为空
     */
    OLD_PASSWORD_MUST_NOT_NULL(147, "原密码不能为空"),

    /**
     * jackson解析失败
     */
    JACKSON_PROCESSING_EXCEPTION(150, "json解析失败"),

    /**
     * 推送任务名称不能为空
     */
    PUSH_TASK_NAME_NOT_NULL(160, "推送任务名称不能为空"),

    /**
     * 推送任务配置不能为空
     */
    PUSH_TASK_CONFIG_NOT_NULL(161, "推送任务配置不能为空"),


    DISPLAY_LIMIT_ERROR(170, "展示列表不能少于两个！"),

    DASHBOARD_ID_MUST_NOT_NULL(180, "看板ID不能为空！"),

    DASHBOARD_PARAMETER_MISS_ERROR(181, "看板参数不完整！"),

    DASHBOARD_IS_NOT_EXIST(182, "看板不存在"),

    ENTITY_IS_EMPTY(190, "无可用实体"),

    PLUGIN_IS_INSTALLED(201, "插件已经安装，当前操作不允许！"),

    PLUGIN_IS_UNINSTALL(202, "插件未安装，当前操作不允许！"),

    PLUGIN_IS_EXIST(203, "相同插件已经存在！"),

    PLUGIN_OPERATION_RUNNING(204, "插件操作正在执行中，请稍后再试！"),

    PLUGIN_PACKAGE_INVALID(205, "插件包不合法！"),


    FIELD_IS_EMPTY(301, "必填字段不能为空"),

    FIELD_NOT_CANDIDATE(302, "字段不在备选值范围");


    private final int code;
    private final String description;

    ResultCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }


}
