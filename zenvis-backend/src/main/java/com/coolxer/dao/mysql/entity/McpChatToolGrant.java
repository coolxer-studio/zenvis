package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Entity
@Accessors(chain = true)
@Table(name = MysqlFinalTableName.T_AI_MCP_CHAT_TOOL_GRANT)
public class McpChatToolGrant {

    @EmbeddedId
    private McpChatToolGrantId id;

    @Column(name = "granted_by", nullable = false)
    private Integer grantedBy;

    @Column(name = "source_request_id", nullable = false, length = 64)
    private String sourceRequestId;

    @Column(name = "create_time", nullable = false)
    private Date createTime;

    @Column(name = "update_time", nullable = false)
    private Date updateTime;
}
