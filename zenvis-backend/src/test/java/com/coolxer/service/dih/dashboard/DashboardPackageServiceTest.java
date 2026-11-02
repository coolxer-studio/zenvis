package com.coolxer.service.dih.dashboard;

import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.dih.dashboard.DashboardChangeSet;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardPackageServiceTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private MetaDataService metaDataService;
    private DashboardRepository dashboardRepository;
    private DashboardValidator validator;
    private DashboardCompiler compiler;
    private DashboardQuerySampler querySampler;
    private DashboardPackageService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        metaDataService = mock(MetaDataService.class);
        dashboardRepository = mock(DashboardRepository.class);
        validator = mock(DashboardValidator.class);
        compiler = mock(DashboardCompiler.class);
        querySampler = mock(DashboardQuerySampler.class);
        service = new DashboardPackageService(configService, metaDataService,
                dashboardRepository, validator, compiler, querySampler);

        when(configService.configPath(anyString()))
                .thenAnswer(invocation -> tempDir.resolve(invocation.getArgument(0) + "_config").toString());
        when(configService.fileExistsInConfigPath(anyString(), anyString())).thenReturn(false);
        when(dashboardRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(dashboardRepository.findBySource(anyString())).thenReturn(List.of());
        when(validator.validate(any())).thenAnswer(invocation ->
                new DashboardValidator.Result(invocation.getArgument(0), new ArrayList<>()));
        when(querySampler.sample(any())).thenReturn(new DashboardQuerySampler.SampleResult(
                new LinkedHashMap<>(), new ArrayList<>()));
        when(metaDataService.getAllDataEntity()).thenReturn(List.of());
        when(compiler.compile(any(), anyBoolean())).thenAnswer(invocation -> {
            DashboardSpec spec = invocation.getArgument(0);
            String path = DashboardSupport.htmlMode(spec)
                    ? DashboardSupport.htmlPath(spec) : "index.json";
            return new DashboardCompiler.CompiledFiles(
                    new LinkedHashMap<>(Map.of(path, "<html>compiled</html>")),
                    DashboardSupport.htmlMode(spec) ? "HTML_PAGE" : "LOW_CODE_PAGE");
        });
    }

    @Test
    void buildsANewHtmlCandidateWithScopedPreviewAndStableDigests() {
        DashboardSpec spec = spec("user-event-dashboard", "用户事件大屏");

        DashboardValidationResult result = service.build(
                spec, null, "wf-1", "chat-1", "token-1");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getStatus()).isEqualTo("validated");
        assertThat(result.getDashboardVersion()).isEqualTo(1);
        assertThat(result.getMode()).isEqualTo("HTML_PAGE");
        assertThat(result.getSpecDigest()).hasSize(64);
        assertThat(result.getCandidateDigest()).hasSize(64);
        assertThat(result.getValidationDigest()).hasSize(64);
        assertThat(result.getFiles()).extracting(item -> item.getPath())
                .containsExactly("user-event-dashboard.html", "user-event-dashboard.manifest.json");
        assertThat(result.getPreview().get("htmlUrl").toString())
                .contains("/api/v1/dih/chat/workflows/wf-1/dashboard-preview")
                .contains("chat_id=chat-1", "token=token-1");
    }

    @Test
    void requiresAChangesetAndLocksTheModeForManagedDashboards() {
        DashboardSpec existingSpec = spec("user-event-dashboard", "用户事件大屏");
        DashboardManifest existing = publishedManifest(existingSpec, 2, "HTML_PAGE");
        stubExisting(existing, Map.of("user-event-dashboard.html", "<html>old</html>"));

        DashboardValidationResult withoutChanges = service.build(
                spec("user-event-dashboard", "新标题"), null, "wf-1");
        assertThat(codes(withoutChanges)).contains("CHANGESET_REQUIRED");

        DashboardSpec lowCode = spec("user-event-dashboard", "用户事件大屏");
        lowCode.setMode(DashboardSpec.MODE_LOW_CODE);
        DashboardChangeSet modeChange = changes(existing, lowCode);
        DashboardValidationResult modeLocked = service.build(null, modeChange, "wf-2");
        assertThat(codes(modeLocked)).contains("DASHBOARD_MODE_LOCKED");
    }

    @Test
    void rejectsStaleVersionSpecAndFileDigests() {
        DashboardSpec existingSpec = spec("user-event-dashboard", "用户事件大屏");
        DashboardManifest existing = publishedManifest(existingSpec, 2, "HTML_PAGE");
        stubExisting(existing, Map.of("user-event-dashboard.html", "<html>old</html>"));

        DashboardSpec next = spec("user-event-dashboard", "用户事件大屏");
        DashboardChangeSet changes = changes(existing, next);
        changes.setBaseDashboardVersion(1);
        assertThat(codes(service.build(null, changes, "wf-1")))
                .contains("DASHBOARD_VERSION_STALE");

        changes = changes(existing, next);
        changes.setBaseSpecDigest("stale-spec");
        assertThat(codes(service.build(null, changes, "wf-2")))
                .contains("DASHBOARD_SPEC_STALE");

        changes = changes(existing, next);
        changes.setBaseFileDigest("stale-file");
        assertThat(codes(service.build(null, changes, "wf-3")))
                .contains("DASHBOARD_FILE_STALE");
    }

    @Test
    void treatsUnmanagedFilesAndDashboardRecordsAsReadOnlyLegacy() throws Exception {
        Path htmlRoot = tempDir.resolve("html-page_config");
        Files.createDirectories(htmlRoot);
        Files.writeString(htmlRoot.resolve("legacy-dashboard.html"), "<html>legacy</html>");

        DashboardValidationResult fileLegacy = service.build(
                spec("legacy-dashboard", "历史大屏"), null, "wf-1");
        assertThat(codes(fileLegacy)).contains("LEGACY_DASHBOARD_READ_ONLY");

        Dashboard dashboard = new Dashboard();
        dashboard.setId(9);
        dashboard.setCode("record-only-dashboard");
        dashboard.setSource("dashboard:record-only-dashboard");
        when(dashboardRepository.findBySource("dashboard:record-only-dashboard"))
                .thenReturn(List.of(dashboard));
        DashboardValidationResult recordLegacy = service.build(
                spec("record-only-dashboard", "仅有记录的大屏"), null, "wf-2");
        assertThat(codes(recordLegacy)).contains("LEGACY_DASHBOARD_READ_ONLY");
        assertThat(service.legacyReadOnly("record-only-dashboard"))
                .containsEntry("read_only", true)
                .containsEntry("managed", false)
                .containsEntry("dashboard_record_id", 9);
    }

    @Test
    void revalidationBlocksVersionFileAndMetaDriftAfterPreview() {
        DashboardSpec spec = spec("user-event-dashboard", "用户事件大屏");
        DashboardManifest previous = publishedManifest(spec, 2, "HTML_PAGE");
        previous.setFiles(new LinkedHashMap<>(Map.of(
                "user-event-dashboard.html", DashboardSupport.digest("<html>old</html>"))));
        DashboardManifest current = publishedManifest(spec, 3, "HTML_PAGE");
        current.setFiles(new LinkedHashMap<>(previous.getFiles()));
        stubExisting(current, Map.of("user-event-dashboard.html", "<html>old</html>"));
        DashboardPackage packageValue = new DashboardPackage(
                spec, current, Map.of("user-event-dashboard.html", "<html>new</html>"),
                "user-event-dashboard.manifest.json", previous);

        assertThatThrownBy(() -> service.revalidateSnapshot(packageValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本或摘要");

        current.setDashboardVersion(2);
        stubExisting(current, Map.of("user-event-dashboard.html", "<html>tampered</html>"));
        assertThatThrownBy(() -> service.revalidateSnapshot(packageValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("人工修改");

        stubExisting(current, Map.of("user-event-dashboard.html", "<html>old</html>"));
        current.setMetaDigest("old-meta");
        assertThatThrownBy(() -> service.revalidateSnapshot(packageValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Meta");
    }

    @Test
    void newCandidateCannotBeSilentlyTakenOverAfterPreview() {
        DashboardSpec spec = spec("user-event-dashboard", "用户事件大屏");
        DashboardManifest manifest = publishedManifest(spec, 1, "HTML_PAGE");
        DashboardPackage packageValue = new DashboardPackage(
                spec, manifest, Map.of("user-event-dashboard.html", "<html>new</html>"),
                "user-event-dashboard.manifest.json", null);
        when(configService.fileExistsInConfigPath("html-page",
                "user-event-dashboard.manifest.json")).thenReturn(true);

        assertThatThrownBy(() -> service.revalidateSnapshot(packageValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被创建");
    }

    private void stubExisting(DashboardManifest manifest, Map<String, String> contents) {
        String id = manifest.getDashboardId();
        when(configService.fileExistsInConfigPath("html-page", id + ".manifest.json"))
                .thenReturn(true);
        when(configService.readFile("html-page", id + ".manifest.json"))
                .thenReturn(JacksonUtil.toJson(manifest));
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            when(configService.fileExistsInConfigPath("html-page", entry.getKey())).thenReturn(true);
            when(configService.readFile("html-page", entry.getKey())).thenReturn(entry.getValue());
        }
    }

    private DashboardManifest publishedManifest(DashboardSpec spec, int version, String mode) {
        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(version);
        manifest.setName(spec.getName());
        manifest.setMode(mode);
        manifest.setTheme(spec.getTheme());
        manifest.setDashboardType(mode);
        manifest.setSpec(spec);
        manifest.setSpecDigest(DashboardSupport.digestObject(spec));
        manifest.setFileDigest(DashboardSupport.digest("old-file"));
        manifest.setMetaDigest(DashboardSupport.digestObject(List.of()));
        manifest.setPublishStatus("published");
        manifest.setCreatedAt("2026-01-01T00:00:00Z");
        manifest.setUpdatedAt("2026-01-02T00:00:00Z");
        return manifest;
    }

    private DashboardChangeSet changes(DashboardManifest existing, DashboardSpec next) {
        DashboardChangeSet changes = new DashboardChangeSet();
        changes.setDashboardId(existing.getDashboardId());
        changes.setBaseDashboardVersion(existing.getDashboardVersion());
        changes.setBaseSpecDigest(existing.getSpecDigest());
        changes.setBaseFileDigest(existing.getFileDigest());
        changes.setSpec(next);
        return changes;
    }

    private DashboardSpec spec(String id, String name) {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId(id);
        spec.setName(name);
        spec.setTimeField("server_time");
        spec.setBlocks(new ArrayList<>());
        return spec;
    }

    private List<String> codes(DashboardValidationResult result) {
        return result.getDiagnostics().stream().map(DashboardDiagnostic::getCode).toList();
    }
}
