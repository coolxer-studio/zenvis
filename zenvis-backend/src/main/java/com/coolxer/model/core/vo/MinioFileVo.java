package com.coolxer.model.core.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinioFileVo {

    /**
     * MinIO 中的对象名称
     */
    private String objectName;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 下载地址
     */
    private String url;
}
