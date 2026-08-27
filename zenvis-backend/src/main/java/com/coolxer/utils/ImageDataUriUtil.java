package com.coolxer.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageDataUriUtil {

    /**
     * 判断字符串是否是 Data URL 格式
     *
     * @param input 输入字符串
     * @return 如果是 Data URL 格式返回 true，否则返回 false
     */
    public static boolean isDataUrl(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Data URL 格式的正则表达式
        String dataUrlRegex = "^data:([a-zA-Z0-9-+/.]+)?(;base64)?,([a-zA-Z0-9+/=]+)?$";

        return input.matches(dataUrlRegex);
    }

    /**
     * 将图片文件转换为Data URI
     *
     * @param fileName
     * @param base64String
     * @return
     * @throws IOException
     */
    public static String toDataUri(String fileName, String base64String) throws IOException {
        // 根据文件扩展名猜测 MIME-Type（简单实现，可自行扩展）
        String mime = guessMime(fileName);
        return "data:" + mime + ";base64," + base64String;
    }

    /**
     * 将文件转换为 data:image/xxx;base64,xxxx 的完整 Data URI。
     *
     * @param file 任意存在的文件
     * @return Data URI 字符串
     * @throws IOException 文件不存在或读取出错时抛出
     */
    public static String toDataUri(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("文件必须存在且为普通文件");
        }

        // 1. 读取文件内容
        byte[] bytes = Files.readAllBytes(file.toPath());

        // 2. 根据文件扩展名猜测 MIME-Type（简单实现，可自行扩展）
        String mime = guessMime(file.getName());

        // 3. Base64 编码并拼成 Data URI
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mime + ";base64," + base64;
    }

    /**
     * 根据文件名后缀返回对应的 MIME-Type（仅示例，可按需补全）。
     */
    private static String guessMime(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        if (name.endsWith(".ico")) return "image/x-icon";
        // 默认按二进制流处理
        return "application/octet-stream";
    }

    /**
     * 将 Data URI 形式的 Base64 字符串解码并保存成图片文件。
     * 支持两种格式：
     * 1) data:image/png;base64,xxxxxx
     * 2) 纯 Base64 字符串（此时必须手动指定扩展名）
     *
     * @param dataUri        含 Data URI 的完整字符串，或纯 Base64 字符串
     * @param destPath       目标保存文件路径
     * @param fileNamePrefix 不带扩展名的文件名前缀
     * @throws IOException 写入失败或格式不正确时抛出
     *                     <p>
     *                     return 保存后的文件名
     */
    public static String dataUriToFile(String dataUri, Path destPath, String fileNamePrefix) throws IOException {
        if (dataUri == null || dataUri.trim().isEmpty()) {
            throw new IllegalArgumentException("dataUri 不能为空");
        }
        String base64;
        String mime = null;
        // 正则匹配 Data URI 格式
        Pattern pattern = Pattern.compile("^data:([^;]+);base64,(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(dataUri.trim());

        if (matcher.matches()) {
            mime = matcher.group(1);
            base64 = matcher.group(2);
        } else {
            // 纯 base64 字符串
            base64 = dataUri.trim();
        }
        // 解码
        byte[] bytes = Base64.getDecoder().decode(base64);
        // 如果文件后缀为空，根据 mime 自动补
        String ext = "icon";
        if (mime != null) {
            ext = mime.substring(mime.lastIndexOf("/") + 1); // 如 image/png -> png
        }
        String fileName = fileNamePrefix + "." + ext;
        File destFile = destPath.resolve(fileName).toFile();
        // 写文件
        Files.write(destFile.toPath(), bytes, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return fileName;
    }
}
