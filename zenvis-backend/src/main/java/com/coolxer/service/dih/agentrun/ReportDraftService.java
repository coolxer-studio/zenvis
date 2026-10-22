package com.coolxer.service.dih.agentrun;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.ReportDocument;
import com.coolxer.dao.mysql.entity.ReportDraft;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.ReportDocumentRepository;
import com.coolxer.dao.mysql.repository.ReportDraftRepository;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.dto.ReportActionDto;
import com.coolxer.service.dih.ReportDocumentService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 报表章节草稿写入与最终修订提交。
 */
@Service
public class ReportDraftService {

    public static final int MAX_SECTION_CHARS = 2_400;

    private final ReportDraftRepository draftRepository;
    private final ReportDocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ReportDocumentService reportDocumentService;
    private final AgentRunService agentRunService;

    public ReportDraftService(ReportDraftRepository draftRepository,
                              ReportDocumentRepository documentRepository,
                              ChatSessionRepository chatSessionRepository,
                              ReportDocumentService reportDocumentService,
                              AgentRunService agentRunService) {
        this.draftRepository = draftRepository;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.reportDocumentService = reportDocumentService;
        this.agentRunService = agentRunService;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public Map<String, Object> prepare(String runId,
                                       String stepId,
                                       User user,
                                       String documentId,
                                       String title,
                                       String format,
                                       Long baseRevision,
                                       List<Map<String, Object>> sourceRefs) {
        AgentRunAccess access = requireAccess(runId, user);
        String resolvedDocumentId = StringUtils.defaultIfBlank(documentId, UUID.randomUUID().toString());
        ReportDraft draft = draftRepository.findByDocumentIdForUpdate(resolvedDocumentId)
                .orElseGet(() -> new ReportDraft()
                        .setDocumentId(resolvedDocumentId)
                        .setChatSessionId(access.session().getId())
                        .setRunId(runId)
                        .setSections("[]")
                        .setSourceRefs("[]"));
        if (!runId.equals(draft.getRunId())
                || !access.session().getId().equals(draft.getChatSessionId())) {
            throw new IllegalArgumentException("报表草稿不属于当前任务或会话");
        }
        ReportDocument existing = documentRepository
                .findByDocumentIdAndChatSessionId(resolvedDocumentId, access.session().getId())
                .orElse(null);
        draft.setTitle(StringUtils.left(StringUtils.defaultIfBlank(title, "报表文档"), 300))
                .setFormat(normalizeFormat(format))
                .setBaseRevision(existing == null ? baseRevision : existing.getCurrentRevision())
                .setSourceRefs(JacksonUtil.toJson(sourceRefs == null ? List.of() : sourceRefs))
                .setStatus("draft");
        draft.setCreateBy(user.getId());
        draft.setUpdateBy(user.getId());
        draft = draftRepository.save(draft);
        agentRunService.recordEvent(runId, "artifact.updated", stepId,
                Map.of("documentId", draft.getDocumentId(), "status", "prepared"));
        return draftMap(draft);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public Map<String, Object> upsertSection(String runId,
                                             String stepId,
                                             User user,
                                             String documentId,
                                             String sectionKey,
                                             String title,
                                             String content,
                                             Integer order,
                                             List<Map<String, Object>> sourceRefs) {
        if (StringUtils.isBlank(sectionKey)) {
            throw new IllegalArgumentException("sectionKey 不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("章节正文不能为空");
        }
        if (content.length() > MAX_SECTION_CHARS) {
            throw new IllegalArgumentException(
                    "章节正文超过 " + MAX_SECTION_CHARS + " 字符，请缩短后重新写入当前章节");
        }
        ReportDraft draft = requireDraft(runId, user, documentId);
        List<Map<String, Object>> sections = parseSections(draft.getSections());
        Map<String, Object> section = sections.stream()
                .filter(item -> StringUtils.equals(sectionKey, stringValue(item.get("key"))))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("key", sectionKey);
                    sections.add(created);
                    return created;
                });
        section.put("title", StringUtils.defaultIfBlank(title, sectionKey));
        section.put("content", content);
        section.put("order", order == null ? sections.size() : order);
        section.put("sourceRefs", sourceRefs == null ? List.of() : sourceRefs);
        section.put("contentHash", sha256(content));
        section.put("updatedAt", new java.util.Date().toInstant().toString());
        sections.sort(Comparator.comparingInt(item -> intValue(item.get("order"), 0)));
        draft.setSections(JacksonUtil.toJson(sections));
        draft.setUpdateBy(user.getId());
        draftRepository.save(draft);
        agentRunService.recordEvent(runId, "artifact.updated", stepId,
                Map.of("documentId", draft.getDocumentId(), "sectionKey", sectionKey,
                        "status", "section_saved", "chars", content.length()));
        return sectionMap(draft, section, sections.size());
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public Map<String, Object> reorder(String runId,
                                       String stepId,
                                       User user,
                                       String documentId,
                                       List<String> orderedKeys) {
        ReportDraft draft = requireDraft(runId, user, documentId);
        List<Map<String, Object>> sections = parseSections(draft.getSections());
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        sections.forEach(section -> byKey.put(stringValue(section.get("key")), section));
        List<Map<String, Object>> reordered = new ArrayList<>();
        int order = 0;
        for (String key : orderedKeys == null ? List.<String>of() : orderedKeys) {
            Map<String, Object> section = byKey.remove(key);
            if (section != null) {
                section.put("order", order++);
                reordered.add(section);
            }
        }
        reordered.addAll(byKey.values());
        for (int index = order; index < reordered.size(); index++) {
            reordered.get(index).put("order", index);
        }
        draft.setSections(JacksonUtil.toJson(reordered));
        draft.setUpdateBy(user.getId());
        draftRepository.save(draft);
        agentRunService.recordEvent(runId, "artifact.updated", stepId,
                Map.of("documentId", draft.getDocumentId(), "status", "reordered"));
        return draftMap(draft);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public Map<String, Object> finalizeDraft(String runId,
                                             String stepId,
                                             User user,
                                             String documentId) {
        ReportDraft draft = requireDraft(runId, user, documentId);
        List<Map<String, Object>> sections = parseSections(draft.getSections());
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("报表草稿没有章节，不能完成文档");
        }
        validateDocument(draft, sections);
        String content = renderDocument(draft, sections);
        ReportActionDto action = new ReportActionDto();
        action.setType(draft.getBaseRevision() == null || draft.getBaseRevision() <= 0
                ? ReportActionDto.FULL_GENERATE : ReportActionDto.FULL_REWRITE);
        action.setDocumentId(draft.getDocumentId());
        action.setBaseRevision(draft.getBaseRevision());
        action.setSourceRefs(parseRefs(draft.getSourceRefs()));
        ChatMessagePart part = ChatMessagePart.builder()
                .id(UUID.randomUUID().toString())
                .type("report-document")
                .title(draft.getTitle())
                .language(draft.getFormat())
                .content(content)
                .metadata(Map.of(
                        "title", draft.getTitle(),
                        "format", draft.getFormat(),
                        "outline", sections.stream().map(item -> Map.of(
                                "key", stringValue(item.get("key")),
                                "title", stringValue(item.get("title")),
                                "order", item.getOrDefault("order", 0)
                        )).toList(),
                        "sourceRefs", parseRefs(draft.getSourceRefs()),
                        "agentRunId", runId))
                .build();
        var workspace = reportDocumentService.saveGenerated(
                requireAccess(runId, user).session(), part, action,
                parseRefs(draft.getSourceRefs()), user);
        draft.setStatus("finalized");
        draft.setUpdateBy(user.getId());
        draftRepository.save(draft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", draft.getDocumentId());
        result.put("revision", workspace.getCurrentDocument() == null
                ? null : workspace.getCurrentDocument().get("revision"));
        result.put("chars", content.length());
        result.put("status", "finalized");
        result.put("validatedSections", sections.size());
        result.put("contentHash", sha256(content));
        agentRunService.recordEvent(runId, "artifact.updated", stepId, result);
        return result;
    }

    public boolean hasFinalizedDraft(String runId) {
        return StringUtils.isNotBlank(runId)
                && draftRepository.existsByRunIdAndStatus(runId, "finalized");
    }

    private ReportDraft requireDraft(String runId, User user, String documentId) {
        String resolvedDocumentId = StringUtils.trimToEmpty(documentId);
        ReportDraft draft = draftRepository.findByDocumentIdForUpdate(resolvedDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("报表草稿不存在，请先调用 report_prepare_document"));
        AgentRunAccess access = requireAccess(runId, user);
        if (!runId.equals(draft.getRunId())
                || !access.session().getId().equals(draft.getChatSessionId())) {
            throw new IllegalArgumentException("报表草稿不属于当前任务或会话");
        }
        return draft;
    }

    private AgentRunAccess requireAccess(String runId, User user) {
        var run = agentRunService.requireOwnedRun(runId, user);
        ChatSession session = chatSessionRepository.findById(run.getChatSessionId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        return new AgentRunAccess(run, session);
    }

    private String renderDocument(ReportDraft draft, List<Map<String, Object>> sections) {
        if ("html".equalsIgnoreCase(draft.getFormat())) {
            StringBuilder html = new StringBuilder("<!DOCTYPE html>\n<html lang=\"zh-CN\"><body>\n");
            html.append("<h1>").append(escapeHtml(draft.getTitle())).append("</h1>\n");
            for (Map<String, Object> section : sections) {
                html.append("<section>\n<h2>")
                        .append(escapeHtml(stringValue(section.get("title"))))
                        .append("</h2>\n")
                        .append(stringValue(section.get("content")))
                        .append("\n</section>\n");
            }
            return html.append("</body></html>").toString();
        }
        StringBuilder markdown = new StringBuilder("# ").append(draft.getTitle()).append("\n\n");
        for (Map<String, Object> section : sections) {
            markdown.append("## ")
                    .append(stringValue(section.get("title")))
                    .append("\n\n")
                    .append(stringValue(section.get("content")))
                    .append("\n\n");
        }
        return markdown.toString().trim();
    }

    private void validateDocument(ReportDraft draft, List<Map<String, Object>> sections) {
        if (StringUtils.isBlank(draft.getTitle())) {
            throw new IllegalArgumentException("报表标题不能为空");
        }
        String format = normalizeFormat(draft.getFormat());
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int totalChars = 0;
        boolean hasEvidence = !parseRefs(draft.getSourceRefs()).isEmpty();
        for (Map<String, Object> section : sections) {
            String key = StringUtils.trimToEmpty(stringValue(section.get("key")));
            String title = StringUtils.trimToEmpty(stringValue(section.get("title")));
            String content = stringValue(section.get("content"));
            if (StringUtils.isBlank(key) || key.length() > 128) {
                throw new IllegalArgumentException("报表章节 key 缺失或超过 128 字符");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("报表章节 key 重复: " + key);
            }
            if (StringUtils.isBlank(title) || title.length() > 300) {
                throw new IllegalArgumentException("报表章节标题缺失或超过 300 字符: " + key);
            }
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("报表章节正文为空: " + key);
            }
            if (content.length() > MAX_SECTION_CHARS) {
                throw new IllegalArgumentException(
                        "报表章节正文超过 " + MAX_SECTION_CHARS + " 字符: " + key);
            }
            int order = intValue(section.get("order"), -1);
            if (order < 0 || !orders.add(order)) {
                throw new IllegalArgumentException("报表章节顺序缺失或重复: " + key);
            }
            String contentHash = stringValue(section.get("contentHash"));
            if (StringUtils.isNotBlank(contentHash) && !contentHash.equals(sha256(content))) {
                throw new IllegalArgumentException("报表章节内容校验失败: " + key);
            }
            Object refs = section.get("sourceRefs");
            if (refs != null && !(refs instanceof List<?>)) {
                throw new IllegalArgumentException("报表章节证据引用格式错误: " + key);
            }
            if (refs instanceof List<?> list) {
                hasEvidence = hasEvidence || !list.isEmpty();
                for (Object ref : list) {
                    if (!(ref instanceof Map<?, ?> map) || map.isEmpty()) {
                        throw new IllegalArgumentException("报表章节包含无效证据引用: " + key);
                    }
                }
            }
            if ("html".equals(format)
                    && content.toLowerCase(java.util.Locale.ROOT).contains("<script")) {
                throw new IllegalArgumentException("报表章节包含不允许的脚本标签: " + key);
            }
            totalChars += content.length();
        }
        if (totalChars <= 0) {
            throw new IllegalArgumentException("报表文档正文为空");
        }
        if (!hasEvidence) {
            throw new IllegalArgumentException("报表文档缺少证据引用，不能完成");
        }
    }

    private Map<String, Object> draftMap(ReportDraft draft) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", draft.getDocumentId());
        result.put("title", draft.getTitle());
        result.put("format", draft.getFormat());
        result.put("baseRevision", draft.getBaseRevision());
        result.put("status", draft.getStatus());
        result.put("sections", parseSections(draft.getSections()).size());
        return result;
    }

    private Map<String, Object> sectionMap(ReportDraft draft,
                                           Map<String, Object> section,
                                           int totalSections) {
        Map<String, Object> result = new LinkedHashMap<>(section);
        result.put("documentId", draft.getDocumentId());
        result.put("sectionCount", totalSections);
        return result;
    }

    private List<Map<String, Object>> parseSections(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JacksonUtil.toList(
                    json, new TypeReference<List<Map<String, Object>>>() {}));
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> parseRefs(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JacksonUtil.toList(
                    json, new TypeReference<List<Map<String, Object>>>() {}));
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private String normalizeFormat(String format) {
        return "html".equalsIgnoreCase(format) ? "html" : "markdown";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private String escapeHtml(String value) {
        return StringUtils.defaultString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算章节摘要", e);
        }
    }

    private record AgentRunAccess(com.coolxer.dao.mysql.entity.AgentRun run, ChatSession session) {
    }
}
