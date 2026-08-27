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
public class McpTaskToolGrantId implements Serializable {

    @Column(name = "execution_id", nullable = false, length = 64)
    private String executionId;

    @Column(name = "tool_key", nullable = false, length = 255)
    private String toolKey;
}
