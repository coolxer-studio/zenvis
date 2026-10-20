package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.service.core.MinioService;
import com.coolxer.service.dih.rag.PluginRagService.DatasetPlan;
import com.coolxer.service.dih.rag.PluginRagService.KnowledgeBasePlan;
import com.coolxer.service.dih.rag.PluginRagService.PluginRagPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginRagServiceTest {

    @TempDir
    Path pluginRoot;

    @Test
    void prepareSupportsDocAndRagPathsAndDefaultFields() throws Exception {
        write("00_doc/guide/a.md", "# A");
        write("08_rag/datasets/events/sub/b.md", "# B");
        PluginRagService service = newService();

        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {
                  "datasets": [
                    {"code": "docs", "name": " Docs ", "path": "00_doc/guide"},
                    {"code": "events", "name": "Events", "path": "08_rag/datasets/events", "tags": ["event"]}
                  ],
                  "knowledge_bases": [
                    {"code": "kb", "name": "KB", "dataset_codes": ["docs", "events"],
                     "source": "custom-source"}
                  ]
                }
                """));

        assertThat(plan.datasets()).hasSize(2);
        DatasetPlan docs = plan.datasets().get(0);
        assertThat(docs.name()).isEqualTo("Docs");
        assertThat(docs.source()).isEqualTo("com.acme.demo");
        assertThat(docs.tags()).isEmpty();
        assertThat(docs.files()).extracting(file -> file.relativePath()).containsExactly("a.md");
        DatasetPlan events = plan.datasets().get(1);
        assertThat(events.files()).extracting(file -> file.relativePath()).containsExactly("sub/b.md");
        KnowledgeBasePlan knowledgeBase = plan.knowledgeBases().get(0);
        assertThat(knowledgeBase.source()).isEqualTo("custom-source");
        assertThat(knowledgeBase.datasetCodes()).containsExactly("docs", "events");
    }

    @Test
    void prepareRejectsUnknownDatasetCode() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["missing"]}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未声明的数据集");
    }

    @Test
    void prepareRejectsDuplicateCodesAndNameConflicts() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[
                  {"code":"docs","name":"Docs","path":"08_rag/datasets/docs"},
                  {"code":"docs","name":"Other","path":"08_rag/datasets/docs"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("编码重复");

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[
                  {"code":"docs","name":"Docs","path":"08_rag/datasets/docs"},
                  {"code":"DOCS","name":"Other","path":"08_rag/datasets/docs"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("编码重复");

        RagDataset occupied = new RagDataset().setName("Docs");
        occupied.setPluginPackage("another.plugin");
        occupied.setPluginCode("docs");
        when(serviceRepository(service).findActiveByName("Docs")).thenReturn(List.of(occupied));
        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称已被现有资源占用");
    }

    @Test
    void prepareRejectsUpgradeWhileManagedKnowledgeBaseIsBuilding() {
        PluginRagService service = newService();
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("KB")
                .setPluginPackage("com.acme.demo").setPluginCode("kb")
                .setStatus(RagKnowledgeBaseStatus.BUILDING);
        knowledgeBase.setId(22);
        when(serviceKnowledgeBaseRepository(service).findAllManaged("com.acme.demo"))
                .thenReturn(List.of(knowledgeBase));

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正在构建中");
    }

    @Test
    void prepareRejectsEscapingPathSymlinkEmptyAndNonMarkdownDirectory() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"../outside"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("越出插件目录");

        Files.createDirectories(pluginRoot.resolve("08_rag/datasets/empty"));
        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/empty"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有 Markdown 文件");

        write("08_rag/datasets/mixed/a.md", "# A");
        write("08_rag/datasets/mixed/a.txt", "x");
        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/mixed"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 Markdown");

        Path target = pluginRoot.resolve("08_rag/datasets/link-target");
        Path link = pluginRoot.resolve("08_rag/datasets/link");
        Files.createDirectories(target);
        Files.writeString(target.resolve("b.md"), "# B");
        Files.createSymbolicLink(link, target.getFileName());
        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/link"}]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("符号链接");
    }

    @Test
    void synchronizeCreatesResourcesAndBuildsKnowledgeBase() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));

        RagDatasetRepository datasetRepository = serviceRepository(service);
        RagKnowledgeBaseRepository knowledgeBaseRepository = serviceKnowledgeBaseRepository(service);
        RagKnowledgeBaseService knowledgeBaseService = serviceKnowledgeBaseService(service);
        MinioService minioService = serviceMinioService(service);
        AtomicInteger datasetId = new AtomicInteger(11);
        AtomicInteger knowledgeBaseId = new AtomicInteger(22);
        AtomicReference<RagDataset> savedDataset = new AtomicReference<>();
        AtomicReference<RagKnowledgeBase> savedKnowledgeBase = new AtomicReference<>();
        AtomicReference<String> uploadedDirectory = new AtomicReference<>();

        when(datasetRepository.findManaged("com.acme.demo", "docs")).thenReturn(List.of());
        when(datasetRepository.findAllManaged("com.acme.demo")).thenReturn(List.of());
        when(datasetRepository.save(any(RagDataset.class))).thenAnswer(invocation -> {
            RagDataset dataset = invocation.getArgument(0);
            if (dataset.getId() == null) {
                dataset.setId(datasetId.incrementAndGet());
            }
            savedDataset.set(dataset);
            return dataset;
        });
        when(knowledgeBaseRepository.findManaged("com.acme.demo", "kb")).thenReturn(List.of());
        when(knowledgeBaseRepository.findAllManaged("com.acme.demo")).thenReturn(List.of());
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> {
            RagKnowledgeBase knowledgeBase = invocation.getArgument(0);
            if (knowledgeBase.getId() == null) {
                knowledgeBase.setId(knowledgeBaseId.incrementAndGet());
            }
            savedKnowledgeBase.set(knowledgeBase);
            return knowledgeBase;
        });
        when(knowledgeBaseRepository.findById(anyInt())).thenAnswer(invocation -> Optional.ofNullable(savedKnowledgeBase.get()));
        when(knowledgeBaseService.buildNow(anyInt())).thenAnswer(invocation -> {
            savedKnowledgeBase.get().setStatus(RagKnowledgeBaseStatus.BUILT);
            return RagKnowledgeBaseStatus.BUILT;
        });
        when(minioService.uploadStream(anyString(), any(), anyLong(), anyString(), eq(null)))
                .thenAnswer(invocation -> {
                    uploadedDirectory.set(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);
        List<String> warnings = new ArrayList<>();

        service.synchronize("com.acme.demo", plan, pluginRoot, warnings);

        assertThat(savedDataset.get().getDirectory()).startsWith("rag/plugins/com.acme.demo/docs/");
        assertThat(uploadedDirectory.get()).startsWith(savedDataset.get().getDirectory());
        assertThat(savedDataset.get().getFileCount()).isEqualTo(1);
        assertThat(savedKnowledgeBase.get().getStatus()).isEqualTo(RagKnowledgeBaseStatus.BUILT);
        assertThat(savedKnowledgeBase.get().getBuildFingerprint()).isNotBlank();
        assertThat(warnings).isEmpty();
        verify(knowledgeBaseService).buildNow(savedKnowledgeBase.get().getId());
    }

    @Test
    void synchronizeRebuildsWhenDatasetContentChanges() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan initialPlan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        RagDataset dataset = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        dataset.setId(11);
        RagKnowledgeBase existing = new RagKnowledgeBase().setName("KB").setPluginPackage("com.acme.demo")
                .setPluginCode("kb").setStatus(RagKnowledgeBaseStatus.BUILT);
        existing.setId(22);
        existing.setBuildFingerprint(ReflectionTestUtils.invokeMethod(service, "buildFingerprint",
                initialPlan.knowledgeBases().get(0), List.of(dataset)));

        write("08_rag/datasets/docs/a.md", "# B");
        PluginRagPlan changedPlan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        RagDatasetRepository datasetRepository = serviceRepository(service);
        RagKnowledgeBaseRepository knowledgeBaseRepository = serviceKnowledgeBaseRepository(service);
        RagKnowledgeBaseService knowledgeBaseService = serviceKnowledgeBaseService(service);
        when(datasetRepository.findManaged("com.acme.demo", "docs")).thenReturn(List.of(dataset));
        when(datasetRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(dataset));
        when(datasetRepository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findManaged("com.acme.demo", "kb")).thenReturn(List.of(existing));
        when(knowledgeBaseRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(existing));
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findById(22)).thenReturn(Optional.of(existing));
        when(knowledgeBaseService.buildNow(22)).thenAnswer(invocation -> {
            existing.setStatus(RagKnowledgeBaseStatus.BUILT);
            return RagKnowledgeBaseStatus.BUILT;
        });
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);

        service.synchronize("com.acme.demo", changedPlan, pluginRoot, new ArrayList<>());

        verify(knowledgeBaseService).buildNow(22);
        assertThat(existing.getBuildFingerprint()).isNotBlank();
    }

    @Test
    void synchronizeOverwritesAdministratorEditsAndRelations() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs","tags":["plugin"]}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"],"tags":["plugin"]}]}
                """));
        RagDataset dataset = new RagDataset().setName("Admin Docs").setTags("[\"admin\"]")
                .setDirectory("rag/admins/docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        dataset.setId(11);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("Admin KB").setTags("[\"admin\"]")
                .setStatus(RagKnowledgeBaseStatus.BUILT).setPluginPackage("com.acme.demo").setPluginCode("kb");
        knowledgeBase.setId(22);
        RagDatasetRepository datasetRepository = serviceRepository(service);
        RagKnowledgeBaseRepository knowledgeBaseRepository = serviceKnowledgeBaseRepository(service);
        RagKnowledgeBaseService knowledgeBaseService = serviceKnowledgeBaseService(service);
        when(datasetRepository.findManaged("com.acme.demo", "docs")).thenReturn(List.of(dataset));
        when(datasetRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(dataset));
        when(datasetRepository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findManaged("com.acme.demo", "kb")).thenReturn(List.of(knowledgeBase));
        when(knowledgeBaseRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(knowledgeBase));
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findById(22)).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseService.buildNow(22)).thenAnswer(invocation -> {
            knowledgeBase.setStatus(RagKnowledgeBaseStatus.BUILT);
            return RagKnowledgeBaseStatus.BUILT;
        });
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);

        service.synchronize("com.acme.demo", plan, pluginRoot, new ArrayList<>());

        assertThat(dataset.getName()).isEqualTo("Docs");
        assertThat(dataset.getTags()).isEqualTo("[\"plugin\"]");
        assertThat(dataset.getDirectory()).startsWith("rag/plugins/com.acme.demo/docs/");
        assertThat(knowledgeBase.getName()).isEqualTo("KB");
        assertThat(knowledgeBase.getTags()).isEqualTo("[\"plugin\"]");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RagKnowledgeBaseDataset>> relations = ArgumentCaptor.forClass(List.class);
        verify(serviceRelationRepository(service)).saveAll(relations.capture());
        assertThat(relations.getValue()).extracting(RagKnowledgeBaseDataset::getDatasetId).containsExactly(11);
    }

    @Test
    void synchronizeRemovesObsoleteManagedResources() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        RagDataset keptDataset = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        keptDataset.setId(11);
        RagDataset obsoleteDataset = new RagDataset().setName("Old Docs").setPluginPackage("com.acme.demo")
                .setPluginCode("old-docs");
        obsoleteDataset.setId(12);
        RagKnowledgeBase keptKnowledgeBase = new RagKnowledgeBase().setName("KB")
                .setPluginPackage("com.acme.demo").setPluginCode("kb");
        keptKnowledgeBase.setId(22);
        RagKnowledgeBase obsoleteKnowledgeBase = new RagKnowledgeBase().setName("Old KB")
                .setPluginPackage("com.acme.demo").setPluginCode("old-kb");
        obsoleteKnowledgeBase.setId(23);
        RagKnowledgeBaseRepository knowledgeBaseRepository = serviceKnowledgeBaseRepository(service);
        when(knowledgeBaseRepository.findAllManaged("com.acme.demo"))
                .thenReturn(List.of(keptKnowledgeBase, obsoleteKnowledgeBase));
        when(serviceRepository(service).findAllManaged("com.acme.demo"))
                .thenReturn(List.of(keptDataset, obsoleteDataset));
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        when(serviceRepository(service).findManaged("com.acme.demo", "docs")).thenReturn(List.of(keptDataset));
        when(serviceRepository(service).save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findManaged("com.acme.demo", "kb")).thenReturn(List.of(keptKnowledgeBase));
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findById(22)).thenReturn(Optional.of(keptKnowledgeBase));
        when(serviceKnowledgeBaseService(service).buildNow(22)).thenAnswer(invocation -> {
            keptKnowledgeBase.setStatus(RagKnowledgeBaseStatus.BUILT);
            return RagKnowledgeBaseStatus.BUILT;
        });
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);

        service.synchronize("com.acme.demo", plan, pluginRoot, new ArrayList<>());

        verify(serviceKnowledgeBaseService(service)).delete(23);
        verify(serviceDatasetService(service)).delete(12);
    }

    @Test
    void prepareRejectsEmptyDeclarationWhenDatasetIsReferencedByExternalKnowledgeBase() {
        PluginRagService service = newService();
        RagDataset managed = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        managed.setId(11);
        RagKnowledgeBase external = new RagKnowledgeBase().setName("External KB").setPluginPackage("other.plugin");
        external.setId(5);
        when(serviceRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(managed));
        when(serviceRelationRepository(service).findByDatasetId(11)).thenReturn(List.of(
                new RagKnowledgeBaseDataset().setKnowledgeBaseId(5).setDatasetId(11)));
        when(serviceKnowledgeBaseRepository(service).findById(5)).thenReturn(Optional.of(external));

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("被非托管知识库引用");
    }

    @Test
    void synchronizeWarnsAndKeepsBuildFailedStatus() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        AtomicReference<RagKnowledgeBase> savedKnowledgeBase = new AtomicReference<>();
        when(serviceRepository(service).save(any(RagDataset.class))).thenAnswer(invocation -> {
            RagDataset dataset = invocation.getArgument(0);
            dataset.setId(11);
            return dataset;
        });
        when(serviceKnowledgeBaseRepository(service).save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> {
            RagKnowledgeBase knowledgeBase = invocation.getArgument(0);
            knowledgeBase.setId(22);
            savedKnowledgeBase.set(knowledgeBase);
            return knowledgeBase;
        });
        when(serviceKnowledgeBaseService(service).buildNow(22)).thenAnswer(invocation -> {
            savedKnowledgeBase.get().setStatus(RagKnowledgeBaseStatus.BUILD_FAILED);
            return RagKnowledgeBaseStatus.BUILD_FAILED;
        });
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);
        List<String> warnings = new ArrayList<>();

        service.synchronize("com.acme.demo", plan, pluginRoot, warnings);

        assertThat(savedKnowledgeBase.get().getStatus()).isEqualTo(RagKnowledgeBaseStatus.BUILD_FAILED);
        assertThat(warnings).anyMatch(item -> item.contains("构建失败"));
    }

    @Test
    void synchronizeRollsBackCreatedResourcesWhenDatabaseWriteFails() throws Exception {
        write("08_rag/datasets/one/a.md", "# A");
        write("08_rag/datasets/two/b.md", "# B");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[
                  {"code":"one","name":"One","path":"08_rag/datasets/one"},
                  {"code":"two","name":"Two","path":"08_rag/datasets/two"}]}
                """));
        AtomicInteger saves = new AtomicInteger();
        when(serviceRepository(service).save(any(RagDataset.class))).thenAnswer(invocation -> {
            RagDataset dataset = invocation.getArgument(0);
            if (saves.incrementAndGet() == 1) {
                dataset.setId(11);
                return dataset;
            }
            throw new IllegalStateException("database unavailable");
        });

        assertThatThrownBy(() -> service.synchronize("com.acme.demo", plan, pluginRoot, new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");

        verify(serviceRepository(service)).deleteById(11);
    }

    @Test
    void synchronizeCleansStagedFilesWhenUploadFails() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        write("08_rag/datasets/docs/b.md", "# B");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}]}
                """));
        AtomicInteger uploads = new AtomicInteger();
        AtomicReference<String> firstObject = new AtomicReference<>();
        when(serviceMinioService(service).uploadStream(anyString(), any(), anyLong(), anyString(), eq(null)))
                .thenAnswer(invocation -> {
                    if (uploads.incrementAndGet() == 1) {
                        firstObject.set(invocation.getArgument(0));
                        return invocation.getArgument(0);
                    }
                    throw new IllegalStateException("upload failed");
                });
        when(serviceMinioService(service).listObjects(anyString(), eq(null)))
                .thenAnswer(invocation -> firstObject.get() == null
                        ? List.of() : List.of(firstObject.get()));

        assertThatThrownBy(() -> service.synchronize("com.acme.demo", plan, pluginRoot, new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload failed");

        verify(serviceMinioService(service)).deleteFile(firstObject.get(), null);
    }

    @Test
    void synchronizeWithEmptyPlanFailsWhenManagedCleanupFails() {
        PluginRagService service = newService();
        RagDataset dataset = new RagDataset().setName("Docs")
                .setPluginPackage("com.acme.demo").setPluginCode("docs");
        dataset.setId(11);
        when(serviceRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(dataset));
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(serviceDatasetService(service)).delete(11);

        assertThatThrownBy(() -> service.synchronize("com.acme.demo",
                new PluginRagPlan("com.acme.demo", List.of(), List.of()), pluginRoot, new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
    }

    @Test
    void synchronizeSkipsUnchangedBuiltKnowledgeBase() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        RagDataset dataset = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        dataset.setId(11);
        RagKnowledgeBase existing = new RagKnowledgeBase().setName("KB").setPluginPackage("com.acme.demo")
                .setPluginCode("kb").setStatus(RagKnowledgeBaseStatus.BUILT);
        existing.setId(22);
        String fingerprint = ReflectionTestUtils.invokeMethod(service, "buildFingerprint",
                plan.knowledgeBases().get(0), List.of(dataset));
        existing.setBuildFingerprint(fingerprint);

        RagDatasetRepository datasetRepository = serviceRepository(service);
        RagKnowledgeBaseRepository knowledgeBaseRepository = serviceKnowledgeBaseRepository(service);
        RagKnowledgeBaseService knowledgeBaseService = serviceKnowledgeBaseService(service);
        when(datasetRepository.findManaged("com.acme.demo", "docs")).thenReturn(List.of(dataset));
        when(datasetRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(dataset));
        when(datasetRepository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeBaseRepository.findManaged("com.acme.demo", "kb")).thenReturn(List.of(existing));
        when(knowledgeBaseRepository.findAllManaged("com.acme.demo")).thenReturn(List.of(existing));
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);

        service.synchronize("com.acme.demo", plan, pluginRoot, new ArrayList<>());

        verify(knowledgeBaseService, never()).buildNow(anyInt());
    }

    @Test
    void synchronizeWarnsWhenEmbeddingUnavailableButKeepsResources() throws Exception {
        write("08_rag/datasets/docs/a.md", "# A");
        PluginRagService service = newService();
        PluginRagPlan plan = service.prepare("com.acme.demo", pluginRoot, config("""
                {"datasets":[{"code":"docs","name":"Docs","path":"08_rag/datasets/docs"}],
                 "knowledge_bases":[{"code":"kb","name":"KB","dataset_codes":["docs"]}]}
                """));
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(false);
        when(serviceRepository(service).save(any(RagDataset.class))).thenAnswer(invocation -> {
            RagDataset dataset = invocation.getArgument(0);
            dataset.setId(11);
            return dataset;
        });
        when(serviceKnowledgeBaseRepository(service).save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> {
            RagKnowledgeBase knowledgeBase = invocation.getArgument(0);
            knowledgeBase.setId(22);
            return knowledgeBase;
        });
        List<String> warnings = new ArrayList<>();

        service.synchronize("com.acme.demo", plan, pluginRoot, warnings);

        assertThat(warnings).anyMatch(item -> item.contains("Embedding/RAG 服务不可用"));
    }

    @Test
    void exportWritesConfigAndManagedFiles() throws Exception {
        PluginRagService service = newService();
        RagDataset dataset = new RagDataset().setName("Docs").setSource("com.acme.demo").setTags("[\"plugin\"]")
                .setDirectory("rag/plugins/com.acme.demo/docs/op").setPluginPackage("com.acme.demo")
                .setPluginCode("docs");
        dataset.setId(11);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("KB").setSource("com.acme.demo")
                .setTags("[]").setPluginPackage("com.acme.demo").setPluginCode("kb");
        knowledgeBase.setId(22);
        RagKnowledgeBaseDataset relation = new RagKnowledgeBaseDataset().setKnowledgeBaseId(22).setDatasetId(11);
        when(serviceRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(dataset));
        when(serviceKnowledgeBaseRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(knowledgeBase));
        when(serviceRelationRepository(service).findByKnowledgeBaseId(22)).thenReturn(List.of(relation));
        when(serviceMinioService(service).listObjects("rag/plugins/com.acme.demo/docs/op", null))
                .thenReturn(List.of("rag/plugins/com.acme.demo/docs/op/sub/a.md"));
        when(serviceMinioService(service).downloadFile("rag/plugins/com.acme.demo/docs/op/sub/a.md", null))
                .thenReturn(new ByteArrayInputStream("# A".getBytes(StandardCharsets.UTF_8)));
        Path target = pluginRoot.resolve("export/08_rag");
        Files.createDirectories(target.resolve("datasets/removed"));
        Files.writeString(target.resolve("datasets/removed/old.md"), "# Old");

        service.exportManagedResources("com.acme.demo", target);

        String config = Files.readString(target.resolve("config.json"));
        assertThat(config).contains("08_rag/datasets/docs");
        assertThat(config).contains("\"dataset_codes\"");
        assertThat(target.resolve("datasets/docs/sub/a.md")).hasContent("# A");
        assertThat(target.resolve("datasets/removed/old.md")).doesNotExist();
    }

    @Test
    void cleanupDeletesManagedResourcesButKeepsDatasetReferencedByOtherKnowledgeBase() {
        PluginRagService service = newService();
        RagDataset managed = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        managed.setId(11);
        RagKnowledgeBase other = new RagKnowledgeBase().setPluginPackage("other.plugin");
        RagKnowledgeBaseDataset relation = new RagKnowledgeBaseDataset().setKnowledgeBaseId(5).setDatasetId(11);
        when(serviceRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(managed));
        when(serviceRelationRepository(service).findByDatasetId(11)).thenReturn(List.of(relation));
        when(serviceKnowledgeBaseRepository(service).findById(5)).thenReturn(Optional.of(other));
        List<String> warnings = new ArrayList<>();

        service.cleanupManagedResources("com.acme.demo", warnings);

        verify(serviceDatasetService(service), never()).delete(11);
        assertThat(warnings).anyMatch(item -> item.contains("仍被其他知识库引用"));
    }

    @Test
    void cleanupRemovesOnlyManagedResourcesAndLegacyDocumentVectors() {
        PluginRagService service = newService();
        RagDataset managed = new RagDataset().setName("Docs").setPluginPackage("com.acme.demo").setPluginCode("docs");
        managed.setId(11);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("KB")
                .setPluginPackage("com.acme.demo").setPluginCode("kb");
        knowledgeBase.setId(22);
        when(serviceRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(managed));
        when(serviceKnowledgeBaseRepository(service).findAllManaged("com.acme.demo")).thenReturn(List.of(knowledgeBase));
        when(serviceVectorStore(service).isRagAvailable()).thenReturn(true);

        service.cleanupManagedResources("com.acme.demo", new ArrayList<>());

        verify(serviceKnowledgeBaseService(service)).delete(22);
        verify(serviceDatasetService(service)).delete(11);
        verify(serviceVectorStore(service)).unloadDocFromRag("com_acme_demo");
    }

    @Test
    void prepareRejectsInvalidJson() {
        PluginRagService service = newService();

        assertThatThrownBy(() -> service.prepare("com.acme.demo", pluginRoot, "{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("解析失败");
    }

    private PluginRagService newService() {
        PluginRagService service = new PluginRagService();
        ReflectionTestUtils.setField(service, "ragDatasetRepository", mock(RagDatasetRepository.class));
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseRepository", mock(RagKnowledgeBaseRepository.class));
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository",
                mock(RagKnowledgeBaseDatasetRepository.class));
        ReflectionTestUtils.setField(service, "ragDatasetService", mock(RagDatasetService.class));
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseService", mock(RagKnowledgeBaseService.class));
        ReflectionTestUtils.setField(service, "minioService", mock(MinioService.class));
        ReflectionTestUtils.setField(service, "vectorStoreInitializerService", mock(VectorStoreInitializerService.class));
        ReflectionTestUtils.setField(service, "embeddingModel", "test-embedding");
        ReflectionTestUtils.setField(service, "vectorIndex", "test-index");
        return service;
    }

    private RagDatasetRepository serviceRepository(PluginRagService service) {
        return (RagDatasetRepository) ReflectionTestUtils.getField(service, "ragDatasetRepository");
    }

    private RagKnowledgeBaseRepository serviceKnowledgeBaseRepository(PluginRagService service) {
        return (RagKnowledgeBaseRepository) ReflectionTestUtils.getField(service, "ragKnowledgeBaseRepository");
    }

    private RagKnowledgeBaseDatasetRepository serviceRelationRepository(PluginRagService service) {
        return (RagKnowledgeBaseDatasetRepository) ReflectionTestUtils.getField(service,
                "ragKnowledgeBaseDatasetRepository");
    }

    private RagDatasetService serviceDatasetService(PluginRagService service) {
        return (RagDatasetService) ReflectionTestUtils.getField(service, "ragDatasetService");
    }

    private RagKnowledgeBaseService serviceKnowledgeBaseService(PluginRagService service) {
        return (RagKnowledgeBaseService) ReflectionTestUtils.getField(service, "ragKnowledgeBaseService");
    }

    private MinioService serviceMinioService(PluginRagService service) {
        return (MinioService) ReflectionTestUtils.getField(service, "minioService");
    }

    private VectorStoreInitializerService serviceVectorStore(PluginRagService service) {
        return (VectorStoreInitializerService) ReflectionTestUtils.getField(service,
                "vectorStoreInitializerService");
    }

    private String config(String value) {
        return value;
    }

    private void write(String relative, String content) throws Exception {
        Path path = pluginRoot.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
