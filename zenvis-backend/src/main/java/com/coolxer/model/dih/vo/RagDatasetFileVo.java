package com.coolxer.model.dih.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG数据集文件视图对象
 */
@Data
@NoArgsConstructor
public class RagDatasetFileVo implements Serializable {

    private String name;
    private String objectName;
    private long size;
}
