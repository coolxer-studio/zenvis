package com.coolxer.model.base.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileTreeNodeVo {
    private String label;          // 文件/目录名
    private String value;          // 相对根目录的完整路径
    private List<FileTreeNodeVo> children;   // 仅目录才有

    public FileTreeNodeVo(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
