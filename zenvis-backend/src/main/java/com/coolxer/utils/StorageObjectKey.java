package com.coolxer.utils;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 四域 MinIO 对象键构造与校验。所有业务对象键都必须经过本类生成。
 */
public final class StorageObjectKey {

    private static final int MAX_FILE_NAME_LENGTH = 180;

    private StorageObjectKey() {
    }

    public static String ragDataset(Integer datasetId) {
        return "rag/datasets/" + segment(String.valueOf(requirePositive(datasetId, "数据集ID")), "数据集ID");
    }

    public static String ragPluginDataset(String packageName, String datasetCode, String operationId) {
        return "rag/plugins/" + segment(packageName, "插件包名")
                + "/" + segment(datasetCode, "数据集编码")
                + "/" + segment(operationId, "操作标识");
    }

    public static String chat(Integer userId, String fileId, String fileName) {
        return "chat/" + segment(String.valueOf(requirePositive(userId, "用户ID")), "用户ID")
                + "/" + segment(fileId, "文件ID")
                + "/" + sanitizeFileName(fileName);
    }

    public static String chatUserFilePrefix(Integer userId, String fileId) {
        return "chat/" + segment(String.valueOf(requirePositive(userId, "用户ID")), "用户ID")
                + "/" + segment(fileId, "文件ID");
    }

    public static String chatDerivedPrefix(Integer userId, String fileId) {
        return "chat-derived/" + segment(String.valueOf(requirePositive(userId, "用户ID")), "用户ID")
                + "/" + segment(fileId, "文件ID");
    }

    public static String chatDerivedManifest(Integer userId, String fileId) {
        return chatDerivedPrefix(userId, fileId) + "/manifest.json";
    }

    public static String chatDerivedChunks(Integer userId, String fileId) {
        return chatDerivedPrefix(userId, fileId) + "/chunks.jsonl";
    }

    public static String pluginPackage(String packageName, String sha256) {
        return "packages/" + segment(packageName, "插件包名")
                + "/" + segment(sha256, "包摘要").toLowerCase(Locale.ROOT) + ".tar.gz";
    }

    public static String pluginCandidate(String packageName, String operationId) {
        return "upgrades/" + segment(packageName, "插件包名")
                + "/" + segment(operationId, "操作标识") + "/candidate.tar.gz";
    }

    public static String pluginSnapshot(String packageName, String operationId) {
        return "upgrades/" + segment(packageName, "插件包名")
                + "/" + segment(operationId, "操作标识") + "/snapshot.tar.gz";
    }

    public static String reportRevision(String documentId, long revision, String format) {
        return reportRevisionPrefix(documentId, revision)
                + "/content." + sourceFormatExtension(format);
    }

    public static String reportRevisionPrefix(String documentId, long revision) {
        return "documents/" + segment(documentId, "报表文档ID")
                + "/revisions/" + requirePositive(revision, "报表修订号");
    }

    public static String reportExport(String documentId,
                                      long revision,
                                      String format,
                                      String exportHash,
                                      String exporterVersion) {
        String targetFormat = format.toLowerCase(Locale.ROOT);
        if (!"docx".equals(targetFormat) && !"pdf".equals(targetFormat)) {
            throw new IllegalArgumentException("不支持的报表导出格式: " + format);
        }
        return reportRevisionPrefix(documentId, revision)
                + "/exports/" + segment(exporterVersion, "导出器版本")
                + "/" + targetFormat
                + "/" + segment(exportHash, "导出摘要").toLowerCase(Locale.ROOT)
                + "." + targetFormat;
    }

    public static String sanitizeFileName(String fileName) {
        String fallback = "attachment";
        String name = StringUtils.defaultIfBlank(fileName, fallback).replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return fallback;
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            String extension = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                extension = name.substring(dot);
            }
            int keep = Math.max(1, MAX_FILE_NAME_LENGTH - extension.length());
            name = name.substring(0, keep) + extension;
        }
        return name;
    }

    public static String segment(String value, String label) {
        if (StringUtils.isBlank(value) || value.length() > 300
                || "/".equals(value) || "\\".equals(value) || ".".equals(value) || "..".equals(value)
                || value.contains("/") || value.contains("\\") || value.contains("\0")) {
            throw new IllegalArgumentException(label + "不合法: " + value);
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(label + "不合法: " + value);
            }
        }
        return value;
    }

    public static void validate(String objectKey) {
        if (StringUtils.isBlank(objectKey) || objectKey.startsWith("/") || objectKey.endsWith("/")
                || objectKey.contains("\\") || objectKey.contains("\0") || objectKey.contains("..")) {
            throw new IllegalArgumentException("对象键不合法: " + objectKey);
        }
        for (String part : objectKey.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("对象键不合法: " + objectKey);
            }
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private static long requirePositive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        return value;
    }

    private static String sourceFormatExtension(String format) {
        String normalized = StringUtils.defaultIfBlank(format, "markdown").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "markdown", "md" -> "md";
            case "html" -> "html";
            default -> throw new IllegalArgumentException("不支持的报表正文格式: " + format);
        };
    }
}
