package com.coolxer.model.dih;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天附件元信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachment {
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String kind;
    private String fileUrl;
    private String parseStatus;
    private String message;
}
