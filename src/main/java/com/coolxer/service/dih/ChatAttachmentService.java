package com.coolxer.service.dih;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.utils.ImageDataUriUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 聊天附件上传与模型上下文读取。
 */
@Service
public class ChatAttachmentService {

    private static final long MAX_UPLOAD_BYTES = 30L * 1024L * 1024L;
    private static final int MAX_CONTEXT_CHARS_PER_FILE = 80_000;
    private static final String KIND_IMAGE = "image";
    private static final String KIND_TEXT = "text";
    private static final String KIND_FILE = "file";
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "jsonl", "csv", "tsv", "sql", "log",
            "xml", "yaml", "yml", "ini", "conf", "properties", "java", "js", "ts",
            "tsx", "jsx", "vue", "py", "go", "rs", "c", "cpp", "h", "hpp", "sh",
            "bat", "ps1", "html", "css", "scss", "less"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");

    private final CustomWebConfig customWebConfig;

    public ChatAttachmentService(CustomWebConfig customWebConfig) {
        this.customWebConfig = customWebConfig;
    }

    public ChatAttachment upload(MultipartFile file, User user) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("文件大小不能超过 30MB");
        }

        String fileId = UUID.randomUUID().toString();
        String fileName = sanitizeFileName(file.getOriginalFilename());
        Path uploadRoot = uploadRoot(user);
        Files.createDirectories(uploadRoot);

        Path targetPath = uploadRoot.resolve(fileId + "_" + fileName).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("非法文件名");
        }
        file.transferTo(targetPath);

        boolean imageAttachment = isImageAttachment(fileName, file.getContentType());
        boolean textAttachment = isTextAttachment(fileName, file.getContentType());
        String kind = imageAttachment ? KIND_IMAGE : textAttachment ? KIND_TEXT : KIND_FILE;
        return ChatAttachment.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .kind(kind)
                .fileUrl("/api/v1/dih/upload/" + fileId + "/preview")
                .parseStatus(imageAttachment ? "image" : textAttachment ? "readable" : "unsupported")
                .message(uploadMessage(kind))
                .build();
    }

    public String appendAttachmentContext(String message, List<ChatAttachment> attachments, User user) {
        if (attachments == null || attachments.isEmpty()) {
            return message;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(message) ? message : "请分析我上传的附件内容。");
        builder.append("\n\n---\n以下是用户本轮消息上传的附件内容，请结合这些附件回答。");

        int index = 1;
        for (ChatAttachment attachment : attachments) {
            builder.append("\n\n[附件 ").append(index++).append("] ")
                    .append(safeText(attachment.getFileName(), "未命名文件"));
            if (attachment.getFileSize() != null) {
                builder.append("，大小 ").append(attachment.getFileSize()).append(" bytes");
            }
            if (StringUtils.hasText(attachment.getContentType())) {
                builder.append("，类型 ").append(attachment.getContentType());
            }

            String textContent = readAttachmentText(attachment, user);
            if (StringUtils.hasText(textContent)) {
                builder.append("\n```").append(extensionOf(attachment.getFileName())).append("\n")
                        .append(textContent)
                        .append("\n```");
            } else if (isImageAttachment(attachment)) {
                builder.append("\n该图片将作为 image_url 图片输入发送给支持视觉能力的模型。");
            } else {
                builder.append("\n该附件暂不支持直接解析内容。你可以基于文件名和用户问题说明限制，")
                        .append("不要声称已经读取了该文件的内部内容。");
            }
        }
        return builder.toString();
    }

    public boolean hasImageAttachment(List<ChatAttachment> attachments) {
        return attachments != null && attachments.stream().anyMatch(this::isImageAttachment);
    }

    public List<Map<String, Object>> buildOpenAiImageContentParts(List<ChatAttachment> attachments, User user) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .filter(this::isImageAttachment)
                .map(attachment -> toOpenAiImageContentPart(attachment, user))
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<Path> resolveAttachmentFile(String fileId, User user) {
        if (!StringUtils.hasText(fileId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(resolveStoredFile(fileId, user));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public String detectContentType(Path filePath) {
        try {
            String detected = Files.probeContentType(filePath);
            if (StringUtils.hasText(detected)) {
                return detected;
            }
        } catch (IOException ignored) {
        }
        return isImageAttachment(filePath.getFileName().toString(), null)
                ? "image/" + normalizeImageExtension(extensionOf(filePath.getFileName().toString()))
                : "application/octet-stream";
    }

    private String readAttachmentText(ChatAttachment attachment, User user) {
        if (attachment == null || !StringUtils.hasText(attachment.getFileId())) {
            return "";
        }
        String contentType = attachment.getContentType();
        String fileName = attachment.getFileName();
        if (!isTextAttachment(fileName, contentType)) {
            return "";
        }

        try {
            Path filePath = resolveStoredFile(attachment.getFileId(), user);
            if (filePath == null || !Files.isRegularFile(filePath)) {
                return "";
            }
            return readTextLimit(filePath, MAX_CONTEXT_CHARS_PER_FILE);
        } catch (Exception e) {
            return "";
        }
    }

    private Optional<Map<String, Object>> toOpenAiImageContentPart(ChatAttachment attachment, User user) {
        try {
            Path filePath = resolveStoredFile(attachment.getFileId(), user);
            if (filePath == null || !Files.isRegularFile(filePath)) {
                return Optional.empty();
            }
            String dataUri = ImageDataUriUtil.toDataUri(filePath.toFile());
            return Optional.of(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", dataUri,
                            "detail", "auto"
                    )
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String readTextLimit(Path filePath, int maxChars) throws IOException {
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(filePath), decoder))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (builder.length() >= maxChars) {
                    truncated = true;
                    break;
                }
                int remaining = maxChars - builder.length();
                builder.append(buffer, 0, Math.min(read, remaining));
                if (read > remaining) {
                    truncated = true;
                    break;
                }
            }
        }
        if (truncated) {
            builder.append("\n\n[内容已截断，仅展示前 ").append(maxChars).append(" 个字符]");
        }
        return builder.toString();
    }

    private Path resolveStoredFile(String fileId, User user) throws IOException {
        Path uploadRoot = uploadRoot(user);
        if (!Files.isDirectory(uploadRoot)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadRoot, fileId + "_*")) {
            for (Path path : stream) {
                Path normalized = path.toAbsolutePath().normalize();
                if (normalized.startsWith(uploadRoot) && Files.isRegularFile(normalized)) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private Path uploadRoot(User user) {
        String userFolder = user != null && user.getId() != null ? String.valueOf(user.getId()) : "anonymous";
        return Paths.get(customWebConfig.getSessionWorkspacePath(), "chat-uploads", userFolder)
                .toAbsolutePath()
                .normalize();
    }

    private boolean isTextAttachment(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return true;
        }
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        return normalizedContentType.startsWith("text/")
                || normalizedContentType.contains("json")
                || normalizedContentType.contains("xml")
                || normalizedContentType.contains("csv")
                || normalizedContentType.contains("yaml");
    }

    private boolean isImageAttachment(ChatAttachment attachment) {
        if (attachment == null) {
            return false;
        }
        if (KIND_IMAGE.equals(attachment.getKind())) {
            return true;
        }
        return isImageAttachment(attachment.getFileName(), attachment.getContentType());
    }

    private boolean isImageAttachment(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return true;
        }
        return StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private String uploadMessage(String kind) {
        if (KIND_IMAGE.equals(kind)) {
            return "图片已上传，将随下一条消息发送给支持视觉能力的模型。";
        }
        if (KIND_TEXT.equals(kind)) {
            return "文件已上传，将随下一条消息发送给模型。";
        }
        return "文件已上传；当前仅文本类文件会解析为模型上下文。";
    }

    private String sanitizeFileName(String originalFilename) {
        String fallback = "attachment";
        String name = StringUtils.hasText(originalFilename) ? originalFilename : fallback;
        name = name.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return StringUtils.hasText(name) ? name : fallback;
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "text";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "text";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeImageExtension(String extension) {
        if ("jpg".equals(extension)) {
            return "jpeg";
        }
        return StringUtils.hasText(extension) ? extension : "png";
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
