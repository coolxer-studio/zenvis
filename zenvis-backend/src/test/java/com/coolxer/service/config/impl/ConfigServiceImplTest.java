package com.coolxer.service.config.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.retrieval.MetaDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigServiceImplTest {

    @TempDir
    Path configRoot;

    @Test
    void ensureRootPathCreatesConfigDirectoryIdempotently() {
        ConfigServiceImpl service = createService();

        Path expectedPath = configRoot.resolve("inspection-dashboard_config");

        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
    }

    @Test
    void addFileCreatesEmptyFileWithoutOverwritingExistingFile() throws IOException {
        ConfigServiceImpl service = createService();
        service.ensureRootPath("meta");

        assertThat(service.addFile("meta", "probe_event.json")).isTrue();

        Path target = configRoot.resolve("meta_config/probe_event.json");
        assertThat(Files.readString(target)).isEmpty();
        assertThat(service.addFile("meta", "probe_event.json")).isFalse();
        assertThat(Files.readString(target)).isEmpty();
    }

    @Test
    void supportsNestedConfigFileLifecycle() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path rootFile = typeRoot.resolve("index.html");
        Path nestedFile = typeRoot.resolve("com.coolxer.plugin.probe/pages/overview.html");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(rootFile, "root");
        Files.writeString(nestedFile, "nested");

        assertThat(service.fileExistsInConfigPath("html-page", "index.html")).isTrue();
        assertThat(service.fileExistsInConfigPath(
                "html-page", "com.coolxer.plugin.probe/pages/overview.html")).isTrue();
        assertThat(service.readFile(
                "html-page", "com.coolxer.plugin.probe/pages/overview.html")).isEqualTo("nested");

        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("com.coolxer.plugin.probe/pages/overview.html");
        configDto.setText("updated");
        service.modifyConfig("html-page", configDto);
        assertThat(Files.readString(nestedFile)).isEqualTo("updated");

        assertThat(service.renameFile(
                "html-page",
                "com.coolxer.plugin.probe/pages/overview.html",
                "com.coolxer.plugin.probe/pages/dashboard.html")).isTrue();
        Path renamedFile = nestedFile.resolveSibling("dashboard.html");
        assertThat(renamedFile).exists();
        assertThat(service.deleteFile(
                "html-page", "com.coolxer.plugin.probe/pages/dashboard.html")).isTrue();
        assertThat(renamedFile).doesNotExist();
    }

    @Test
    void configTreeContainsStableRelativePaths() throws IOException {
        ConfigServiceImpl service = createService();
        Path nestedFile = configRoot.resolve(
                "html-page_config/com.coolxer.plugin.probe/probe-ingestion-overview.html");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(nestedFile, "overview");

        ConfigVo root = service.getConfigFileTree("html-page").get(0);
        ConfigVo directory = findByRelativePath(root.getNodes(), "com.coolxer.plugin.probe");
        ConfigVo file = findByRelativePath(
                directory.getNodes(),
                "com.coolxer.plugin.probe/probe-ingestion-overview.html");

        assertThat(root.getRelativePath()).isEmpty();
        assertThat(directory.getRelativePath()).isEqualTo("com.coolxer.plugin.probe");
        assertThat(file.getRelativePath())
                .isEqualTo("com.coolxer.plugin.probe/probe-ingestion-overview.html")
                .doesNotStartWith(configRoot.toString());
    }

    @Test
    void rejectsPathsOutsideConfigRoot() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path outsideDirectory = configRoot.resolve("outside");
        Files.createDirectories(typeRoot);
        Files.createDirectories(outsideDirectory);
        Files.writeString(outsideDirectory.resolve("secret.html"), "secret");
        Files.createSymbolicLink(typeRoot.resolve("external"), outsideDirectory);

        assertThat(service.fileExistsInConfigPath("html-page", "../outside/secret.html")).isFalse();
        assertThat(service.fileExistsInConfigPath(
                "html-page", outsideDirectory.resolve("secret.html").toString())).isFalse();
        assertThat(service.fileExistsInConfigPath("html-page", "external/secret.html")).isFalse();
        assertThatThrownBy(() -> service.readFile("html-page", "../outside/secret.html"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.readFile("html-page", "external/secret.html"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void metaApplyUsesStrictReloadInsteadOfFallbackSnapshot() {
        ConfigServiceImpl service = createService();
        MetaDataService metaDataService = mock(MetaDataService.class);
        when(metaDataService.reloadMetaDataOrThrow())
                .thenThrow(new IllegalStateException("候选 Meta 无法加载"));
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);

        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("probe_event.json");
        assertThatThrownBy(() -> service.applyConfig("meta", configDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("候选 Meta 无法加载");

        verify(metaDataService).reloadMetaDataOrThrow();
        verify(metaDataService, never()).loadMetaData();
    }

    @Test
    void renameSupportsFilesAndDirectoriesWithoutOverwritingTarget() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path nested = typeRoot.resolve("archive/nested/page.html");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "page");

        ConfigPathOperationResult renameFile = service.renameEntry(
                "html-page", "archive/nested/page.html", "index.html");
        assertThat(renameFile.isSuccess()).isTrue();
        assertThat(renameFile.isChanged()).isTrue();
        assertThat(renameFile.isApplied()).isTrue();
        assertThat(typeRoot.resolve("archive/nested/index.html")).hasContent("page");
        assertThat(nested).doesNotExist();

        Files.writeString(typeRoot.resolve("archive/nested/occupied.html"), "occupied");
        ConfigPathOperationResult occupied = service.renameEntry(
                "html-page", "archive/nested/index.html", "occupied.html");
        assertThat(occupied.isSuccess()).isFalse();
        assertThat(occupied.isChanged()).isFalse();
        assertThat(occupied.getMessage()).contains("拒绝覆盖");
        assertThat(typeRoot.resolve("archive/nested/index.html")).exists();

        ConfigPathOperationResult renameDirectory = service.renameEntry(
                "html-page", "archive/nested", "renamed");
        assertThat(renameDirectory.isSuccess()).isTrue();
        assertThat(renameDirectory.getKind()).isEqualTo("directory");
        assertThat(typeRoot.resolve("archive/renamed/index.html")).hasContent("page");
    }

    @Test
    void renameRejectsCrossDirectoryMoveRootAndEscapedTargets() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Files.createDirectories(typeRoot.resolve("one"));
        Files.createDirectories(typeRoot.resolve("two"));
        Files.writeString(typeRoot.resolve("one/page.html"), "page");

        assertThat(service.renameEntry("html-page", "one/page.html", "../two/page.html")
                .isSuccess()).isFalse();
        assertThat(service.renameEntry("html-page", ".", "renamed").isSuccess()).isFalse();
        assertThat(service.renameEntry("html-page", "one/page.html", "..")
                .isSuccess()).isFalse();
        assertThat(typeRoot.resolve("one/page.html")).exists();
    }

    @Test
    void deleteRequiresRecursiveFlagForNonEmptyDirectoryAndProtectsRoot() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path empty = typeRoot.resolve("empty");
        Path nonEmpty = typeRoot.resolve("archive");
        Files.createDirectories(empty);
        Files.createDirectories(nonEmpty.resolve("nested"));
        Files.writeString(nonEmpty.resolve("nested/page.html"), "page");

        ConfigPathOperationResult emptyDeleted = service.deleteEntry(
                "html-page", "empty", false);
        assertThat(emptyDeleted.isSuccess()).isTrue();
        assertThat(emptyDeleted.isChanged()).isTrue();
        assertThat(empty).doesNotExist();

        ConfigPathOperationResult refused = service.deleteEntry(
                "html-page", "archive", false);
        assertThat(refused.isSuccess()).isFalse();
        assertThat(refused.isChanged()).isFalse();
        assertThat(refused.getAffectedPaths())
                .contains("archive", "archive/nested/page.html");
        assertThat(nonEmpty).exists();

        ConfigPathOperationResult recursive = service.deleteEntry(
                "html-page", "archive", true);
        assertThat(recursive.isSuccess()).isTrue();
        assertThat(recursive.isChanged()).isTrue();
        assertThat(recursive.getAffectedPaths()).contains("archive/nested/page.html");
        assertThat(nonEmpty).doesNotExist();

        assertThat(service.deleteEntry("html-page", "", false).isSuccess()).isFalse();
        assertThat(typeRoot).exists();
    }

    @Test
    void metaRenameStrictlyReloadsAndRefreshesSchema() throws IOException {
        ConfigServiceImpl service = createService();
        Path metaRoot = configRoot.resolve("meta_config");
        Files.createDirectories(metaRoot);
        Files.writeString(metaRoot.resolve("old.json"), "{}");
        MetaDataService metaDataService = mock(MetaDataService.class);
        ClickhouseSchemeService schemeService = mock(ClickhouseSchemeService.class);
        MetaData metaData = new MetaData();
        when(metaDataService.reloadMetaDataOrThrow()).thenReturn(metaData);
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);
        ReflectionTestUtils.setField(service, "clickhouseSchemeService", schemeService);

        ConfigPathOperationResult result = service.renameEntry(
                "meta", "old.json", "new.json");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.isApplied()).isTrue();
        assertThat(metaRoot.resolve("new.json")).exists();
        verify(metaDataService).reloadMetaDataOrThrow();
        verify(schemeService).loadSchemeFromMetaData(metaData);
    }

    @Test
    void metaReloadFailureKeepsFilesystemChangeAndReportsNotApplied() throws IOException {
        ConfigServiceImpl service = createService();
        Path metaRoot = configRoot.resolve("meta_config");
        Files.createDirectories(metaRoot);
        Files.writeString(metaRoot.resolve("old.json"), "{}");
        MetaDataService metaDataService = mock(MetaDataService.class);
        when(metaDataService.reloadMetaDataOrThrow())
                .thenThrow(new IllegalStateException("Meta 严格加载失败"));
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);
        ReflectionTestUtils.setField(service, "clickhouseSchemeService",
                mock(ClickhouseSchemeService.class));

        ConfigPathOperationResult result = service.renameEntry(
                "meta", "old.json", "new.json");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.isApplied()).isFalse();
        assertThat(result.getMessage()).contains("Meta 严格重载");
        assertThat(metaRoot.resolve("old.json")).doesNotExist();
        assertThat(metaRoot.resolve("new.json")).exists();
    }

    private ConfigServiceImpl createService() {
        ConfigServiceImpl service = new ConfigServiceImpl();
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "configPath", configRoot.toString());
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);
        return service;
    }

    private ConfigVo findByRelativePath(List<ConfigVo> nodes, String relativePath) {
        return nodes.stream()
                .filter(node -> relativePath.equals(node.getRelativePath()))
                .findFirst()
                .orElseThrow();
    }
}
