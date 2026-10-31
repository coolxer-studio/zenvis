package com.coolxer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vector 进程单轮运行元数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunMetadata {

    private String runId;

    private String startedAt;
}
