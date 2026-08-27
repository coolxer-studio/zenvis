package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.ReportArtifact;
import com.coolxer.dao.mysql.entity.ReportDocument;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.ReportArtifactRepository;
import com.coolxer.dao.mysql.repository.ReportDocumentRepository;
import com.coolxer.dao.mysql.repository.ReportRevisionRepository;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ReportArchiveDto;
import com.coolxer.model.dih.dto.ReportDocumentSaveDto;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDocumentServiceTest {

    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final ReportDocumentRepository documentRepository = mock(ReportDocumentRepository.class);
    private final ReportRevisionRepository revisionRepository = mock(ReportRevisionRepository.class);
    private final ReportArtifactRepository artifactRepository = mock(ReportArtifactRepository.class);

    private ReportDocumentService service;
    private ChatSession session;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ReportDocumentService(
                chatSessionRepository,
                documentRepository,
                revisionRepository,
                artifactRepository);
        session = new ChatSession();
        session.setId(11);
        session.setCreateBy(7);
        session.setExtraData("""
                {"dataVisualization":{"chartLibrary":[{"id":"chart-1"}]},"workflow":{"state":"running"}}
                """);
        user = new User();
        user.setId(7);
        when(chatSessionRepository.findOwnedByIdForUpdate(11, 7))
                .thenReturn(Optional.of(session));
        when(chatSessionRepository.findById(11)).thenReturn(Optional.of(session));
        when(documentRepository.save(any(ReportDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.findByChatSessionIdAndCreateByOrderByCreateTimeDesc(11, 7))
                .thenReturn(List.of());
        when(revisionRepository.findByDocumentIdOrderByRevisionDesc(any()))
                .thenReturn(List.of());
    }

    @Test
    void saveCreatesRevisionWithoutCreatingArtifactOrOverwritingOtherExtraData() {
        when(documentRepository.findFirstByChatSessionIdOrderByUpdateTimeDesc(11))
                .thenReturn(Optional.empty());
        ReportDocumentSaveDto request = saveRequest(null, 0L, "# 周报\n\n正文");

        ReportWorkspaceVo workspace = service.save(11L, request, user);

        assertThat(workspace.getCurrentDocument())
                .containsEntry("revision", 1L)
                .containsEntry("version", "v1")
                .containsEntry("format", "markdown")
                .containsEntry("content", "# 周报\n\n正文");
        Map<String, Object> extraData = JacksonUtil.toMap(
                workspace.getExtraData(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(extraData).containsKeys("dataVisualization", "workflow", "report");
        Map<String, Object> report = castMap(extraData.get("report"));
        assertThat(castMap(report.get("currentDocument"))).doesNotContainKey("content");
        assertThat((List<?>) report.get("artifacts")).isEmpty();
        verify(revisionRepository).save(any());
        verify(artifactRepository, never()).save(any());
    }

    @Test
    void staleBaseRevisionReturnsConflictAndDoesNotWrite() {
        ReportDocument current = document(3L, "最新正文");
        when(documentRepository.findByDocumentIdAndChatSessionId("doc-1", 11))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.save(
                11L, saveRequest("doc-1", 2L, "过期正文"), user))
                .isInstanceOf(ReportRevisionConflictException.class)
                .hasMessageContaining("刷新");
        verify(documentRepository, never()).save(any());
        verify(revisionRepository, never()).save(any());
    }

    @Test
    void archiveIsCreatedOnlyByExplicitOperationAndKeepsRevisionSnapshot() {
        ReportDocument current = document(4L, "不可变快照");
        when(documentRepository.findByDocumentIdAndChatSessionId("doc-1", 11))
                .thenReturn(Optional.of(current));
        List<ReportArtifact> stored = new ArrayList<>();
        when(artifactRepository.save(any(ReportArtifact.class)))
                .thenAnswer(invocation -> {
                    ReportArtifact artifact = invocation.getArgument(0);
                    stored.add(artifact);
                    return artifact;
                });
        when(artifactRepository.findByChatSessionIdAndCreateByOrderByCreateTimeDesc(11, 7))
                .thenAnswer(invocation -> List.copyOf(stored));
        ReportArchiveDto request = new ReportArchiveDto();
        request.setDocumentId("doc-1");
        request.setBaseRevision(4L);
        request.setName("正式周报");

        ReportWorkspaceVo workspace = service.archive(11L, request, user);

        ArgumentCaptor<ReportArtifact> captor = ArgumentCaptor.forClass(ReportArtifact.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("不可变快照");
        assertThat(captor.getValue().getRevision()).isEqualTo(4L);
        assertThat(workspace.getArtifacts()).hasSize(1);
        assertThat(workspace.getArtifacts().get(0))
                .containsEntry("version", "v4")
                .containsEntry("status", "archived");
    }

    @Test
    void materialsIncludeOnlyOwnedCrossAgentOutputsWithBoundedContent() {
        ChatSession analysisSession = new ChatSession();
        analysisSession.setId(22);
        analysisSession.setCreateBy(7);
        analysisSession.setSessionId("analysis-session");
        analysisSession.setTitle("异常分析");
        analysisSession.setType("agent_data_analysis");
        Message message = new Message("ai", "已完成分析");
        message.setAttachments(List.of(ChatAttachment.builder()
                .fileId("file-2")
                .fileName("告警.csv")
                .parseStatus("success")
                .build()));
        message.setParts(List.of(ChatMessagePart.builder()
                .id("part-2")
                .type("data-analysis-record")
                .title("异常结论")
                .content("高风险事件集中在夜间。")
                .status("completed")
                .build()));
        analysisSession.setMessages(JacksonUtil.toJson(List.of(message)));
        analysisSession.setExtraData("""
                {"dataVisualization":{"chartLibrary":[{"id":"chart-2","name":"夜间趋势"}]},
                 "dataAnalysis":{"records":[{"recordId":"analysis-2","title":"异常检测"}]}}
                """);
        when(chatSessionRepository.findTop50ByCreateByOrderByUpdateTimeDesc(7))
                .thenReturn(List.of(analysisSession));
        when(documentRepository.findTop50ByCreateByOrderByUpdateTimeDesc(7))
                .thenReturn(List.of());
        when(artifactRepository.findTop100ByCreateByOrderByCreateTimeDesc(7))
                .thenReturn(List.of());

        List<Map<String, Object>> materials = service.materials(11L, user);

        assertThat(materials)
                .extracting(item -> item.get("type"))
                .contains("attachment", "agent_output", "chart", "analysis_record");
        assertThat(materials)
                .allSatisfy(item -> {
                    assertThat(item).containsEntry("sessionRecordId", 22);
                    assertThat(item).containsEntry("sessionTitle", "异常分析");
                });

        List<Map<String, Object>> verified = service.validateSourceRefs(
                11L,
                List.of(
                        Map.of("type", "chart", "id", "chart-2"),
                        Map.of("type", "attachment", "id", "forged-file")),
                user);
        assertThat(verified).hasSize(1);
        assertThat(verified.get(0))
                .containsEntry("type", "chart")
                .containsEntry("id", "chart-2");
    }

    private ReportDocumentSaveDto saveRequest(
            String documentId, Long baseRevision, String content) {
        ReportDocumentSaveDto request = new ReportDocumentSaveDto();
        request.setDocumentId(documentId);
        request.setBaseRevision(baseRevision);
        request.setTitle("周报");
        request.setFormat("markdown");
        request.setContent(content);
        request.setOutline(List.of(Map.of("level", 1, "text", "周报")));
        request.setSourceRefs(List.of(Map.of("type", "attachment", "id", "file-1")));
        return request;
    }

    private ReportDocument document(Long revision, String content) {
        ReportDocument document = new ReportDocument();
        document.setDocumentId("doc-1");
        document.setChatSessionId(11);
        document.setTitle("周报");
        document.setFormat("markdown");
        document.setCurrentRevision(revision);
        document.setStatus("draft");
        document.setContentHash("hash");
        document.setContent(content);
        document.setOutline("[]");
        document.setSourceRefs("[]");
        document.setCreateBy(7);
        return document;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
