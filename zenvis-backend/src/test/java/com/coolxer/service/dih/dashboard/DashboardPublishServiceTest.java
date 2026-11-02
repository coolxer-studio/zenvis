package com.coolxer.service.dih.dashboard;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import com.coolxer.service.config.ConfigService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardPublishServiceTest {

    @TempDir
    Path tempDir;

    private InMemoryConfigService configService;
    private DashboardRepository dashboardRepository;
    private DashboardPackageService packageService;
    private DashboardPublishService service;
    private final Map<Integer, Dashboard> rows = new LinkedHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(21);
    private volatile boolean failSave;
    private volatile boolean wrongReadBack;

    @BeforeEach
    void setUp() {
        configService = new InMemoryConfigService(tempDir);
        dashboardRepository = mock(DashboardRepository.class);
        packageService = mock(DashboardPackageService.class);
        service = new DashboardPublishService(configService, dashboardRepository, packageService);

        when(packageService.revalidateSnapshot(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardRepository.findBySource(anyString())).thenAnswer(invocation ->
                rows.values().stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getSource()))
                        .toList());
        when(dashboardRepository.findByCode(anyString())).thenAnswer(invocation ->
                rows.values().stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getCode()))
                        .findFirst());
        when(dashboardRepository.findById(anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(invocation.<Integer>getArgument(0))));
        when(dashboardRepository.save(any())).thenAnswer(invocation -> {
            if (failSave) {
                throw new IllegalStateException("Dashboard 服务不可用");
            }
            Dashboard value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(nextId.getAndIncrement());
            }
            if (wrongReadBack) {
                value.setCode("wrong-code");
            }
            rows.put(value.getId(), value);
            return value;
        });
        doAnswer(invocation -> {
            Dashboard value = invocation.getArgument(0);
            rows.remove(value.getId());
            return null;
        }).when(dashboardRepository).delete(any());
    }

    @Test
    void firstPublishCreatesConfigurationAndDashboardWithoutChangingDefault() {
        DashboardPackage packageValue = packageValue(1, null);

        DashboardApplyResult result = service.apply(session(packageValue),
                "candidate-1", "validation-1");

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(result.isApplied()).isTrue();
        assertThat(result.getDashboardRecordId()).isNotNull();
        assertThat(result.getDashboardType()).isEqualTo("HTML_PAGE");
        assertThat(configService.read("html-page", "user-event-dashboard.html")).contains("version-1");
        DashboardManifest manifest = JacksonUtil.toObject(
                configService.read("html-page", "user-event-dashboard.manifest.json"),
                DashboardManifest.class);
        assertThat(manifest.getPublishStatus()).isEqualTo("published");
        assertThat(manifest.getDashboardRecordId()).isEqualTo(result.getDashboardRecordId());
        Dashboard saved = rows.get(result.getDashboardRecordId());
        assertThat(saved.getCode()).isEqualTo("user-event-dashboard");
        assertThat(saved.getSource()).isEqualTo("dashboard:user-event-dashboard");
        assertThat(saved.getType()).isEqualTo(DashboardType.HTML_PAGE);
        assertThat(saved.getIsDefault()).isFalse();
    }

    @Test
    void updateKeepsTheExistingRecordAndUserDefaultFlag() {
        Dashboard previous = dashboard(21, "用户事件大屏", DashboardType.HTML_PAGE,
                "dashboard:user-event-dashboard", true);
        rows.put(21, previous);
        DashboardPackage packageValue = packageValue(2, previous);

        DashboardApplyResult result = service.apply(session(packageValue),
                "candidate-2", "validation-2");

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(rows).hasSize(1);
        Dashboard saved = rows.get(21);
        assertThat(saved.getName()).isEqualTo("用户事件大屏");
        assertThat(saved.getIsDefault()).isTrue();
        assertThat(configService.read("html-page", "user-event-dashboard.html")).contains("version-2");
    }

    @Test
    void repeatedApplyIsIdempotentForTheSameManagedDashboard() {
        DashboardPackage packageValue = packageValue(1, null);
        DashboardPreviewRegistry.PreviewSession session = session(packageValue);

        DashboardApplyResult first = service.apply(session, "candidate-1", "validation-1");
        DashboardApplyResult second = service.apply(session, "candidate-1", "validation-1");

        assertThat(first.getStatus()).isEqualTo("verified");
        assertThat(second.getStatus()).isEqualTo("verified");
        assertThat(rows).hasSize(1);
        assertThat(second.getDashboardRecordId()).isEqualTo(first.getDashboardRecordId());
    }

    @Test
    void dashboardWriteFailureRollsBackFilesAndDashboardSnapshot() {
        Dashboard previous = dashboard(21, "旧标题", DashboardType.HTML_PAGE,
                "dashboard:user-event-dashboard", true);
        rows.put(21, previous);
        configService.write("html-page", "user-event-dashboard.html", "<html>old-version</html>");
        DashboardPackage packageValue = packageValue(2, previous);
        failSave = true;

        DashboardApplyResult result = service.apply(session(packageValue),
                "candidate-2", "validation-2");

        assertThat(result.getStatus()).isEqualTo("blocked");
        assertThat(result.isApplied()).isFalse();
        assertThat(result.getDiagnostics()).extracting(item -> item.getCode())
                .contains("DASHBOARD_WRITE_FAILED");
        assertThat(configService.read("html-page", "user-event-dashboard.html"))
                .isEqualTo("<html>old-version</html>");
        Dashboard restored = rows.get(21);
        assertThat(restored.getName()).isEqualTo("旧标题");
        assertThat(restored.getIsDefault()).isTrue();
    }

    @Test
    void dashboardReadBackMismatchBlocksAndRollsBack() {
        DashboardPackage packageValue = packageValue(1, null);
        wrongReadBack = true;

        DashboardApplyResult result = service.apply(session(packageValue),
                "candidate-1", "validation-1");

        assertThat(result.getStatus()).isEqualTo("blocked");
        assertThat(result.getDiagnostics()).extracting(item -> item.getCode())
                .contains("DASHBOARD_WRITE_FAILED");
        assertThat(configService.exists("html-page", "user-event-dashboard.html")).isFalse();
        assertThat(rows).isEmpty();
    }

    @Test
    void rejectsDigestMismatchAndUnsafePathsBeforeLeavingSideEffects() {
        DashboardPackage packageValue = packageValue(1, null);
        DashboardApplyResult mismatch = service.apply(session(packageValue),
                "wrong-candidate", "wrong-validation");
        assertThat(mismatch.getStatus()).isEqualTo("blocked");
        assertThat(configService.operations()).isEmpty();

        Map<String, String> files = new LinkedHashMap<>(packageValue.getFiles());
        files.put("../escape.html", "<html>bad</html>");
        DashboardPackage unsafe = new DashboardPackage(
                packageValue.getSpec(), packageValue.getManifest(), files,
                packageValue.getManifestPath(), packageValue.getPreviousManifest());
        DashboardApplyResult unsafeResult = service.apply(session(unsafe),
                "candidate-1", "validation-1");
        assertThat(unsafeResult.getStatus()).isEqualTo("blocked");
        assertThat(unsafeResult.getDiagnostics()).extracting(item -> item.getCode())
                .contains("DASHBOARD_WRITE_FAILED");
    }

    private DashboardPackage packageValue(int version, Dashboard previous) {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        spec.setMode(DashboardSpec.MODE_HTML);
        spec.setTheme(DashboardSpec.THEME_DARK_TECH);
        spec.setTimeField("server_time");

        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(version);
        manifest.setName(spec.getName());
        manifest.setMode(spec.getMode());
        manifest.setTheme(spec.getTheme());
        manifest.setDashboardType("HTML_PAGE");
        manifest.setSpec(spec);
        manifest.setSpecDigest(DashboardSupport.digestObject(spec));
        manifest.setFileDigest("file-digest-" + version);
        manifest.setMetaDigest("meta-digest");
        manifest.setPublishStatus("validated");
        manifest.setCreatedAt("2026-01-01T00:00:00Z");
        manifest.setUpdatedAt("2026-01-02T00:00:00Z");
        if (previous != null) {
            manifest.setDashboardRecordId(previous.getId());
            manifest.setDashboardCode(previous.getCode());
        }

        Map<String, String> files = new LinkedHashMap<>();
        files.put("user-event-dashboard.html", "<html>version-" + version + "</html>");
        files.put("user-event-dashboard.manifest.json", JacksonUtil.toJson(manifest));
        DashboardManifest previousManifest = previous == null ? null : manifest;
        return new DashboardPackage(spec, manifest, files,
                "user-event-dashboard.manifest.json", previousManifest);
    }

    private DashboardPreviewRegistry.PreviewSession session(DashboardPackage packageValue) {
        DashboardValidationResult validation = new DashboardValidationResult();
        validation.setPassed(true);
        validation.setStatus("validated");
        validation.setSpec(packageValue.getSpec());
        validation.setCandidateDigest("candidate-" + packageValue.getManifest().getDashboardVersion());
        validation.setValidationDigest("validation-" + packageValue.getManifest().getDashboardVersion());
        return new DashboardPreviewRegistry.PreviewSession(
                "wf-1", "chat-1", 7, "token-1",
                validation.getCandidateDigest(), validation.getValidationDigest(),
                validation, packageValue, Map.of(), Instant.now().plusSeconds(1800));
    }

    private Dashboard dashboard(int id, String name, DashboardType type,
                                String source, boolean isDefault) {
        Dashboard dashboard = new Dashboard();
        dashboard.setId(id);
        dashboard.setName(name);
        dashboard.setCode("user-event-dashboard");
        dashboard.setType(type);
        dashboard.setSource(source);
        dashboard.setHtmlPath("user-event-dashboard.html");
        dashboard.setIsDefault(isDefault);
        return dashboard;
    }

    private static final class InMemoryConfigService implements ConfigService {

        private final Path root;
        private final Map<String, Map<String, String>> files = new LinkedHashMap<>();
        private final List<String> operations = new ArrayList<>();

        private InMemoryConfigService(Path root) {
            this.root = root;
        }

        private void write(String type, String path, String content) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(path, content);
        }

        private String read(String type, String path) {
            return files.getOrDefault(type, Map.of()).get(path);
        }

        private boolean exists(String type, String path) {
            return read(type, path) != null;
        }

        private List<String> operations() {
            return operations;
        }

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            return List.of();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            return null;
        }

        @Override
        public String readFile(String type, String fileName) {
            return read(type, fileName);
        }

        @Override
        public void modifyConfig(String type, ConfigDto configDto) {
            operations.add("modify:" + configDto.getFileName());
            write(type, configDto.getFileName(), configDto.getText());
        }

        @Override
        public boolean addFile(String type, String fileName) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).putIfAbsent(fileName, "");
            return true;
        }

        @Override
        public boolean renameFile(String type, String originalFile, String newFile) {
            return false;
        }

        @Override
        public ConfigPathOperationResult renameEntry(String type, String sourcePath, String targetName) {
            return ConfigPathOperationResult.failure("rename", sourcePath, "unsupported");
        }

        @Override
        public boolean deleteFile(String type, String fileName) {
            return files.getOrDefault(type, Map.of()).remove(fileName) != null;
        }

        @Override
        public ConfigPathOperationResult deleteEntry(String type, String sourcePath, boolean recursive) {
            boolean removed = files.getOrDefault(type, Map.of()).remove(sourcePath) != null;
            operations.add("delete:" + sourcePath);
            return new ConfigPathOperationResult()
                    .setSuccess(removed)
                    .setOperation("delete")
                    .setSourcePath(sourcePath)
                    .setChanged(removed)
                    .setApplied(removed);
        }

        @Override
        public String configPath(String type) {
            return root.resolve(type + "_config").toString();
        }

        @Override
        public void applyConfig(String type, ConfigDto configDto) {
            modifyConfig(type, configDto);
        }

        @Override
        public boolean addRootPath(String type) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
            return true;
        }

        @Override
        public boolean ensureRootPath(String type) {
            return addRootPath(type);
        }

        @Override
        public boolean fileExistsInConfigPath(String type, String fileName) {
            return exists(type, fileName);
        }
    }
}
