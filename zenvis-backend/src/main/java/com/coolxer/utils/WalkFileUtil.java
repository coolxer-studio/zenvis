package com.coolxer.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.core.util.ArrayUtil;
import com.coolxer.commons.constants.ConfigConstants;
import com.coolxer.model.config.vo.ConfigVo;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 文件遍历工具类
 */
public class WalkFileUtil {

    private WalkFileUtil() {
    }

    public static void walkFilesWithDir(
            File file, ConfigVo walkFileVo, Integer depth, Long id, Consumer<ConfigVo> consumer) {

        if (!file.exists()) {
            return;
        }

        if (file.isDirectory() && ArrayUtil.isEmpty(file.list())) {
            return;
        }
        // 不是config目录且为需要忽略的文件时需要跳过
        if (isIgnoredFile(file)) {
            return;
        }

        // 设置基本值
        setBasicValue(file, walkFileVo, depth, id);
        if (file.isDirectory()) {
            // 文件夹中的文件列表
            File[] subFiles = file.listFiles();
            // 如果是空文件夹则直接返回
            if (ArrayUtil.isEmpty(subFiles)) {
                return;
            }
            List<ConfigVo> subFileInfos = new ArrayList<>(subFiles.length);
            int count = 1;
            for (File sub : subFiles) {
                ConfigVo subInfo = new ConfigVo();
                walkFilesWithDir(sub, subInfo, depth + 1, id * 100 + count, consumer);
                if (!subInfo.isEmpty()) {
                    subFileInfos.add(subInfo);
                    count++;
                }
            }
            // 设置子文件信息
            walkFileVo.setNodes(subFileInfos);
        } else {
            if (Objects.nonNull(consumer)) {
                // 只对文件进行设置
                consumer.accept(walkFileVo);
            }
        }
    }

    private static void setBasicValue(File file, ConfigVo info, Integer depth, Long id) {
        info.setId(id)
                .setParentId(id / 100)
                .setIsDir(file.isDirectory())
                .setModifiable(!file.isDirectory())
                .setFileName(file.getName())
                .setDepth(depth)
                .setSize(file.isDirectory() ? ConfigConstants.EMPTY_FILE_SIZE : FileUtil.size(file))
                .setPath(file.getPath());
    }

    /**
     * 是否为需要忽略的文件
     *
     * @return 是否为需要忽略的文件
     */
    public static boolean isIgnoredFile(File file) {
        // 此处不能用file.exist()判断，如果是删除文件的操作话，文件不存在会导致直接忽略，如有需要请在外部判断exist
        if (file == null) {
            return true;
        }

        // 需要忽略的文件或文件夹
        boolean anyMatch = ConfigConstants.IGNORE_FILE_OR_DIR
                .stream()
                .anyMatch(path -> file.getName().contains(path));

        if (anyMatch) {
            return true;
        }

        // 以"."开头的文件也需要忽略
        return file.getName().startsWith(StrPool.DOT);

    }

    /**
     * 复制文件或目录
     *
     * @param source 源文件或目录路径
     * @param target 目标文件或目录路径
     * @throws IOException 如果发生 IO 异常
     */
    public static void copy(Path source, Path target) throws IOException {
        // 检查源路径是否存在
        if (!Files.exists(source)) {
            throw new IOException("Source path does not exist: " + source);
        }

        // 如果目标路径不存在，创建目标路径的父目录
        Files.createDirectories(target.getParent());

        // 如果源路径是文件，直接复制
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.isDirectory(source)) {
            // 如果源路径是目录，递归复制目录内容
            Files.createDirectories(target);
            Files.walk(source)
                    .forEach(sourcePath -> {
                        Path targetPath = target.resolve(source.relativize(sourcePath));
                        try {
                            if (Files.isRegularFile(sourcePath)) {
                                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                Files.createDirectories(targetPath);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } else {
            throw new IOException("Source path is neither a file nor a directory: " + source);
        }
    }

    /**
     * 删除文件或目录（包括其内容）
     *
     * @param path 要删除的文件或目录路径
     * @throws IOException 如果发生 IO 异常
     */
    public static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Path does not exist: " + path);
        }

        // 如果是文件，直接删除
        if (Files.isRegularFile(path)) {
            Files.delete(path);
        } else if (Files.isDirectory(path)) {
            // 如果是目录，递归删除
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            throw new IOException("Path is neither a file nor a directory: " + path);
        }
    }

    /**
     * 创建目录
     *
     * @param path 要创建的目录路径
     * @throws IOException 如果发生 IO 异常
     */
    public static void mkdir(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }


}
