package com.coolxer.service.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.minio.MinioBucketDomain;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.ReportArtifact;
import com.coolxer.dao.mysql.entity.ReportDocument;
import com.coolxer.dao.mysql.entity.ReportRevision;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.ReportArtifactRepository;
import com.coolxer.dao.mysql.repository.ReportDocumentRepository;
import com.coolxer.dao.mysql.repository.ReportRevisionRepository;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.dto.ReportActionDto;
import com.coolxer.model.dih.dto.ReportArchiveDto;
import com.coolxer.model.dih.dto.ReportArtifactRenameDto;
import com.coolxer.model.dih.dto.ReportDocumentSaveDto;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.JacksonUtil;
import com.coolxer.utils.StorageObjectKey;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 报表专用原子存储。会话 extraData 仅保存工作区摘要，正文和修订由独立表负责。
 */
@Service
@Slf4j
public class ReportDocumentService {

    public static final int MAX_REPORT_CONTENT_CHARS = 2_000_000;

    private final ChatSessionRepository chatSessionRepository;
    private final ReportDocumentRepository documentRepository;
    private final ReportRevisionRepository revisionRepository;
    private final ReportArtifactRepository artifactRepository;
    private final MinioService minioService;
    private final MinioProperties minioProperties;

    public ReportDocumentService(
            ChatSessionRepository chatSessionRepository,
            ReportDocumentRepository documentRepository,
            ReportRevisionRepository revisionRepository,
            ReportArtifactRepository artifactRepository) {
        this(chatSessionRepository, documentRepository, revisionRepository, artifactRepository,
                null, new MinioProperties());
    }

    @Autowired
    public ReportDocumentService(
            ChatSessionRepository chatSessionRepository,
            ReportDocumentRepository documentRepository,
            ReportRevisionRepository revisionRepository,
            ReportArtifactRepository artifactRepository,
            MinioService minioService,
            MinioProperties minioProperties) {
        this.chatSessionRepository = chatSessionRepository;
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.artifactRepository = artifactRepository;
        this.minioService = minioService;
        this.minioProperties = minioProperties;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo save(Long chatSessionId, ReportDocumentSaveDto request, User currentUser) {
        ChatSession session = lockOwnedSession(chatSessionId, currentUser);
        return saveLocked(session, request, currentUser, false);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo saveGenerated(
            ChatSession session,
            ChatMessagePart part,
            ReportActionDto action,
            List<Map<String, Object>> sourceRefs,
            User currentUser) {
        if (session == null || session.getId() == null) {
            throw new IllegalArgumentException("生成报表前必须先创建会话。");
        }
        ChatSession locked = lockOwnedSession((long) session.getId(), currentUser);
        Map<String, Object> metadata = part == null || part.getMetadata() == null
                ? Map.of()
                : part.getMetadata();
        ReportDocumentSaveDto request = new ReportDocumentSaveDto();
        request.setDocumentId(action == null
                ? stringValue(metadata.get("documentId"))
                : StringUtils.defaultIfBlank(action.getDocumentId(), stringValue(metadata.get("documentId"))));
        request.setBaseRevision(action == null ? null : action.getBaseRevision());
        request.setTitle(firstNonBlank(
                stringValue(metadata.get("title")),
                part == null ? null : part.getTitle(),
                "报表文档"));
        request.setFormat(firstNonBlank(
                stringValue(metadata.get("format")),
                part == null ? null : part.getLanguage(),
                "markdown"));
        request.setContent(part == null ? null : part.getContent());
        request.setOutline(listOfMaps(metadata.get("outline")));
        request.setSourceRefs(mergeSourceRefs(
                action == null ? List.of() : action.getSourceRefs(),
                listOfMaps(metadata.get("sourceRefs")),
                Boolean.TRUE.equals(metadata.get("demo"))
                        ? List.of(Map.of(
                                "type", "demo",
                                "id", stringValue(metadata.get("demoId")),
                                "status", "demonstration"))
                        : List.of(),
                sourceRefs));
        ReportWorkspaceVo workspace = saveLocked(locked, request, currentUser, true);
        session.setExtraData(workspace.getExtraData());
        return workspace;
    }

    @Transactional(readOnly = true, transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo workspace(Long chatSessionId, User currentUser) {
        ChatSession session = ownedSession(chatSessionId, currentUser);
        Optional<ReportDocument> document = documentRepository
                .findFirstByChatSessionIdAndCreateByOrderByUpdateTimeDesc(
                        session.getId(), currentUser.getId());
        return buildWorkspace(session, document.orElse(null), currentUser, true);
    }

    /**
     * 兼容旧会话级素材入口，当前只返回当前用户的图表库记录。
     */
    @Transactional(readOnly = true, transactionManager = "mysqlTransactionManager")
    public List<Map<String, Object>> materials(Long chatSessionId, User currentUser) {
        ownedSession(chatSessionId, currentUser);
        return chartLibrary(currentUser);
    }

    @Transactional(readOnly = true, transactionManager = "mysqlTransactionManager")
    public List<Map<String, Object>> materials(User currentUser) {
        return chartLibrary(currentUser);
    }

    /**
     * 汇总当前用户所有会话中已经执行“加入图表库”的临时图表。
     */
    @Transactional(readOnly = true, transactionManager = "mysqlTransactionManager")
    public List<Map<String, Object>> chartLibrary(User currentUser) {
        if (currentUser == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        return availableChartLibrary(currentUser);
    }

    @Transactional(readOnly = true, transactionManager = "mysqlTransactionManager")
    public List<Map<String, Object>> validateSourceRefs(
            Long chatSessionId,
            List<Map<String, Object>> requested,
            User currentUser) {
        ChatSession session = ownedSession(chatSessionId, currentUser);
        ReportDocument current = documentRepository
                .findFirstByChatSessionIdAndCreateByOrderByUpdateTimeDesc(
                        session.getId(), currentUser.getId())
                .orElse(null);
        return validatedSourceRefs(current, requested, currentUser);
    }

    private List<Map<String, Object>> availableChartLibrary(User currentUser) {
        Map<String, Map<String, Object>> charts = new LinkedHashMap<>();
        List<ChatSession> sourceSessions = Optional.ofNullable(chatSessionRepository
                        .findAllByCreateByOrderByUpdateTimeDesc(currentUser.getId()))
                .orElseGet(List::of);
        for (ChatSession sourceSession : sourceSessions) {
            if (sourceSession == null
                    || !Objects.equals(sourceSession.getCreateBy(), currentUser.getId())) {
                continue;
            }
            collectChartLibrary(charts, sourceSession);
        }
        return charts.values().stream()
                .sorted(Comparator
                        .comparingLong(this::chartAddedAt)
                        .reversed()
                        .thenComparing(
                                chart -> stringValue(chart.get("addedAt")),
                                Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo archive(
            Long chatSessionId,
            ReportArchiveDto request,
            User currentUser) {
        ChatSession session = lockOwnedSession(chatSessionId, currentUser);
        ReportDocument document = requireDocument(
                session,
                request == null ? null : request.getDocumentId());
        assertRevision(document, request == null ? null : request.getBaseRevision());

        ReportArtifact artifact = new ReportArtifact();
        artifact.setArtifactId(UUID.randomUUID().toString());
        artifact.setChatSessionId(session.getId());
        artifact.setDocumentId(document.getDocumentId());
        artifact.setRevision(document.getCurrentRevision());
        artifact.setName(firstNonBlank(
                request == null ? null : request.getName(),
                document.getTitle() + " " + version(document.getCurrentRevision())));
        artifact.setTitle(document.getTitle());
        artifact.setFormat(document.getFormat());
        artifact.setContentHash(document.getContentHash());
        requireRevisionObject(document.getDocumentId(), document.getCurrentRevision(), document.getFormat());
        artifact.setContent(null);
        artifact.setOutline(document.getOutline());
        artifact.setSourceRefs(document.getSourceRefs());
        artifact.setCreateBy(currentUser.getId());
        artifact.setUpdateBy(currentUser.getId());
        artifactRepository.save(artifact);
        updateExtraDataReferences(session, document, currentUser);
        return buildWorkspace(session, document, currentUser, true);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo restore(
            Long chatSessionId,
            String artifactId,
            ReportArchiveDto request,
            User currentUser) {
        ChatSession session = lockOwnedSession(chatSessionId, currentUser);
        ReportDocument document = requireDocument(
                session,
                request == null ? null : request.getDocumentId());
        assertRevision(document, request == null ? null : request.getBaseRevision());
        ReportArtifact artifact = requireArtifact(session, artifactId, currentUser);

        ReportDocumentSaveDto save = new ReportDocumentSaveDto();
        save.setDocumentId(document.getDocumentId());
        save.setBaseRevision(document.getCurrentRevision());
        save.setTitle(artifact.getTitle());
        save.setFormat(artifact.getFormat());
        save.setContent(readRevisionContent(
                artifact.getDocumentId(), artifact.getRevision(), artifact.getFormat()));
        save.setOutline(parseList(artifact.getOutline()));
        save.setSourceRefs(parseList(artifact.getSourceRefs()));
        return saveLocked(session, save, currentUser, true);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo renameArtifact(
            Long chatSessionId,
            String artifactId,
            ReportArtifactRenameDto request,
            User currentUser) {
        ChatSession session = lockOwnedSession(chatSessionId, currentUser);
        ReportDocument document = requireDocument(session, null);
        assertRevision(document, request == null ? null : request.getBaseRevision());
        if (request == null || StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("归档名称不能为空。");
        }
        ReportArtifact artifact = requireArtifact(session, artifactId, currentUser);
        artifact.setName(StringUtils.left(request.getName().trim(), 300));
        artifact.setUpdateBy(currentUser.getId());
        artifactRepository.save(artifact);
        updateExtraDataReferences(session, document, currentUser);
        return buildWorkspace(session, document, currentUser, true);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ReportWorkspaceVo deleteArtifact(
            Long chatSessionId,
            String artifactId,
            Long baseRevision,
            User currentUser) {
        ChatSession session = lockOwnedSession(chatSessionId, currentUser);
        ReportDocument document = requireDocument(session, null);
        assertRevision(document, baseRevision);
        ReportArtifact artifact = requireArtifact(session, artifactId, currentUser);
        artifactRepository.delete(artifact);
        artifactRepository.flush();
        updateExtraDataReferences(session, document, currentUser);
        return buildWorkspace(session, document, currentUser, true);
    }

    private ReportWorkspaceVo saveLocked(
            ChatSession session,
            ReportDocumentSaveDto request,
            User currentUser,
            boolean trustedSourceRefs) {
        validateSaveRequest(request);
        ReportDocument document = findDocument(session, request.getDocumentId()).orElse(null);
        long currentRevision = document == null ? 0 : document.getCurrentRevision();
        Long requestedBase = request.getBaseRevision();
        if (requestedBase != null && requestedBase != currentRevision) {
            throw new ReportRevisionConflictException(documentMap(document, true));
        }
        if (document == null) {
            document = new ReportDocument();
            document.setDocumentId(StringUtils.defaultIfBlank(
                    request.getDocumentId(), UUID.randomUUID().toString()));
            document.setChatSessionId(session.getId());
            document.setCreateBy(currentUser.getId());
        }

        long nextRevision = currentRevision + 1;
        String title = StringUtils.left(firstNonBlank(request.getTitle(), "报表文档"), 300);
        String format = normalizeFormat(request.getFormat());
        String content = request.getContent();
        String outline = JacksonUtil.toJson(
                request.getOutline() == null ? List.of() : request.getOutline());
        List<Map<String, Object>> acceptedSourceRefs = trustedSourceRefs
                ? request.getSourceRefs()
                : validatedSourceRefs(document, request.getSourceRefs(), currentUser);
        String sourceRefs = JacksonUtil.toJson(
                acceptedSourceRefs == null ? List.of() : acceptedSourceRefs);
        String contentHash = sha256(content);
        String documentId = document.getDocumentId();
        String revisionObjectKey = StorageObjectKey.reportRevision(documentId, nextRevision, format);
        storeRevisionContent(revisionObjectKey, content);

        document.setTitle(title);
        document.setFormat(format);
        document.setCurrentRevision(nextRevision);
        document.setStatus("draft");
        document.setContentHash(contentHash);
        document.setContent(content);
        document.setOutline(outline);
        document.setSourceRefs(sourceRefs);
        document.setUpdateBy(currentUser.getId());
        document = documentRepository.save(document);

        ReportRevision revision = new ReportRevision();
        revision.setDocumentId(document.getDocumentId());
        revision.setRevision(nextRevision);
        revision.setTitle(title);
        revision.setFormat(format);
        revision.setContentHash(contentHash);
        revision.setContent(null);
        revision.setOutline(outline);
        revision.setSourceRefs(sourceRefs);
        revision.setCreateBy(currentUser.getId());
        revision.setUpdateBy(currentUser.getId());
        revisionRepository.save(revision);

        updateExtraDataReferences(session, document, currentUser);
        return buildWorkspace(session, document, currentUser, true);
    }

    private ChatSession lockOwnedSession(Long id, User currentUser) {
        if (currentUser == null || id == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        return chatSessionRepository.findOwnedByIdForUpdate(
                        Math.toIntExact(id), currentUser.getId())
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_AUTHORITY));
    }

    private ChatSession ownedSession(Long id, User currentUser) {
        if (currentUser == null || id == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        ChatSession session = chatSessionRepository.findById(Math.toIntExact(id)).orElse(null);
        if (session == null || !Objects.equals(session.getCreateBy(), currentUser.getId())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        return session;
    }

    private Optional<ReportDocument> findDocument(ChatSession session, String documentId) {
        if (StringUtils.isNotBlank(documentId)) {
            return documentRepository.findByDocumentIdAndChatSessionId(
                    documentId, session.getId());
        }
        return documentRepository.findFirstByChatSessionIdOrderByUpdateTimeDesc(session.getId());
    }

    private ReportDocument requireDocument(ChatSession session, String documentId) {
        return findDocument(session, documentId)
                .orElseThrow(() -> new IllegalArgumentException("报表文档不存在。"));
    }

    private ReportArtifact requireArtifact(
            ChatSession session, String artifactId, User currentUser) {
        return artifactRepository.findByArtifactIdAndChatSessionIdAndCreateBy(
                        artifactId, session.getId(), currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("归档不存在。"));
    }

    private void assertRevision(ReportDocument document, Long baseRevision) {
        if (baseRevision == null || !Objects.equals(baseRevision, document.getCurrentRevision())) {
            throw new ReportRevisionConflictException(documentMap(document, true));
        }
    }

    private void validateSaveRequest(ReportDocumentSaveDto request) {
        if (request == null || StringUtils.isBlank(request.getContent())) {
            throw new IllegalArgumentException("报表正文不能为空。");
        }
        if (request.getContent().length() > MAX_REPORT_CONTENT_CHARS) {
            throw new IllegalArgumentException(
                    "报表正文超过 " + MAX_REPORT_CONTENT_CHARS + " 字符限制。");
        }
        normalizeFormat(request.getFormat());
    }

    private void collectChartLibrary(
            Map<String, Map<String, Object>> charts,
            ChatSession session) {
        Map<String, Object> extraData;
        try {
            extraData = parseObject(session.getExtraData());
        } catch (RuntimeException e) {
            // 历史会话可能存在旧版或损坏 JSON；跳过该会话，不影响其余图表。
            return;
        }
        Map<String, Object> visualization = mapValue(extraData.get("visualizationChart"));
        for (Map<String, Object> source : listOfMaps(visualization.get("chartLibrary"))) {
            Map<String, Object> chart = chartLibraryMaterial(source, session);
            String key = chartLibraryKey(chart);
            Map<String, Object> existing = charts.get(key);
            if (existing == null || chartAddedAt(chart) >= chartAddedAt(existing)) {
                charts.put(key, chart);
            }
        }
    }

    private Map<String, Object> chartLibraryMaterial(
            Map<String, Object> source,
            ChatSession session) {
        String artifactId = stringValue(source.get("artifactId"));
        String id = firstNonBlank(
                stringValue(source.get("recordId")),
                stringValue(source.get("id")),
                artifactId,
                sha256(JacksonUtil.toJson(source)));
        Map<String, Object> echarts = mapValue(source.get("echarts"));
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "chart");
        chart.put("id", id);
        if (StringUtils.isNotBlank(artifactId)) {
            chart.put("artifactId", artifactId);
        }
        chart.put("name", firstNonBlank(
                stringValue(source.get("name")),
                stringValue(source.get("title")),
                "图表"));
        chart.put("chartType", firstNonBlank(
                stringValue(source.get("chartType")),
                stringValue(source.get("chart_type")),
                stringValue(echarts.get("chart_type")),
                stringValue(echarts.get("chartType")),
                "chart"));
        chart.put("description", firstNonBlank(
                stringValue(source.get("description")),
                stringValue(source.get("content")),
                ""));
        chart.put("status", firstNonBlank(
                stringValue(source.get("status")),
                "available"));
        chart.put("origin", "chart_library");
        for (String field : List.of("source", "addedAt", "queriedAt")) {
            if (StringUtils.isNotBlank(stringValue(source.get(field)))) {
                chart.put(field, source.get(field));
            }
        }
        chart.put("echartsOption", chartOption(source));
        addSessionMetadata(chart, session);
        return chart;
    }

    private Map<String, Object> chartOption(Map<String, Object> source) {
        Map<String, Object> echarts = mapValue(source.get("echarts"));
        List<Object> candidates = new ArrayList<>();
        candidates.add(source.get("echartsOption"));
        candidates.add(echarts.get("option"));
        candidates.add(source.get("option"));
        for (Object candidate : candidates) {
            Map<String, Object> option = optionValue(candidate);
            if (!option.isEmpty()) {
                return option;
            }
        }
        for (String field : List.of("config", "amisConfig")) {
            Map<String, Object> config = mapValue(source.get(field));
            if (!"chart".equals(stringValue(config.get("type")))) {
                continue;
            }
            Map<String, Object> option = optionValue(firstNonBlankObject(
                    config.get("config"), config.get("option")));
            if (!option.isEmpty()) {
                return option;
            }
        }
        return Map.of();
    }

    private Object firstNonBlankObject(Object first, Object second) {
        return first instanceof Map<?, ?> map && !map.isEmpty() ? first : second;
    }

    private Map<String, Object> optionValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return mapValue(value);
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                Map<String, Object> parsed = JacksonUtil.toMap(
                        text, new TypeReference<Map<String, Object>>() {});
                return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private String chartLibraryKey(Map<String, Object> chart) {
        return firstNonBlank(
                stringValue(chart.get("artifactId")),
                stringValue(chart.get("id")),
                sha256(JacksonUtil.toJson(chart)));
    }

    private long chartAddedAt(Map<String, Object> chart) {
        String value = stringValue(chart.get("addedAt"));
        if (StringUtils.isBlank(value)) {
            return Long.MIN_VALUE;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignoredAgain) {
                return Long.MIN_VALUE;
            }
        }
    }

    private List<Map<String, Object>> validatedSourceRefs(
            ReportDocument current,
            List<Map<String, Object>> requested,
            User currentUser) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> allowed = new LinkedHashMap<>();
        if (current != null) {
            for (Map<String, Object> ref : parseList(current.getSourceRefs())) {
                Map<String, Object> compact = compactSourceRef(ref);
                allowed.put(sourceRefKey(compact), compact);
            }
        }
        List<Map<String, Object>> limitedRequested = requested.stream().limit(300).toList();
        boolean containsNewReference = limitedRequested.stream()
                .map(this::sourceRefKey)
                .anyMatch(key -> !allowed.containsKey(key));
        if (containsNewReference) {
            for (Map<String, Object> chart : availableChartLibrary(currentUser)) {
                if (!"chart".equals(stringValue(chart.get("type")))) {
                    continue;
                }
                Map<String, Object> compact = chartSourceRef(chart);
                allowed.put(sourceRefKey(compact), compact);
            }
        }
        Map<String, Map<String, Object>> verified = new LinkedHashMap<>();
        for (Map<String, Object> ref : limitedRequested) {
            Map<String, Object> canonical = allowed.get(sourceRefKey(ref));
            if (canonical != null) {
                verified.putIfAbsent(sourceRefKey(canonical), canonical);
            }
        }
        return new ArrayList<>(verified.values());
    }

    private Map<String, Object> compactSourceRef(Map<String, Object> ref) {
        if (ref == null) {
            return new LinkedHashMap<>();
        }
        if ("chart".equals(stringValue(ref.get("type")))) {
            return chartSourceRef(ref);
        }
        return new LinkedHashMap<>(ref);
    }

    private Map<String, Object> chartSourceRef(Map<String, Object> chart) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("type", "chart");
        for (String field : List.of(
                "id", "artifactId", "name", "title", "chartType", "description",
                "status", "origin", "source", "addedAt", "queriedAt",
                "sessionRecordId", "chatSessionId", "sessionTitle", "agentType")) {
            Object value = chart.get(field);
            if (value != null && StringUtils.isNotBlank(stringValue(value))) {
                ref.put(field, value);
            }
        }
        return ref;
    }

    private String sourceRefKey(Map<String, Object> ref) {
        if (ref == null) {
            return "";
        }
        return firstNonBlank(stringValue(ref.get("type")), "material") + ":"
                + firstNonBlank(
                        stringValue(ref.get("id")),
                        stringValue(ref.get("auditId")),
                        stringValue(ref.get("partId")),
                        stringValue(ref.get("name")),
                        "");
    }

    private void addSessionMetadata(Map<String, Object> ref, ChatSession session) {
        if (session == null) {
            return;
        }
        ref.put("sessionRecordId", session.getId());
        ref.put("chatSessionId", session.getSessionId());
        ref.put("sessionTitle", session.getTitle());
        ref.put("agentType", session.getType());
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private String normalizeFormat(String format) {
        String normalized = StringUtils.defaultIfBlank(format, "markdown").toLowerCase();
        if (!List.of("markdown", "html").contains(normalized)) {
            throw new IllegalArgumentException("报表格式仅支持 markdown 或 html。");
        }
        return normalized;
    }

    private void updateExtraDataReferences(
            ChatSession session,
            ReportDocument document,
            User currentUser) {
        Map<String, Object> extraData = parseObject(session.getExtraData());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("currentDocument", documentMap(document, false));
        report.put("documents", List.of(documentMap(document, false)));
        report.put("artifacts", artifactRepository
                .findByChatSessionIdAndCreateByOrderByCreateTimeDesc(
                        session.getId(), currentUser.getId())
                .stream()
                .map(artifact -> artifactMap(artifact, false))
                .toList());
        extraData.put("report", report);
        session.setExtraData(JacksonUtil.toJson(extraData));
        chatSessionRepository.save(session);
    }

    private ReportWorkspaceVo buildWorkspace(
            ChatSession session,
            ReportDocument document,
            User currentUser,
            boolean includeContent) {
        List<Map<String, Object>> revisions = document == null
                ? List.of()
                : revisionRepository.findByDocumentIdOrderByRevisionDesc(document.getDocumentId())
                .stream()
                .map(this::revisionMap)
                .toList();
        List<Map<String, Object>> artifacts = artifactRepository
                .findByChatSessionIdAndCreateByOrderByCreateTimeDesc(
                        session.getId(), currentUser.getId())
                .stream()
                .map(artifact -> artifactMap(artifact, includeContent))
                .toList();
        return new ReportWorkspaceVo(
                documentMap(document, includeContent),
                revisions,
                artifacts,
                session.getExtraData());
    }

    private Map<String, Object> documentMap(ReportDocument document, boolean includeContent) {
        if (document == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", document.getDocumentId());
        result.put("documentId", document.getDocumentId());
        result.put("title", document.getTitle());
        result.put("name", document.getTitle());
        result.put("format", document.getFormat());
        result.put("revision", document.getCurrentRevision());
        result.put("version", version(document.getCurrentRevision()));
        result.put("status", document.getStatus());
        result.put("contentHash", document.getContentHash());
        result.put("outline", parseList(document.getOutline()));
        result.put("sourceRefs", parseList(document.getSourceRefs()));
        result.put("updatedAt", document.getUpdateTime() == null
                ? OffsetDateTime.now().toString()
                : document.getUpdateTime());
        if (includeContent) {
            result.put("content", document.getContent());
        }
        return result;
    }

    private Map<String, Object> revisionMap(ReportRevision revision) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revision", revision.getRevision());
        result.put("version", version(revision.getRevision()));
        result.put("title", revision.getTitle());
        result.put("format", revision.getFormat());
        result.put("contentHash", revision.getContentHash());
        result.put("createdAt", revision.getCreateTime());
        result.put("sourceRefs", parseList(revision.getSourceRefs()));
        return result;
    }

    private Map<String, Object> artifactMap(ReportArtifact artifact, boolean includeContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", artifact.getArtifactId());
        result.put("artifactId", artifact.getArtifactId());
        result.put("documentId", artifact.getDocumentId());
        result.put("revision", artifact.getRevision());
        result.put("version", version(artifact.getRevision()));
        result.put("name", artifact.getName());
        result.put("title", artifact.getTitle());
        result.put("format", artifact.getFormat());
        result.put("contentHash", artifact.getContentHash());
        result.put("status", "archived");
        result.put("createdAt", artifact.getCreateTime());
        result.put("sourceRefs", parseList(artifact.getSourceRefs()));
        if (includeContent) {
            result.put("content", readRevisionContent(
                    artifact.getDocumentId(), artifact.getRevision(), artifact.getFormat()));
            result.put("outline", parseList(artifact.getOutline()));
        }
        return result;
    }

    private Map<String, Object> parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> parsed = JacksonUtil.toMap(
                json, new TypeReference<Map<String, Object>>() {});
        return new LinkedHashMap<>(parsed);
    }

    private List<Map<String, Object>> parseList(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        return JacksonUtil.toList(
                json, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), nestedValue));
                result.add(copy);
            }
        }
        return result;
    }

    @SafeVarargs
    private final List<Map<String, Object>> mergeSourceRefs(
            List<Map<String, Object>>... sources) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (List<Map<String, Object>> source : sources) {
            if (source == null) {
                continue;
            }
            for (Map<String, Object> ref : source) {
                if (ref == null || ref.isEmpty()) {
                    continue;
                }
                String key = firstNonBlank(
                        stringValue(ref.get("id")),
                        stringValue(ref.get("auditId")),
                        stringValue(ref.get("partId")),
                        sha256(JacksonUtil.toJson(ref)));
                unique.putIfAbsent(key, new LinkedHashMap<>(ref));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void storeRevisionContent(String objectKey, String content) {
        if (minioService == null) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "报表对象存储未配置");
        }
        byte[] bytes = StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8);
        boolean existed = minioService.isFile(objectKey, reportsBucket());
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioService.uploadStream(objectKey, inputStream, bytes.length,
                    objectKey.endsWith(".html") ? "text/html; charset=utf-8" : "text/markdown; charset=utf-8",
                    reportsBucket());
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.FILE_WRITE_FAIL.getCode(), "报表修订正文写入失败：" + e.getMessage());
        }
        if (!existed && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_ROLLED_BACK) {
                        return;
                    }
                    try {
                        minioService.deleteFile(objectKey, reportsBucket());
                    } catch (Exception cleanupError) {
                        log.warn("回滚报表对象失败: object={}", objectKey, cleanupError);
                    }
                }
            });
        }
    }

    private void requireRevisionObject(String documentId, long revision, String format) {
        String objectKey = StorageObjectKey.reportRevision(documentId, revision, format);
        try {
            minioService.getObjectInfo(objectKey, reportsBucket());
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(),
                    "报表修订正文不存在或不可读取，请重新生成报表。");
        }
    }

    private String readRevisionContent(String documentId, long revision, String format) {
        if (minioService == null) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "报表对象存储未配置");
        }
        String objectKey = StorageObjectKey.reportRevision(documentId, revision, format);
        try (InputStream inputStream = minioService.downloadFile(objectKey, reportsBucket())) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(),
                    "报表修订正文不存在或不可读取，请重新生成报表。");
        }
    }

    private String reportsBucket() {
        return minioProperties == null
                ? MinioProperties.DEFAULT_REPORTS_BUCKET
                : minioProperties.bucket(MinioBucketDomain.REPORTS);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算报表内容摘要。", e);
        }
    }

    private String version(Long revision) {
        return "v" + (revision == null ? 0 : revision);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
