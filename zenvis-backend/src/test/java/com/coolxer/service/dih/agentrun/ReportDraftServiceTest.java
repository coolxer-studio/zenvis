package com.coolxer.service.dih.agentrun;

import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.ReportDraft;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.ReportDocumentRepository;
import com.coolxer.dao.mysql.repository.ReportDraftRepository;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.service.dih.ReportDocumentService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDraftServiceTest {

    private final ReportDraftRepository draftRepository = mock(ReportDraftRepository.class);
    private final ReportDocumentRepository documentRepository = mock(ReportDocumentRepository.class);
    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final ReportDocumentService reportDocumentService = mock(ReportDocumentService.class);
    private final AgentRunService agentRunService = mock(AgentRunService.class);

    private ReportDraftService service;
    private ReportDraft draft;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ReportDraftService(draftRepository, documentRepository,
                chatSessionRepository, reportDocumentService, agentRunService);
        user = new User();
        user.setId(7);
        ChatSession session = new ChatSession();
        session.setId(11);
        AgentRun run = new AgentRun().setRunId("run-1").setChatSessionId(11);
        run.setCreateBy(7);
        when(agentRunService.requireOwnedRun("run-1", user)).thenReturn(run);
        when(chatSessionRepository.findById(11)).thenReturn(Optional.of(session));
        draft = new ReportDraft()
                .setDocumentId("doc-1")
                .setRunId("run-1")
                .setChatSessionId(11)
                .setTitle("季度分析")
                .setFormat("markdown")
                .setBaseRevision(0L)
                .setStatus("draft");
        draft.setId(3);
        when(draftRepository.findByDocumentIdForUpdate("doc-1")).thenReturn(Optional.of(draft));
        when(draftRepository.save(any(ReportDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reportDocumentService.saveGenerated(any(), any(), any(), any(), any()))
                .thenReturn(new ReportWorkspaceVo(Map.of("revision", 1L), List.of(), List.of(), "{}"));
    }

    @Test
    void finalizeRejectsDuplicateSectionKeysAndDoesNotCreateRevision() {
        draft.setSections(sections(
                section("overview", "概览", "第一段", 0),
                section("overview", "重复", "第二段", 1)));

        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        verify(reportDocumentService, never()).saveGenerated(any(), any(), any(), any(), any());
    }

    @Test
    void finalizeRejectsUnsafeHtmlAndDuplicateOrdering() {
        draft.setFormat("html");
        draft.setSections(sections(section("overview", "概览", "<script>alert(1)</script>", 0)));

        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("脚本");

        draft.setFormat("markdown");
        draft.setSections(sections(
                section("a", "A", "正文 A", 0),
                section("b", "B", "正文 B", 0)));
        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("顺序");
    }

    @Test
    void finalizeValidatesAndCreatesOneImmutableRevision() {
        draft.setSections(sections(
                section("overview", "概览", "第一段", 0),
                section("details", "细节", "第二段", 1)));

        Map<String, Object> result = service.finalizeDraft("run-1", "1", user, "doc-1");

        assertThat(result)
                .containsEntry("documentId", "doc-1")
                .containsEntry("revision", 1L)
                .containsEntry("status", "finalized")
                .containsEntry("validatedSections", 2);
        assertThat(String.valueOf(result.get("contentHash"))).hasSize(64);
        assertThat(draft.getStatus()).isEqualTo("finalized");
        verify(reportDocumentService).saveGenerated(any(), any(), any(), any(), eq(user));
        verify(agentRunService).recordEvent(eq("run-1"), eq("artifact.updated"), eq("1"), any());
    }

    @Test
    void finalizeRejectsEmptyDocumentAndOversizedSection() {
        draft.setSections("[]");
        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有章节");

        draft.setSections(sections(section("overview", "概览", "x".repeat(2401), 0)));
        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2400");
    }

    @Test
    void finalizeRequiresEvidenceReferencesBeforeCreatingRevision() {
        Map<String, Object> section = new java.util.LinkedHashMap<>(
                section("overview", "概览", "无引用正文", 0));
        section.remove("sourceRefs");
        draft.setSections(sections(section));

        assertThatThrownBy(() -> service.finalizeDraft("run-1", "1", user, "doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("证据引用");
        verify(reportDocumentService, never()).saveGenerated(any(), any(), any(), any(), any());
    }

    private Map<String, Object> section(String key, String title, String content, int order) {
        return Map.of(
                "key", key,
                "title", title,
                "content", content,
                "order", order,
                "sourceRefs", List.of(Map.of("type", "message", "id", "m-1"))
        );
    }

    private String sections(Map<String, Object>... sections) {
        return JacksonUtil.toJson(List.of(sections));
    }
}
