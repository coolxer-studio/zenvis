package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.RagKnowledgeBaseDto;
import com.coolxer.model.dih.dto.RagKnowledgeBaseSearchDto;
import com.coolxer.model.dih.vo.RagKnowledgeBaseVo;
import com.coolxer.service.core.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAG知识库管理服务
 */
@Slf4j
@Service
public class RagKnowledgeBaseService {

    private static final String KB_SOURCE_PREFIX = "kb_";

    private static final int LOG_QUEUE_CAPACITY = 512;

    private static final long LOG_POLL_TIMEOUT_SECONDS = 2L;

    private static final ConcurrentHashMap<Integer, KnowledgeBaseLogBuffer> LOG_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private RagKnowledgeBaseRepository ragKnowledgeBaseRepository;

    @Autowired
    private RagKnowledgeBaseDatasetRepository ragKnowledgeBaseDatasetRepository;

    @Autowired
    private RagDatasetRepository ragDatasetRepository;

    @Autowired
    private MinioService minioService;

    @Autowired
    private VectorStoreInitializerService vectorStoreInitializerService;

    @Autowired
    private VectorStoreDelegate vectorStoreDelegate;

    /**
     * 分页查询知识库
     *
     * @param searchDto 搜索参数
     * @return 分页结果
     */
    public PageRowsVo<RagKnowledgeBaseVo> getPageList(RagKnowledgeBaseSearchDto searchDto) {
        Pageable pageable = PageRequest.of(searchDto.getPage() - 1, searchDto.getPerPage());
        Page<RagKnowledgeBase> page = ragKnowledgeBaseRepository.findByPage(pageable, searchDto.getName(), searchDto.getSource(),
                searchDto.getTag());
        List<RagKnowledgeBaseVo> rows = page.getContent().stream().map(this::toVo).collect(Collectors.toList());
        return new PageRowsVo<>(rows, page.getTotalElements());
    }

    /**
     * 查询全部知识库的轻量选项（label/value），供 Skill 绑定等场景使用。
     * 不校验 vectorstore 管理开关，仅暴露 id 与名称。
     */
    public List<Map<String, Object>> listOptions() {
        return ragKnowledgeBaseRepository.findAll().stream()
                .sorted(Comparator.comparing(RagKnowledgeBase::getId))
                .map(kb -> {
                    Map<String, Object> option = new LinkedHashMap<String, Object>();
                    option.put("label", kb.getName());
                    option.put("value", kb.getId());
                    return option;
                })
                .collect(Collectors.toList());
    }

    /**
     * 创建知识库
     *
     * @param dto 传输实体
     * @return 知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public RagKnowledgeBase create(RagKnowledgeBaseDto dto) {
        validateDto(dto);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase()
                .setName(dto.getName())
                .setSource(dto.getSource())
                .setTags(writeTags(dto.getTags()))
                .setDescription(dto.getDescription())
                .setStatus(RagKnowledgeBaseStatus.UNBUILD)
                .setDocumentCount(0);
        knowledgeBase.setUpdateTime(new Date());
        knowledgeBase = ragKnowledgeBaseRepository.save(knowledgeBase);
        saveDatasets(knowledgeBase.getId(), dto.getDatasetIds());
        return knowledgeBase;
    }

    /**
     * 更新知识库
     *
     * @param id  知识库id
     * @param dto 传输实体
     * @return 知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public RagKnowledgeBase update(Integer id, RagKnowledgeBaseDto dto) {
        RagKnowledgeBase knowledgeBase = getKnowledgeBaseOrThrow(id);
        if (RagKnowledgeBaseStatus.BUILDING == knowledgeBase.getStatus()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "知识库正在构建中，请稍后再修改");
        }
        validateDto(dto);
        knowledgeBase.setName(dto.getName())
                .setSource(dto.getSource())
                .setTags(writeTags(dto.getTags()))
                .setDescription(dto.getDescription());
        List<Integer> oldDatasetIds = ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(id).stream()
                .map(RagKnowledgeBaseDataset::getDatasetId)
                .sorted()
                .toList();
        List<Integer> newDatasetIds = dto.getDatasetIds() == null ? List.of() : dto.getDatasetIds().stream().sorted().toList();
        if (!oldDatasetIds.equals(newDatasetIds)) {
            ragKnowledgeBaseDatasetRepository.deleteAll(ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(id));
            saveDatasets(id, newDatasetIds);
            if (RagKnowledgeBaseStatus.BUILT == knowledgeBase.getStatus()
                    || RagKnowledgeBaseStatus.BUILD_FAILED == knowledgeBase.getStatus()) {
                knowledgeBase.setStatus(RagKnowledgeBaseStatus.UNBUILD);
                knowledgeBase.setDocumentCount(0);
            }
        }
        return ragKnowledgeBaseRepository.save(knowledgeBase);
    }

    /**
     * 删除知识库
     *
     * @param id 知识库id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        RagKnowledgeBase knowledgeBase = getKnowledgeBaseOrThrow(id);
        if (RagKnowledgeBaseStatus.BUILDING == knowledgeBase.getStatus()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "知识库正在构建中，请稍后再删除");
        }
        unloadKnowledgeBaseVectors(id);
        ragKnowledgeBaseDatasetRepository.deleteAll(ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(id));
        ragKnowledgeBaseRepository.delete(knowledgeBase);
        LOG_CACHE.remove(id);
    }

    /**
     * 知识库详情
     *
     * @param id 知识库id
     * @return 知识库
     */
    public RagKnowledgeBaseVo info(Integer id) {
        return toVo(getKnowledgeBaseOrThrow(id));
    }

    /**
     * 触发异步构建
     *
     * @param id 知识库id
     */
    public void build(Integer id) {
        RagKnowledgeBase knowledgeBase = getKnowledgeBaseOrThrow(id);
        if (RagKnowledgeBaseStatus.BUILDING == knowledgeBase.getStatus()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "知识库正在构建中，请勿重复操作");
        }
        List<RagKnowledgeBaseDataset> relations = ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(id);
        if (CollectionUtils.isEmpty(relations)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "知识库未关联数据集，无法构建");
        }
        if (!vectorStoreInitializerService.isRagAvailable()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "Embedding/RAG 服务不可用，请稍后再试");
        }
        resetLogs(id);
        knowledgeBase.setStatus(RagKnowledgeBaseStatus.BUILDING);
        knowledgeBase.setOperationMessage("构建中");
        knowledgeBase.setOperationError(null);
        knowledgeBase.setOperationStartedAt(new Date());
        knowledgeBase.setOperationEndedAt(null);
        ragKnowledgeBaseRepository.save(knowledgeBase);
        Thread thread = new Thread(() -> doBuild(id));
        thread.setDaemon(true);
        thread.setName("rag-knowledge-base-build-" + id);
        thread.start();
    }

    /**
     * 读取构建日志
     *
     * @param id 知识库id
     * @return 日志
     */
    public String getLogs(Integer id) {
        KnowledgeBaseLogBuffer buffer = LOG_CACHE.computeIfAbsent(id, key -> new KnowledgeBaseLogBuffer());
        try {
            return buffer.poll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void doBuild(Integer id) {
        RagKnowledgeBase knowledgeBase = null;
        try {
            knowledgeBase = getKnowledgeBaseOrThrow(id);
            String kbSource = buildKnowledgeBaseSource(id);
            writeLog(id, "开始构建知识库......");
            writeLog(id, "1 清理旧向量数据......");
            unloadKnowledgeBaseVectors(id);
            writeLog(id, "2 加载关联数据集......");
            List<RagDataset> datasets = loadDatasets(id);
            if (CollectionUtils.isEmpty(datasets)) {
                throw new IllegalStateException("未找到可用的数据集");
            }
            writeLog(id, "共关联 " + datasets.size() + " 个数据集");
            VectorStore vectorStore = vectorStoreDelegate.getVectorStore("redis");
            int totalDocuments = 0;
            int datasetIndex = 0;
            for (RagDataset dataset : datasets) {
                datasetIndex++;
                writeLog(id, "3." + datasetIndex + " 处理数据集：" + dataset.getName());
                int count = buildDatasetDocuments(vectorStore, knowledgeBase, dataset, kbSource, id);
                totalDocuments += count;
                writeLog(id, "数据集 " + dataset.getName() + " 完成，写入 " + count + " 个文档分块");
            }
            knowledgeBase.setStatus(RagKnowledgeBaseStatus.BUILT);
            knowledgeBase.setDocumentCount(totalDocuments);
            knowledgeBase.setOperationMessage("构建成功，共写入 " + totalDocuments + " 个文档分块");
            knowledgeBase.setOperationError(null);
            knowledgeBase.setOperationEndedAt(new Date());
            ragKnowledgeBaseRepository.save(knowledgeBase);
            writeLog(id, "完成......");
        } catch (Exception e) {
            log.error("知识库构建失败: id={}", id, e);
            if (knowledgeBase != null) {
                knowledgeBase.setStatus(RagKnowledgeBaseStatus.BUILD_FAILED);
                knowledgeBase.setOperationMessage("构建失败");
                knowledgeBase.setOperationError(e.getMessage());
                knowledgeBase.setOperationEndedAt(new Date());
                ragKnowledgeBaseRepository.save(knowledgeBase);
            }
            writeLog(id, "失败......" + e.getMessage());
        }
    }

    private int buildDatasetDocuments(VectorStore vectorStore, RagKnowledgeBase knowledgeBase, RagDataset dataset, String kbSource, Integer knowledgeBaseId) throws Exception {
        List<String> objectNames = minioService.listObjects(dataset.getDirectory(), null);
        List<String> markdownObjects = objectNames.stream()
                .filter(name -> StringUtils.endsWithIgnoreCase(name, ".md"))
                .toList();
        if (CollectionUtils.isEmpty(markdownObjects)) {
            return 0;
        }
        String knowledgeBaseTags = String.join(", ", readTags(knowledgeBase.getTags()));
        Path tempDir = Files.createTempDirectory("rag-kb-" + knowledgeBaseId + "-dataset-" + dataset.getId());
        try {
            int count = 0;
            for (String objectName : markdownObjects) {
                String fileName = objectName.substring(dataset.getDirectory().length() + 1);
                Path tempFile = tempDir.resolve(fileName);
                Files.createDirectories(tempFile.getParent());
                try (InputStream inputStream = minioService.downloadFile(objectName, null)) {
                    Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                MarkdownDocumentReader reader = new MarkdownDocumentReader(tempFile.toAbsolutePath().toUri().toString());
                List<Document> documents = new TokenTextSplitter(2000, 1024, 10, 10000, true).transform(reader.get());
                for (Document doc : documents) {
                    doc.getMetadata().put("source", kbSource);
                    doc.getMetadata().put("knowledge_base_id", knowledgeBaseId);
                    doc.getMetadata().put("knowledge_base_name", knowledgeBase.getName());
                    doc.getMetadata().put("knowledge_base_tags", knowledgeBaseTags);
                    doc.getMetadata().put("dataset_id", dataset.getId());
                    doc.getMetadata().put("dataset_name", dataset.getName());
                    doc.getMetadata().put("file_name", fileName);
                }
                for (int i = 0; i < documents.size(); i += 25) {
                    int end = Math.min(i + 25, documents.size());
                    vectorStore.add(documents.subList(i, end));
                }
                count += documents.size();
            }
            return count;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private void unloadKnowledgeBaseVectors(Integer id) {
        try {
            vectorStoreInitializerService.unloadDocFromRag(buildKnowledgeBaseSource(id));
        } catch (Exception e) {
            log.warn("清理知识库旧向量数据失败: id={}", id, e);
        }
    }

    private List<RagDataset> loadDatasets(Integer knowledgeBaseId) {
        List<RagKnowledgeBaseDataset> relations = ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(knowledgeBaseId);
        List<Integer> datasetIds = relations.stream().map(RagKnowledgeBaseDataset::getDatasetId).toList();
        if (CollectionUtils.isEmpty(datasetIds)) {
            return List.of();
        }
        return ragDatasetRepository.findAllById(datasetIds.stream().map(i -> (Serializable) i).toList()).stream()
                .filter(item -> item.getIsDelete() == null || item.getIsDelete() == 0)
                .collect(Collectors.toList());
    }

    private RagKnowledgeBase getKnowledgeBaseOrThrow(Integer id) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "知识库ID不能为空");
        }
        return ragKnowledgeBaseRepository.findById(id)
                .filter(item -> item.getIsDelete() == null || item.getIsDelete() == 0)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "知识库不存在"));
    }

    private RagKnowledgeBaseVo toVo(RagKnowledgeBase knowledgeBase) {
        RagKnowledgeBaseVo vo = new RagKnowledgeBaseVo(knowledgeBase);
        List<RagKnowledgeBaseDataset> relations = ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(knowledgeBase.getId());
        List<Integer> datasetIds = relations.stream().map(RagKnowledgeBaseDataset::getDatasetId).toList();
        vo.setDatasetIds(datasetIds.stream().map(String::valueOf).collect(Collectors.toList()));
        if (CollectionUtils.isNotEmpty(datasetIds)) {
            List<String> datasetNames = ragDatasetRepository.findAllById(datasetIds.stream().map(i -> (Serializable) i).toList()).stream()
                    .map(RagDataset::getName)
                    .collect(Collectors.toList());
            vo.setDatasetNames(datasetNames);
        } else {
            vo.setDatasetNames(List.of());
        }
        return vo;
    }

    private void saveDatasets(Integer knowledgeBaseId, List<Integer> datasetIds) {
        if (CollectionUtils.isEmpty(datasetIds)) {
            return;
        }
        List<RagKnowledgeBaseDataset> relations = datasetIds.stream()
                .map(datasetId -> new RagKnowledgeBaseDataset().setKnowledgeBaseId(knowledgeBaseId).setDatasetId(datasetId))
                .collect(Collectors.toList());
        ragKnowledgeBaseDatasetRepository.saveAll(relations);
    }

    private void validateDto(RagKnowledgeBaseDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "知识库名称不能为空");
        }
    }

    private String buildKnowledgeBaseSource(Integer id) {
        return KB_SOURCE_PREFIX + id;
    }

    private void resetLogs(Integer id) {
        LOG_CACHE.put(id, new KnowledgeBaseLogBuffer());
    }

    private void writeLog(Integer id, String logLine) {
        LOG_CACHE.computeIfAbsent(id, key -> new KnowledgeBaseLogBuffer()).write(logLine);
    }

    private String writeTags(List<String> tags) {
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readTags(String tags) {
        try {
            if (StringUtils.isBlank(tags)) {
                return List.of();
            }
            List<String> parsed = JacksonConfig.OBJECT_MAPPER.readValue(tags,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(item -> {
                            try {
                                Files.delete(item);
                            } catch (Exception e) {
                                log.warn("删除临时文件失败: {}", item, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("删除临时目录失败: {}", path, e);
        }
    }

    private static class KnowledgeBaseLogBuffer {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(LOG_QUEUE_CAPACITY);

        void write(String logLine) {
            if (!queue.offer(logLine)) {
                queue.poll();
                queue.offer(logLine);
            }
        }

        String poll() throws InterruptedException {
            return queue.poll(LOG_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
