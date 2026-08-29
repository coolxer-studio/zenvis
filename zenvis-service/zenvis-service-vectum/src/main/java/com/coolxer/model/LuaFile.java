package com.coolxer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Lua文件实体类
 * <p>
 * 表示一个Lua脚本文件，包含文件名和内容
 * </p>
 */
@Data
@AllArgsConstructor
public class LuaFile {

    /**
     * 文件名
     */
    private String fileName;
    /**
     * 文件内容
     */
    private String context;

}