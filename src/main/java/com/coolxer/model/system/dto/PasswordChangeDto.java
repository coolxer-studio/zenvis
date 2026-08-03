package com.coolxer.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码修改传输对象
 */
@Data
public class PasswordChangeDto {

    /**
     * 密码
     */
    @NotBlank(message = "新密码不能为空")
    private String password;
    /**
     * 旧密码
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

}
