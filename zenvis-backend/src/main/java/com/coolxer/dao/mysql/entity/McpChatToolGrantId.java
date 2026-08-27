package com.coolxer.dao.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class McpChatToolGrantId implements Serializable {

    @Column(name = "chat_id", nullable = false, length = 128)
    private String chatId;

    @Column(name = "requester_user_id", nullable = false)
    private Integer requesterUserId;

    @Column(name = "tool_key", nullable = false, length = 255)
    private String toolKey;
}
