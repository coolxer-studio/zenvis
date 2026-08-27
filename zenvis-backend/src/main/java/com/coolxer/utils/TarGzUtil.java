package com.coolxer.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

@Slf4j
public final class TarGzUtil {

    /**
     * 解压 .tar.gz 文件到指定目录
     *
     * @param tarGzFile 源 .tar.gz 文件
     * @param destDir   目标目录（不存在则自动创建）
     * @throws IOException IO异常
     */
    public static void decompressTarGz(Path tarGzFile, Path destDir) throws IOException {
        if (!Files.isRegularFile(tarGzFile)) {
            throw new FileNotFoundException("File not found: " + tarGzFile);
        }

        Path normalizedDestDir = destDir.toAbsolutePath().normalize();
        // 确保目标目录存在
        Files.createDirectories(normalizedDestDir);

        try (InputStream fi = Files.newInputStream(tarGzFile);
             BufferedInputStream bi = new BufferedInputStream(fi);
             GZIPInputStream gzi = new GZIPInputStream(bi);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                validateEntry(entry);
                String entryName = entry.getName();
                Path targetPath = normalizedDestDir.resolve(entryName).normalize();

                if (!targetPath.startsWith(normalizedDestDir)) {
                    log.warn("targetPath not start with: {},{}", targetPath, normalizedDestDir);
                    throw new IOException("Bad entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    // 确保父目录存在
                    Files.createDirectories(targetPath.getParent());
                    // 拷贝文件并保留时间戳
                    Files.copy(tis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(targetPath, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));
                }
            }
        }
    }

    /**
     * 校验 tar.gz 中所有条目的路径安全性和格式可读性。
     */
    public static void validateTarGz(Path tarGzFile) throws IOException {
        if (!Files.isRegularFile(tarGzFile)) {
            throw new FileNotFoundException("File not found: " + tarGzFile);
        }
        try (InputStream fi = Files.newInputStream(tarGzFile);
             BufferedInputStream bi = new BufferedInputStream(fi);
             GZIPInputStream gzi = new GZIPInputStream(bi);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                validateEntry(entry);
            }
        }
    }

    /**
     * 将目录 sourceDir 打包成 tar.gz 文件 tarGzFile
     *
     * @param sourceDir 待压缩目录
     * @param tarGzFile 输出 tar.gz 文件（不存在则自动创建父目录）
     * @throws IOException IO 异常
     */
    public static void compressDirToTarGz(Path sourceDir, Path tarGzFile) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Not a directory: " + sourceDir);
        }
        if (Files.notExists(tarGzFile.getParent())) {
            Files.createDirectories(tarGzFile.getParent());
        }
        Path normalizedSourceDir = sourceDir.toAbsolutePath().normalize();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(
                        Files.newOutputStream(tarGzFile)))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            Files.walk(normalizedSourceDir).forEach(p -> {
                try {
                    Path relativePath = normalizedSourceDir.relativize(p);
                    if (relativePath.startsWith("..") || relativePath.isAbsolute()) {
                        log.warn("Skip illegal path during compress: {}", p);
                        return;
                    }
                    TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), relativePath.toString().replace('\\', '/'));
                    entry.setMode(0777);
                    tarOut.putArchiveEntry(entry);
                    if (Files.isRegularFile(p)) {
                        Files.copy(p, tarOut);
                    }
                    tarOut.closeArchiveEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            tarOut.finish();
        }
    }

    /**
     * 从 tar.gz 中读取根目录的文件并返回字符串
     *
     * @param tarGz    tar.gz 文件路径
     * @param fileName 读取的文件名
     * @return 文件的内容；若不存在则返回 null
     * @throws IOException 读取异常
     */
    public static String readRootFile(Path tarGz, String fileName) throws IOException {
        ByteArrayOutputStream outputStream = readFile(tarGz, fileName);
        if (outputStream != null) {
            return outputStream.toString(StandardCharsets.UTF_8);
        }
        return null; // 未找到
    }

    /**
     * 从 tar.gz 中读取文件并以 Base64 编码返回结果
     *
     * @param tarGz    tar.gz 文件路径
     * @param fileName 读取的文件名
     * @return 文件内容的 Base64 编码字符串；若不存在则返回 null
     * @throws IOException 读取异常
     */
    public static String readBase64File(Path tarGz, String fileName) throws IOException {
        ByteArrayOutputStream outputStream = readFile(tarGz, fileName);
        if (outputStream != null) {
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
        return null; // 未找到
    }

    private static ByteArrayOutputStream readFile(Path tarGz, String fileName) throws IOException {
        if (!Files.exists(tarGz)) {
            throw new FileNotFoundException(tarGz.toString());
        }
        validateEntryName(fileName);

        try (InputStream fi = Files.newInputStream(tarGz);
             BufferedInputStream bi = new BufferedInputStream(fi);
             GZIPInputStream gzi = new GZIPInputStream(bi);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                validateEntry(entry);
                // 只匹配根目录的 index.json
                if (!entry.isDirectory() && fileName.equals(entry.getName())) {
                    // 一次性读入内存
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = tis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos;
                }
            }
        }
        return null; // 未找到
    }

    private static void validateEntry(TarArchiveEntry entry) throws IOException {
        validateEntryName(entry.getName());
        if (entry.isSymbolicLink() || entry.isLink()) {
            throw new IOException("Link entry is not allowed: " + entry.getName());
        }
    }

    private static void validateEntryName(String entryName) throws IOException {
        if (entryName == null || entryName.isBlank()) {
            throw new IOException("Empty tar entry name");
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.contains("\0")) {
            throw new IOException("Bad entry: " + entryName);
        }
        while (normalizedName.endsWith("/")) {
            normalizedName = normalizedName.substring(0, normalizedName.length() - 1);
        }
        if (normalizedName.isBlank()) {
            throw new IOException("Bad entry: " + entryName);
        }
        String[] parts = normalizedName.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("Bad entry: " + entryName);
            }
        }
    }

}
