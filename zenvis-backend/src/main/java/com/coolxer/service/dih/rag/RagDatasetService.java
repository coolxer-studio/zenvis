package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.RagDatasetDto;
import com.coolxer.model.dih.dto.RagDatasetSearchDto;
import com.coolxer.model.dih.vo.RagDatasetFileVo;
import com.coolxer.model.dih.vo.RagDatasetVo;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG数据集管理服务
 */
@Slf4j
@Service
public class RagDatasetService {

    private static final String DATASET_DIR_PREFIX = "rag/datasets/";

    @Autowired
    private RagDatasetRepository ragDatasetRepository;

    @Autowired
    private RagKnowledgeBaseDatasetRepository ragKnowledgeBaseDatasetRepository;

    @Autowired
    private MinioService minioService;

    /**
     * 分页查询数据集
     *
     * @param searchDto 搜索参数
     * @return 分页结果
     */
    public PageRowsVo<RagDatasetVo> getPageList(RagDatasetSearchDto searchDto) {
        Pageable pageable = PageRequest.of(searchDto.getPage() - 1, searchDto.getPerPage());
        Page<RagDataset> page = ragDatasetRepository.findByPage(pageable, searchDto.getName(), searchDto.getSource(),
                searchDto.getTag());
        List<RagDatasetVo> rows = page.getContent().stream().map(RagDatasetVo::new).collect(Collectors.toList());
        return new PageRowsVo<>(rows, page.getTotalElements());
    }

    /**
     * 查询全部数据集选项
     *
     * @return 选项列表
     */
    public List<Map<String, String>> options() {
        List<RagDataset> all = ragDatasetRepository.findAll().stream()
                .filter(item -> item.getIsDelete() == null || item.getIsDelete() == 0)
                .toList();
        return CommonUtil.createOptions(all, RagDataset::getName, item -> String.valueOf(item.getId()));
    }

    /**
     * 创建数据集
     *
     * @param dto 传输实体
     * @return 数据集
     */
    public RagDataset create(RagDatasetDto dto) {
        validateDto(dto, null);
        RagDataset dataset = new RagDataset()
                .setName(dto.getName())
                .setSource(dto.getSource())
                .setTags(writeTags(dto.getTags()))
                .setDescription(dto.getDescription())
                .setDirectory("")
                .setFileCount(0);
        dataset.setUpdateTime(new Date());
        dataset = ragDatasetRepository.save(dataset);
        dataset.setDirectory(buildDirectory(dataset.getId()));
        return ragDatasetRepository.save(dataset);
    }

    /**
     * 更新数据集
     *
     * @param id  数据集id
     * @param dto 传输实体
     * @return 数据集
     */
    public RagDataset update(Integer id, RagDatasetDto dto) {
        RagDataset dataset = getDatasetOrThrow(id);
        validateDto(dto, id);
        dataset.setName(dto.getName())
                .setSource(dto.getSource())
                .setTags(writeTags(dto.getTags()))
                .setDescription(dto.getDescription());
        return ragDatasetRepository.save(dataset);
    }

    /**
     * 删除数据集
     *
     * @param id 数据集id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        RagDataset dataset = getDatasetOrThrow(id);
        List<RagKnowledgeBaseDataset> references = ragKnowledgeBaseDatasetRepository.findByDatasetId(id);
        if (CollectionUtils.isNotEmpty(references)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "数据集已被 " + references.size() + " 个知识库引用，请先解除引用后再删除");
        }
        List<String> objectNames = minioService.listObjects(dataset.getDirectory(), null);
        for (String objectName : objectNames) {
            try {
                minioService.deleteFile(objectName, null);
            } catch (Exception e) {
                log.warn("删除数据集文件失败: objectName={}", objectName, e);
            }
        }
        ragDatasetRepository.delete(dataset);
    }

    /**
     * 数据集详情
     *
     * @param id 数据集id
     * @return 数据集
     */
    public RagDatasetVo info(Integer id) {
        return new RagDatasetVo(getDatasetOrThrow(id));
    }

    /**
     * 上传文件到数据集
     *
     * @param id   数据集id
     * @param file 文件
     * @return 上传结果（value 为对象名，filename 为原始文件名，适配 amis input-file 回显）
     */
    public Map<String, String> uploadFile(Integer id, MultipartFile file) {
        return uploadFile(id, file, null);
    }

    /**
     * 上传文件到数据集；当提供 relativePath 时，将其作为数据集目录下的相对路径保留层级
     *
     * @param id           数据集id
     * @param file         文件
     * @param relativePath 目录上传时的相对路径（如 mydir/sub/a.md），可为空
     * @return 上传结果（value 为对象名，filename 为原始文件名）
     */
    public Map<String, String> uploadFile(Integer id, MultipartFile file, String relativePath) {
        RagDataset dataset = getDatasetOrThrow(id);
        validateFile(file);
        MultipartFile target = wrapWithRelativePath(file, relativePath);
        String objectName = minioService.uploadFile(target, null, dataset.getDirectory());
        String fileName = objectName.substring(objectName.lastIndexOf('/') + 1);
        refreshFileCount(dataset);
        ragDatasetRepository.save(dataset);
        return Map.of("value", objectName, "filename", fileName);
    }

    /**
     * 列出数据集文件
     *
     * @param id 数据集id
     * @return 文件列表（name 为相对数据集目录的层级路径，保留子目录）
     */
    public List<RagDatasetFileVo> listFiles(Integer id) {
        RagDataset dataset = getDatasetOrThrow(id);
        List<String> objectNames = minioService.listObjects(dataset.getDirectory(), null);
        String prefix = dataset.getDirectory() + "/";
        List<RagDatasetFileVo> files = new ArrayList<>();
        for (String objectName : objectNames) {
            RagDatasetFileVo vo = new RagDatasetFileVo();
            vo.setObjectName(objectName);
            String relative = objectName.startsWith(prefix) ? objectName.substring(prefix.length()) : objectName;
            vo.setName(relative);
            try {
                vo.setSize(minioService.getFileSize(objectName, null));
            } catch (Exception e) {
                vo.setSize(0);
            }
            files.add(vo);
        }
        return files;
    }

    /**
     * 读取数据集文件内容
     *
     * @param id         数据集id
     * @param objectName 文件对象名称
     * @return 文件内容
     */
    public String readFileContent(Integer id, String objectName) {
        RagDataset dataset = getDatasetOrThrow(id);
        if (StringUtils.isBlank(objectName) || !objectName.startsWith(dataset.getDirectory() + "/")) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "文件不属于该数据集");
        }
        try (InputStream inputStream = minioService.downloadFile(objectName, null)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "读取文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据集文件
     *
     * @param id         数据集id
     * @param objectName 文件对象名称
     * @return 数据集
     */
    public RagDataset deleteFile(Integer id, String objectName) {
        RagDataset dataset = getDatasetOrThrow(id);
        if (StringUtils.isBlank(objectName) || !objectName.startsWith(dataset.getDirectory() + "/")) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "文件不属于该数据集");
        }
        minioService.deleteFile(objectName, null);
        refreshFileCount(dataset);
        return ragDatasetRepository.save(dataset);
    }

    private RagDataset getDatasetOrThrow(Integer id) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "数据集ID不能为空");
        }
        return ragDatasetRepository.findById(id)
                .filter(item -> item.getIsDelete() == null || item.getIsDelete() == 0)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "数据集不存在"));
    }

    private void validateDto(RagDatasetDto dto, Integer excludeId) {
        if (dto == null || StringUtils.isBlank(dto.getName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "数据集名称不能为空");
        }
        if (ragDatasetRepository.countByName(dto.getName().trim(), excludeId) > 0) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "数据集名称已存在：" + dto.getName().trim());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件名不能为空");
        }
        String fileName = java.nio.file.Paths.get(originalFilename).getFileName().toString();
        if (!StringUtils.endsWithIgnoreCase(fileName, ".md")) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "仅支持 markdown(.md) 格式文件");
        }
    }

    private void refreshFileCount(RagDataset dataset) {
        try {
            List<String> objectNames = minioService.listObjects(dataset.getDirectory(), null);
            dataset.setFileCount(objectNames.size());
        } catch (Exception e) {
            log.warn("刷新数据集文件数量失败: id={}", dataset.getId(), e);
        }
    }

    /**
     * 当存在 relativePath 时，包装 MultipartFile 以覆盖 getOriginalFilename，
     * 让 MinioService 按 relativePath 构建对象名以保留目录层级
     */
    private MultipartFile wrapWithRelativePath(MultipartFile file, String relativePath) {
        String sanitized = sanitizeRelativePath(relativePath);
        if (StringUtils.isBlank(sanitized)) {
            return file;
        }
        return new RelativePathMultipartFile(file, sanitized);
    }

    /**
     * 清洗目录上传时的相对路径：
     * 统一为正斜杠、去掉开头的斜杠/点、禁止 .. 越级、折叠连续斜杠、去除 Windows 盘符
     *
     * @return 清洗后的相对路径；不合法时返回 null
     */
    private String sanitizeRelativePath(String relativePath) {
        if (StringUtils.isBlank(relativePath)) {
            return null;
        }
        String path = relativePath.replace('\\', '/');
        // 去掉 Windows 盘符前缀，例如 C:/xxx
        path = path.replaceFirst("^[A-Za-z]:/", "");
        path = path.replaceAll("/+", "/");
        path = path.replaceAll("^/+", "");
        // 去除开头的 ./ 或 ../ 段，禁止越级
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("../") || path.contains("/../") || path.endsWith("/..") || path.equals("..")) {
            return null;
        }
        if (StringUtils.isBlank(path) || !StringUtils.endsWithIgnoreCase(path, ".md")) {
            return null;
        }
        return path;
    }

    /**
     * MultipartFile 装饰器：仅覆盖 getOriginalFilename 以携带相对路径
     */
    private static class RelativePathMultipartFile implements MultipartFile {
        private final MultipartFile delegate;
        private final String originalFilename;

        RelativePathMultipartFile(MultipartFile delegate, String originalFilename) {
            this.delegate = delegate;
            this.originalFilename = originalFilename;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws java.io.IOException {
            return delegate.getBytes();
        }

        @Override
        public InputStream getInputStream() throws java.io.IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
            delegate.transferTo(dest);
        }
    }

    private String buildDirectory(Integer id) {
        return DATASET_DIR_PREFIX + id;
    }

    private String writeTags(List<String> tags) {
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception e) {
            return "[]";
        }
    }
}
