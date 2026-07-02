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
import java.io.InputStream;
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
import java.util.regex.Pattern;

/**
 * 聊天附件上传与模型上下文读取。
 */
@Service
public class ChatAttachmentService {

    private static final long MAX_UPLOAD_BYTES = 30L * 1024L * 1024L;
    private static final int MAX_CONTEXT_CHARS_PER_FILE = 80_000;
    private static final int MAX_CONTEXT_CHARS_TOTAL = 160_000;
    private static final String KIND_IMAGE = "image";
    private static final String KIND_TEXT = "text";
    private static final String KIND_FILE = "file";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "jsonl", "csv", "tsv", "sql", "log",
            "xml", "yaml", "yml", "ini", "conf", "properties", "java", "js", "ts",
            "tsx", "jsx", "vue", "py", "go", "rs", "c", "cpp", "h", "hpp", "sh",
            "bat", "ps1", "html", "css", "scss", "less"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp"
    );

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

        boolean imageAttachment = isImageAttachment(fileName, file.getContentType()) && isSupportedImageFile(targetPath);
        boolean textAttachment = isTextAttachment(fileName, file.getContentType());
        String kind = imageAttachment ? KIND_IMAGE : textAttachment ? KIND_TEXT : KIND_FILE;
        return ChatAttachment.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileSize(file.getSize())
                .contentType(imageAttachment ? detectContentType(targetPath) : file.getContentType())
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
        int remainingContextChars = MAX_CONTEXT_CHARS_TOTAL;
        for (ChatAttachment attachment : attachments) {
            builder.append("\n\n[附件 ").append(index++).append("] ")
                    .append(safeText(attachment.getFileName(), "未命名文件"));
            if (attachment.getFileSize() != null) {
                builder.append("，大小 ").append(attachment.getFileSize()).append(" bytes");
            }
            if (StringUtils.hasText(attachment.getContentType())) {
                builder.append("，类型 ").append(attachment.getContentType());
            }

            if (remainingContextChars <= 0) {
                builder.append("\n附件文本总长度已超过 ").append(MAX_CONTEXT_CHARS_TOTAL).append(" 个字符，后续附件内容未写入模型上下文。");
                continue;
            }

            String textContent = readAttachmentText(attachment, user, Math.min(MAX_CONTEXT_CHARS_PER_FILE, remainingContextChars));
            if (StringUtils.hasText(textContent)) {
                remainingContextChars -= Math.min(textContent.length(), remainingContextChars);
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
        if (!isValidFileId(fileId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(resolveStoredFile(fileId, user));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public String detectContentType(Path filePath) {
        Optional<String> imageContentType = detectImageContentType(filePath);
        if (imageContentType.isPresent()) {
            return imageContentType.get();
        }
        try {
            String detected = Files.probeContentType(filePath);
            if (isSupportedImageContentType(detected)) {
                return detected.toLowerCase(Locale.ROOT);
            }
        } catch (IOException ignored) {
        }
        return "application/octet-stream";
    }

    private String readAttachmentText(ChatAttachment attachment, User user, int maxChars) {
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
            return readTextLimit(filePath, maxChars);
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
            if (detectImageContentType(filePath).isEmpty()) {
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
        if (!isValidFileId(fileId)) {
            return null;
        }
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
        return isImageAttachment(attachment.getFileName(), attachment.getContentType());
    }

    private boolean isImageAttachment(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return true;
        }
        return isSupportedImageContentType(contentType);
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

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean isValidFileId(String fileId) {
        return StringUtils.hasText(fileId) && UUID_PATTERN.matcher(fileId).matches();
    }

    private boolean isSupportedImageContentType(String contentType) {
        return StringUtils.hasText(contentType)
                && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private boolean isSupportedImageFile(Path filePath) {
        return detectImageContentType(filePath).isPresent();
    }

    private Optional<String> detectImageContentType(Path filePath) {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return Optional.empty();
        }
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            read = inputStream.read(header);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (read >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return Optional.of("image/png");
        }
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (read >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return Optional.of("image/gif");
        }
        if (read >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return Optional.of("image/webp");
        }
        if (read >= 2 && header[0] == 'B' && header[1] == 'M') {
            return Optional.of("image/bmp");
        }
        return Optional.empty();
    }
}
