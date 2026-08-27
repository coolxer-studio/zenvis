package com.coolxer.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参
 */
@Data
public class LoginDto {

    /**
     * 登录邮箱
     */
    @NotBlank(message = "用户名不能为空")
    private String userName;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码
     */
    private String authCode;

}
