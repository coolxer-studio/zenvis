package com.coolxer.model.dih.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatActionDecisionDto {
    @NotBlank(message = "会话ID不能为空")
    private String chatId;

    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    @NotBlank(message = "片段ID不能为空")
    private String partId;

    @NotBlank(message = "决策不能为空")
    private String decision;
}
