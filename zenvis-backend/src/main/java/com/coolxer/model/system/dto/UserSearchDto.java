package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户搜索传输对象
 */
@Data
@NoArgsConstructor
public class UserSearchDto extends SortPageDto {

    /**
     * 用户名
     */
    private String name;

    /**
     * 登录邮箱
     */
    private String email;

}