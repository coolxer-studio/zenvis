package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppChangeSet;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataAppPackageServiceTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private MetaDataService metaDataService;
    private DataAppValidator validator;
    private DataAppCompiler compiler;
    private DataAppQuerySampler querySampler;
    private DataAppPackageService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        metaDataService = mock(MetaDataService.class);
        validator = mock(DataAppValidator.class);
        compiler = mock(DataAppCompiler.class);
        querySampler = mock(DataAppQuerySampler.class);
        service = new DataAppPackageService(
                configService, metaDataService, validator, compiler, querySampler);

        when(configService.configPath(anyString()))
                .thenAnswer(invocation -> tempDir.resolve(invocation.getArgument(0) + "_config").toString());
        when(configService.fileExistsInConfigPath(anyString(), anyString())).thenReturn(false);
        when(validator.validate(any())).thenAnswer(invocation ->
                new DataAppValidator.Result(invocation.getArgument(0), new ArrayList<>()));
        when(querySampler.sample(any())).thenReturn(new DataAppQuerySampler.SampleResult(
                new LinkedHashMap<>(), new ArrayList<>()));
        when(metaDataService.getAllDataEntity()).thenReturn(List.of());
        when(compiler.compile(any(), anyBoolean(), eq(false))).thenAnswer(invocation ->
                compiled(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void buildsSinglePageCandidateWithScopedPreview() {
        DataAppSpec spec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));

        DataAppValidationResult result = service.build(
                spec, null, "wf-1", "chat-1", "token-1");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getAppVersion()).isEqualTo(1);
        assertThat(result.getMode()).isEqualTo("LOW_CODE_PAGE");
        assertThat(result.getSpecDigest()).isEqualTo(DataAppSupport.digestObject(result.getSpec()));
        assertThat(result.getCandidateDigest()).hasSize(64);
        assertThat(result.getValidationDigest()).hasSize(64);
        assertThat(result.getFiles()).extracting(item -> item.getPath())
                .containsExactly("index.json", "_app.json");
        assertThat(result.getMenu().getFiles()).containsOnlyKeys("index.json");
        assertThat(result.getPreview().get("siteUrl").toString())
                .contains("/api/v1/dih/chat/workflows/wf-1/app-preview/site")
                .contains("chat_id=chat-1", "token=token-1");
    }

    @Test
    void buildsMultiPageCandidateWithSiteNavigation() {
        DataAppSpec spec = spec("user-event-analysis", "用户事件分析",
                page("overview", "总览"), page("details", "明细"));

        DataAppValidationResult result = service.build(spec, null, "wf-1");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getMode()).isEqualTo("LOW_CODE_APP");
        assertThat(result.getFiles()).extracting(item -> item.getPath())
                .containsExactly("pages/overview.json", "pages/details.json", "site.json", "_app.json");
    }

    @Test
    void addsPagesAndUpgradesSinglePageAppWhileKeepingCompatibilityEntry() {
        DataAppSpec oldSpec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));
        DataAppManifest existing = existingManifest(oldSpec, "LOW_CODE_PAGE", 2);
        stubExisting(existing, Map.of("index.json", "old-index"));
        DataAppChangeSet changes = new DataAppChangeSet();
        changes.setAppId("user-event-analysis");
        changes.setBaseAppVersion(2);
        changes.setBaseSpecDigest(existing.getSpecDigest());
        changes.setPutPages(new ArrayList<>(List.of(page("details", "明细"))));

        DataAppValidationResult result = service.build(null, changes, "wf-2");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getPreviousMode()).isEqualTo("LOW_CODE_PAGE");
        assertThat(result.getMode()).isEqualTo("LOW_CODE_APP");
        assertThat(result.getAppVersion()).isEqualTo(3);
        assertThat(result.getSpec().getPages()).extracting(DataAppSpec.DataAppPage::getPageId)
                .containsExactly("overview", "details");
        assertThat(result.getMenu().getCompatibilityFiles()).containsExactly("index.json");
        assertThat(result.getDeletedFiles()).isEmpty();
    }

    @Test
    void modifiesAndDeletesPagesWithoutDowngradingAnExistingMultiPageApp() {
        DataAppSpec oldSpec = spec("user-event-analysis", "用户事件分析",
                page("overview", "总览"), page("details", "明细"));
        DataAppManifest existing = existingManifest(oldSpec, "LOW_CODE_APP", 4);
        stubExisting(existing, Map.of(
                "pages/overview.json", "old-overview",
                "pages/details.json", "old-details",
                "site.json", "old-site"));
        DataAppSpec.DataAppPage changedOverview = page("overview", "新的总览");
        DataAppChangeSet changes = new DataAppChangeSet();
        changes.setAppId("user-event-analysis");
        changes.setBaseAppVersion(4);
        changes.setBaseSpecDigest(existing.getSpecDigest());
        changes.setPutPages(new ArrayList<>(List.of(changedOverview)));
        changes.setDeletePageIds(new ArrayList<>(List.of("details")));

        DataAppValidationResult result = service.build(null, changes, "wf-3");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getMode()).isEqualTo("LOW_CODE_APP");
        assertThat(result.getSpec().getPages()).singleElement()
                .extracting(DataAppSpec.DataAppPage::getTitle).isEqualTo("新的总览");
        assertThat(result.getDeletedFiles()).containsExactly("pages/details.json");
    }

    @Test
    void refusesHomePageAndLastPageDeletion() {
        DataAppSpec twoPages = spec("user-event-analysis", "用户事件分析",
                page("overview", "总览"), page("details", "明细"));
        DataAppManifest existing = existingManifest(twoPages, "LOW_CODE_APP", 2);
        stubExisting(existing, Map.of(
                "pages/overview.json", "overview",
                "pages/details.json", "details",
                "site.json", "site"));
        DataAppChangeSet deleteHome = changes(existing, List.of(), List.of("overview"));

        DataAppValidationResult homeResult = service.build(null, deleteHome, "wf-4");

        assertThat(homeResult.isPassed()).isFalse();
        assertThat(homeResult.getStatus()).isEqualTo("invalid");
        assertThat(codes(homeResult)).contains("HOME_PAGE_DELETED");

        DataAppSpec single = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));
        DataAppManifest singleExisting = existingManifest(single, "LOW_CODE_PAGE", 1);
        stubExisting(singleExisting, Map.of("index.json", "index"));
        DataAppChangeSet deleteLast = changes(singleExisting, List.of(), List.of("overview"));

        DataAppValidationResult lastResult = service.build(null, deleteLast, "wf-5");

        assertThat(lastResult.isPassed()).isFalse();
        assertThat(codes(lastResult)).contains("LAST_PAGE_DELETE_FORBIDDEN");
    }

    @Test
    void rejectsStaleVersionsDigestsAndManagedFileDrift() {
        DataAppSpec oldSpec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));
        DataAppManifest existing = existingManifest(oldSpec, "LOW_CODE_PAGE", 3);
        stubExisting(existing, Map.of("index.json", "changed-by-human"), false);

        DataAppChangeSet staleVersion = changes(existing, List.of(page("details", "明细")), List.of());
        staleVersion.setBaseAppVersion(2);
        assertThat(codes(service.build(null, staleVersion, "wf-6")))
                .contains("APP_VERSION_STALE", "APP_FILE_DRIFT");

        DataAppChangeSet missingDigest = changes(existing, List.of(page("details", "明细")), List.of());
        missingDigest.setBaseSpecDigest("");
        assertThat(codes(service.build(null, missingDigest, "wf-7")))
                .contains("APP_SPEC_DIGEST_REQUIRED", "APP_FILE_DRIFT");

        DataAppChangeSet staleDigest = changes(existing, List.of(page("details", "明细")), List.of());
        staleDigest.setBaseSpecDigest("another-digest");
        assertThat(codes(service.build(null, staleDigest, "wf-8")))
                .contains("APP_SPEC_STALE", "APP_FILE_DRIFT");
    }

    @Test
    void treatsLegacyDirectoriesAsReadOnlyAndRequiresChangesetsForManagedApps() throws Exception {
        Path legacy = tempDir.resolve("legacy-app_config");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("index.json"), "{}");

        DataAppValidationResult legacyResult = service.build(
                spec("legacy-app", "旧应用", page("overview", "总览")), null, "wf-9");

        assertThat(legacyResult.isPassed()).isFalse();
        assertThat(codes(legacyResult)).contains("LEGACY_APP_READ_ONLY");

        DataAppSpec oldSpec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));
        DataAppManifest existing = existingManifest(oldSpec, "LOW_CODE_PAGE", 1);
        stubExisting(existing, Map.of("index.json", "index"));
        DataAppValidationResult noChanges = service.build(null, null, "wf-10");
        assertThat(noChanges.getAppId()).isNull();

        DataAppValidationResult managedWithoutBase = service.build(
                spec("user-event-analysis", "用户事件分析", page("details", "明细")), null, "wf-11");
        assertThat(codes(managedWithoutBase)).contains("CHANGESET_REQUIRED");
    }

    @Test
    void revalidatesLockedPackageAgainstCurrentMetaAndFiles() {
        DataAppSpec spec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));
        DataAppManifest manifest = existingManifest(spec, "LOW_CODE_PAGE", 1);
        manifest.setMetaDigest("old-meta-digest");
        DataAppPackage packageValue = new DataAppPackage(
                spec, manifest, Map.of("index.json", "{}"), List.of(), null);

        assertThatThrownBy(() -> service.revalidateSnapshot(packageValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Meta");
    }

    @Test
    void metaDigestChangesWhenEnumMetadataChanges() {
        DataEntity entity = new DataEntity();
        entity.setName("user_event");
        entity.setTableName("user_event");
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("user_event");
        attribute.setName("event_type");
        attribute.setColumnName("event_type");
        attribute.setColumnType("String");
        attribute.setEnums(new ArrayList<>(List.of("login")));
        when(metaDataService.getAllDataEntity()).thenReturn(List.of(entity));
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(List.of(attribute));
        DataAppSpec spec = spec("user-event-analysis", "用户事件分析", page("overview", "总览"));

        DataAppValidationResult first = service.build(spec, null, "wf-1");
        attribute.setEnums(new ArrayList<>(List.of("login", "logout")));
        DataAppValidationResult second = service.build(spec, null, "wf-1");

        assertThat(first.getMetaDigest()).isNotBlank();
        assertThat(second.getMetaDigest()).isNotBlank().isNotEqualTo(first.getMetaDigest());
    }

    private void stubExisting(DataAppManifest manifest, Map<String, String> contents) {
        stubExisting(manifest, contents, true);
    }

    private void stubExisting(DataAppManifest manifest,
                              Map<String, String> contents,
                              boolean alignDigests) {
        if (alignDigests) {
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                if (manifest.getFiles().containsKey(entry.getKey())) {
                    manifest.getFiles().put(entry.getKey(), DataAppSupport.digest(entry.getValue()));
                }
            }
        }
        when(configService.fileExistsInConfigPath(manifest.getAppId(), DataAppPackageService.MANIFEST_FILE))
                .thenReturn(true);
        when(configService.readFile(manifest.getAppId(), DataAppPackageService.MANIFEST_FILE))
                .thenReturn(JacksonUtil.toJson(manifest));
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            when(configService.fileExistsInConfigPath(manifest.getAppId(), entry.getKey())).thenReturn(true);
            when(configService.readFile(manifest.getAppId(), entry.getKey())).thenReturn(entry.getValue());
        }
        for (String compatibility : manifest.getCompatibilityFiles()) {
            when(configService.fileExistsInConfigPath(manifest.getAppId(), compatibility))
                    .thenReturn(contents.containsKey(compatibility));
            if (contents.containsKey(compatibility)) {
                when(configService.readFile(manifest.getAppId(), compatibility))
                        .thenReturn(contents.get(compatibility));
            }
        }
    }

    private DataAppManifest existingManifest(DataAppSpec spec, String menuType, int version) {
        DataAppManifest manifest = new DataAppManifest();
        manifest.setAppId(spec.getAppId());
        manifest.setAppVersion(version);
        manifest.setMode(menuType);
        manifest.setMenuType(menuType);
        manifest.setName(spec.getName());
        manifest.setHomePageId(spec.getHomePageId());
        manifest.setSpec(spec);
        manifest.setSpecDigest(DataAppSupport.digestObject(spec));
        manifest.setPublishStatus("published");
        manifest.setCreatedAt("2026-01-01T00:00:00Z");
        manifest.setUpdatedAt("2026-01-02T00:00:00Z");
        List<DataAppManifest.DataAppPageRef> pages = new ArrayList<>();
        for (DataAppSpec.DataAppPage page : spec.getPages()) {
            DataAppManifest.DataAppPageRef ref = new DataAppManifest.DataAppPageRef();
            ref.setPageId(page.getPageId());
            ref.setTitle(page.getTitle());
            ref.setPath(DataAppSupport.fileNameForPage(
                    spec.getAppId(), page.getPageId(), "LOW_CODE_PAGE".equals(menuType)));
            pages.add(ref);
        }
        manifest.setPages(pages);
        Map<String, String> files = new LinkedHashMap<>();
        pages.forEach(page -> files.put(page.getPath(), DataAppSupport.digest("stub")));
        manifest.setFiles(files);
        return manifest;
    }

    private DataAppChangeSet changes(DataAppManifest existing,
                                     List<DataAppSpec.DataAppPage> putPages,
                                     List<String> deletePageIds) {
        DataAppChangeSet changes = new DataAppChangeSet();
        changes.setAppId(existing.getAppId());
        changes.setBaseAppVersion(existing.getAppVersion());
        changes.setBaseSpecDigest(existing.getSpecDigest());
        changes.setPutPages(new ArrayList<>(putPages));
        changes.setDeletePageIds(new ArrayList<>(deletePageIds));
        return changes;
    }

    private DataAppCompiler.CompiledFiles compiled(DataAppSpec spec, boolean single) {
        Map<String, String> files = new LinkedHashMap<>();
        if (single) {
            files.put("index.json", "{\"type\":\"page\"}");
        } else {
            for (DataAppSpec.DataAppPage page : spec.getPages()) {
                files.put("pages/" + page.getPageId() + ".json", "{\"type\":\"page\"}");
            }
            files.put("site.json", "{\"data\":{}}");
        }
        return new DataAppCompiler.CompiledFiles(files, single ? "LOW_CODE_PAGE" : "LOW_CODE_APP");
    }

    private DataAppSpec spec(String appId, String name, DataAppSpec.DataAppPage... pages) {
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId(appId);
        spec.setName(name);
        spec.setHomePageId(pages[0].getPageId());
        spec.setPages(new ArrayList<>(List.of(pages)));
        return spec;
    }

    private DataAppSpec.DataAppPage page(String id, String title) {
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId(id);
        page.setTitle(title);
        page.setBlocks(new ArrayList<>());
        return page;
    }

    private List<String> codes(DataAppValidationResult result) {
        return result.getDiagnostics().stream().map(DataAppDiagnostic::getCode).toList();
    }
}
