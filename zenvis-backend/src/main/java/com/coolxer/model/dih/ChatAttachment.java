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
    private Integer parseProgress;
    private String parseMessage;
    private String downloadUrl;
    private Integer pageCount;
    private Integer sheetCount;
    private Integer slideCount;
    private Long rowCount;
    private Long textChars;
    private Integer chunkCount;
    private Boolean truncated;
    private String warning;
    private String message;
}
