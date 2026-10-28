package com.coolxer.service.dih;

import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.ReportArtifact;
import com.coolxer.dao.mysql.entity.ReportDocument;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.ReportArtifactRepository;
import com.coolxer.dao.mysql.repository.ReportDocumentRepository;
import com.coolxer.dao.mysql.repository.ReportRevisionRepository;
import com.coolxer.model.dih.dto.ReportArchiveDto;
import com.coolxer.model.dih.dto.ReportDocumentSaveDto;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.model.core.vo.MinioObjectInfo;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDocumentServiceTest {

    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final ReportDocumentRepository documentRepository = mock(ReportDocumentRepository.class);
    private final ReportRevisionRepository revisionRepository = mock(ReportRevisionRepository.class);
    private final ReportArtifactRepository artifactRepository = mock(ReportArtifactRepository.class);
    private final MinioService minioService = mock(MinioService.class);

    private ReportDocumentService service;
    private ChatSession session;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ReportDocumentService(
                chatSessionRepository,
                documentRepository,
                revisionRepository,
                artifactRepository,
                minioService,
                new MinioProperties());
        when(minioService.isFile(anyString(), eq("zenvis-reports"))).thenReturn(false);
        when(minioService.uploadStream(anyString(), any(InputStream.class), anyLong(), anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(minioService.getObjectInfo(anyString(), eq("zenvis-reports")))
                .thenReturn(new MinioObjectInfo(16, "text/markdown", "etag", Instant.now()));
        when(minioService.downloadFile(anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> new ByteArrayInputStream("不可变快照".getBytes(StandardCharsets.UTF_8)));
        session = new ChatSession();
        session.setId(11);
        session.setCreateBy(7);
        session.setExtraData("""
                {"visualizationChart":{"chartLibrary":[{"id":"chart-1"}]},"workflow":{"state":"running"}}
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
    void saveCreatesRevisionWithoutCreatingArtifactOrOverwritingOtherExtraData() throws Exception {
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
        assertThat(extraData).containsKeys("visualizationChart", "workflow", "report");
        Map<String, Object> report = castMap(extraData.get("report"));
        assertThat(castMap(report.get("currentDocument"))).doesNotContainKey("content");
        assertThat((List<?>) report.get("artifacts")).isEmpty();
        ArgumentCaptor<com.coolxer.dao.mysql.entity.ReportRevision> revisionCaptor =
                ArgumentCaptor.forClass(com.coolxer.dao.mysql.entity.ReportRevision.class);
        verify(revisionRepository).save(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getContent()).isNull();
        ArgumentCaptor<InputStream> uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(minioService).uploadStream(
                eq("documents/" + workspace.getCurrentDocument().get("documentId")
                        + "/revisions/1/content.md"),
                uploadCaptor.capture(), anyLong(), eq("text/markdown; charset=utf-8"), eq("zenvis-reports"));
        assertThat(new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("# 周报\n\n正文");
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
    void rollbackDeletesRevisionObjectCreatedByFailedTransaction() throws Exception {
        when(documentRepository.findFirstByChatSessionIdOrderByUpdateTimeDesc(11))
                .thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.save(11L, saveRequest(null, 0L, "# 回滚正文"), user);

            ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
            verify(minioService).uploadStream(
                    objectKey.capture(), any(InputStream.class), anyLong(),
                    eq("text/markdown; charset=utf-8"), eq("zenvis-reports"));
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(minioService).deleteFile(objectKey.getValue(), "zenvis-reports");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
        assertThat(captor.getValue().getContent()).isNull();
        assertThat(captor.getValue().getRevision()).isEqualTo(4L);
        assertThat(workspace.getArtifacts()).hasSize(1);
        assertThat(workspace.getArtifacts().get(0))
                .containsEntry("version", "v4")
                .containsEntry("status", "archived");
    }

    @Test
    void chartLibraryAggregatesOwnedSessionsDeduplicatesAndKeepsFullOptions() {
        session.setSessionId("report-session");
        session.setTitle("报表会话");
        session.setExtraData("""
                {"visualizationChart":{"chartLibrary":[
                  {"id":"chart-1","name":"旧趋势","addedAt":"2026-10-29T05:00:00Z",
                   "echartsOption":{"series":[{"type":"line","data":[1]}]}}
                ]}}
                """);

        String largeMarker = "x".repeat(2_500);
        ChatSession chartSession = new ChatSession();
        chartSession.setId(22);
        chartSession.setCreateBy(7);
        chartSession.setSessionId("chart-session");
        chartSession.setTitle("临时图表演示");
        chartSession.setExtraData("""
                {"visualizationChart":{"chartLibrary":[
                  {"id":"chart-1","name":"最新趋势","addedAt":"2026-10-29T07:00:00Z",
                   "echartsOption":{"series":[{"type":"bar","data":[2]}]}},
                  {"id":"chart-2","artifactId":"artifact-2","name":"配置快照",
                   "addedAt":"2026-10-29T06:00:00Z",
                   "config":{"type":"chart","config":{"series":[{"type":"pie","data":[3]}]}}},
                  {"id":"chart-3","name":"嵌套快照","addedAt":"2026-10-29T04:00:00Z",
                   "echarts":{"chart_type":"line","option":{"marker":"%s"}}}
                ]}}
                """.formatted(largeMarker));

        ChatSession otherUserSession = new ChatSession();
        otherUserSession.setId(33);
        otherUserSession.setCreateBy(8);
        otherUserSession.setExtraData("""
                {"visualizationChart":{"chartLibrary":[{"id":"other-chart"}]}}
                """);
        ChatSession corruptSession = new ChatSession();
        corruptSession.setId(44);
        corruptSession.setCreateBy(7);
        corruptSession.setExtraData("{broken");

        when(chatSessionRepository.findAllByCreateByOrderByUpdateTimeDesc(7))
                .thenReturn(List.of(session, chartSession, otherUserSession, corruptSession));

        List<Map<String, Object>> charts = service.chartLibrary(user);

        assertThat(charts).hasSize(3);
        assertThat(charts).extracting(item -> item.get("id"))
                .containsExactly("chart-1", "chart-2", "chart-3");
        assertThat(charts).allSatisfy(item -> {
            assertThat(item).containsEntry("type", "chart")
                    .containsEntry("origin", "chart_library");
        });
        assertThat(charts.get(0))
                .containsEntry("name", "最新趋势")
                .containsEntry("sessionRecordId", 22);
        assertThat((List<?>) castMap(charts.get(0).get("echartsOption")).get("series"))
                .hasSize(1);
        assertThat(castMap(charts.get(1).get("echartsOption")))
                .containsKey("series");
        assertThat(String.valueOf(castMap(charts.get(2).get("echartsOption"))
                .get("marker"))).hasSize(2_500);
        assertThat(service.materials(11L, user)).isEqualTo(charts);
        assertThat(service.materials(user)).isEqualTo(charts);

        List<Map<String, Object>> verified = service.validateSourceRefs(
                11L,
                List.of(
                        Map.of("type", "chart", "id", "chart-2"),
                        Map.of("type", "attachment", "id", "forged-file")),
                user);
        assertThat(verified).hasSize(1);
        assertThat(verified.get(0))
                .containsEntry("type", "chart")
                .containsEntry("id", "chart-2")
                .containsEntry("artifactId", "artifact-2")
                .doesNotContainKey("echartsOption");
    }

    @Test
    void validateSourceRefsPreservesHistoricalNonChartSourcesButRejectsNewOnes() {
        ReportDocument current = document(3L, "正文");
        current.setSourceRefs(JacksonUtil.toJson(List.of(
                Map.of("type", "attachment", "id", "file-old", "name", "历史附件"),
                Map.of("type", "analysis_record", "id", "analysis-old", "name", "历史分析"))));
        when(documentRepository.findFirstByChatSessionIdAndCreateByOrderByUpdateTimeDesc(11, 7))
                .thenReturn(Optional.of(current));
        when(chatSessionRepository.findAllByCreateByOrderByUpdateTimeDesc(7))
                .thenReturn(List.of());

        List<Map<String, Object>> verified = service.validateSourceRefs(
                11L,
                List.of(
                        Map.of("type", "attachment", "id", "file-old"),
                        Map.of("type", "analysis_record", "id", "analysis-old"),
                        Map.of("type", "attachment", "id", "file-new")),
                user);

        assertThat(verified).extracting(item -> item.get("id"))
                .containsExactly("file-old", "analysis-old");
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
