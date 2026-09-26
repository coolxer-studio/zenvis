package com.coolxer.model.core.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinioItemVo {

    /**
     * 名称（文件或目录名）
     */
    private String name;

    /**
     * 完整路径（MinIO objectName 或目录前缀）
     */
    private String path;

    /**
     * 类型：file / directory
     */
    private String type;

    /**
     * 文件大小（字节），目录为 null
     */
    private Long size;

    /**
     * 最后修改时间戳（毫秒），目录为 null
     */
    private Long lastModified;
}
