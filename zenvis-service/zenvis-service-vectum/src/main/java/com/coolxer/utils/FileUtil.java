package com.coolxer.utils;

import java.io.File;

/**
 * 文件工具类
 * <p>
 * 提供文件操作相关的工具方法
 * </p>
 */
public class FileUtil {

    /**
     * 递归删除目录及其所有内容
     * @param directory 要删除的目录
     * @return 是否成功
     */
    public static boolean deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }

}