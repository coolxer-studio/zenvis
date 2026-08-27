package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话搜索传输对象
 */
@Data
@NoArgsConstructor
public class ChatSessionSearchDto extends SortPageDto {

    /**
     * 标题
     */
    private String title;

    /**
     * 会话类型
     */
    private String type;

}
