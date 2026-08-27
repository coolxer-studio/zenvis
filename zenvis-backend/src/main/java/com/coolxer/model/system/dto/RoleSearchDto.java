package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色搜索传输对象
 */
@Data
@NoArgsConstructor
public class RoleSearchDto extends SortPageDto {

    /**
     * 角色名
     */
    private String name;


}