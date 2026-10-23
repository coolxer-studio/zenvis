package com.coolxer.service.core.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.minio.MinioBucketDomain;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.model.core.vo.MinioObjectInfo;
import com.coolxer.service.core.MinioService;
import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void init() {
        try {
            Set<String> businessBuckets = minioProperties.requiredBusinessBuckets();
            if (businessBuckets.size() != MinioBucketDomain.values().length
                    || businessBuckets.stream().anyMatch(StringUtils::isBlank)) {
                throw new IllegalStateException("MinIO 业务存储桶名称不能为空且必须互不相同");
            }
            String bucketName = minioProperties.getBucketName();
            if (StringUtils.isBlank(bucketName)) {
                throw new IllegalStateException("MinIO 默认存储桶名称不能为空");
            }
            if (businessBuckets.contains(bucketName)) {
                throw new IllegalStateException("MinIO 默认存储桶不能与业务存储桶重复");
            }
            ensureBucket(bucketName);
            for (String businessBucket : businessBuckets) {
                ensureBucket(businessBucket);
            }
        } catch (Exception e) {
            log.error("MinIO initialization failed: {}", e.getMessage(), e);
            throw new IllegalStateException("MinIO 初始化失败: " + e.getMessage(), e);
        }
    }

    private void ensureBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
            log.info("MinIO bucket '{}' created successfully", bucketName);
        } else {
            log.info("MinIO bucket '{}' already exists", bucketName);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String bucketName, String path) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件名不能为空");
        }

        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }

        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }

        String objectName = buildObjectName(originalFilename, path);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("File uploaded successfully: bucket={}, object={}, size={}",
                    bucketName, objectName, file.getSize());
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.FILE_WRITE_FAIL.getCode(), "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public String uploadStream(String objectName, InputStream inputStream, long size,
                               String contentType, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }
        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            log.info("File stream uploaded successfully: bucket={}, object={}, size={}",
                    bucketName, objectName, size);
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload file stream to MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.FILE_WRITE_FAIL.getCode(), "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public InputStream downloadFile(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }

        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "文件下载失败：" + e.getMessage());
        }
    }

    @Override
    public boolean isFile(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            return false;
        }
        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDirectory(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            return false;
        }
        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        String prefix = objectName.endsWith("/") ? objectName : objectName + "/";
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(1)
                    .build());
            return results.iterator().hasNext();
        } catch (Exception e) {
            log.error("Failed to check directory: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<String> listObjects(String prefix, String bucketName) {
        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        String normalizedPrefix = prefix == null ? "" : (prefix.endsWith("/") ? prefix : prefix + "/");
        List<String> objectNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(normalizedPrefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    objectNames.add(item.objectName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to list objects: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "列出文件失败：" + e.getMessage());
        }
        return objectNames;
    }

    @Override
    public List<Map<String, Object>> listDirectory(String prefix, String bucketName) {
        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        String normalizedPrefix;
        if (StringUtils.isBlank(prefix) || prefix.equals("/")) {
            normalizedPrefix = "";
        } else {
            normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        }
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(normalizedPrefix)
                    .delimiter("/")
                    .recursive(false)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                Map<String, Object> map = new HashMap<>();
                if (item.isDir()) {
                    String dirName = item.objectName();
                    // 去掉前缀，保留最后一级目录名
                    if (dirName.endsWith("/")) {
                        dirName = dirName.substring(0, dirName.length() - 1);
                    }
                    int lastSlash = dirName.lastIndexOf('/');
                    String name = lastSlash >= 0 ? dirName.substring(lastSlash + 1) : dirName;
                    map.put("name", name);
                    map.put("path", item.objectName());
                    map.put("type", "directory");
                } else {
                    String objName = item.objectName();
                    int lastSlash = objName.lastIndexOf('/');
                    String name = lastSlash >= 0 ? objName.substring(lastSlash + 1) : objName;
                    map.put("name", name);
                    map.put("path", objName);
                    map.put("type", "file");
                    map.put("size", item.size());
                    map.put("lastModified", item.lastModified() != null ? item.lastModified().toInstant().toEpochMilli() : null);
                }
                items.add(map);
            }
        } catch (Exception e) {
            log.error("Failed to list directory: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "列出目录失败：" + e.getMessage());
        }
        return items;
    }

    @Override
    public long getFileSize(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }

        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return stat.size();
        } catch (Exception e) {
            log.error("Failed to get file info from MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "获取文件信息失败：" + e.getMessage());
        }
    }

    @Override
    public MinioObjectInfo getObjectInfo(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }
        String resolvedBucket = resolveBucket(bucketName);
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(resolvedBucket)
                    .object(objectName)
                    .build());
            return new MinioObjectInfo(
                    stat.size(),
                    stat.contentType(),
                    stat.etag(),
                    stat.lastModified() == null ? null : stat.lastModified().toInstant());
        } catch (Exception e) {
            log.error("Failed to get object info from MinIO: bucket={}, object={}, error={}",
                    resolvedBucket, objectName, e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "获取文件信息失败：" + e.getMessage());
        }
    }

    @Override
    public void copyObject(String sourceBucket, String sourceObjectName,
                           String targetBucket, String targetObjectName) {
        if (StringUtils.isBlank(sourceObjectName) || StringUtils.isBlank(targetObjectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }
        String resolvedSource = resolveBucket(sourceBucket);
        String resolvedTarget = resolveBucket(targetBucket);
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(resolvedTarget)
                    .object(targetObjectName)
                    .source(CopySource.builder()
                            .bucket(resolvedSource)
                            .object(sourceObjectName)
                            .build())
                    .build());
            log.info("Object copied successfully: {}:{}, -> {}:{}",
                    resolvedSource, sourceObjectName, resolvedTarget, targetObjectName);
        } catch (Exception e) {
            log.error("Failed to copy object in MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "文件复制失败：" + e.getMessage());
        }
    }

    @Override
    public int deletePrefix(String prefix, String bucketName) {
        if (StringUtils.isBlank(prefix)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "对象前缀不能为空");
        }
        String resolvedBucket = resolveBucket(bucketName);
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        int deleted = 0;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(resolvedBucket)
                    .prefix(normalizedPrefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(resolvedBucket)
                            .object(item.objectName())
                            .build());
                    deleted++;
                }
            }
            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete object prefix from MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "文件删除失败：" + e.getMessage());
        }
    }

    private String resolveBucket(String bucketName) {
        return StringUtils.isBlank(bucketName) ? minioProperties.getBucketName() : bucketName;
    }

    @Override
    public void deleteFile(String objectName, String bucketName) {
        if (StringUtils.isBlank(objectName)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID.getCode(), "文件对象名称不能为空");
        }

        if (StringUtils.isBlank(bucketName)) {
            bucketName = minioProperties.getBucketName();
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            log.info("File deleted successfully: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "文件删除失败：" + e.getMessage());
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to check bucket existence: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "检查存储桶失败：" + e.getMessage());
        }
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "创建存储桶失败：" + e.getMessage());
        }
    }

    @Override
    public List<Bucket> listBuckets() {
        try {
            return minioClient.listBuckets();
        } catch (Exception e) {
            log.error("Failed to list buckets: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), "获取存储桶列表失败：" + e.getMessage());
        }
    }

    /**
     * 构建对象名称，保持原始文件名，可选添加路径前缀
     * 如 path="docs/reports", filename="test.txt" → "docs/reports/test.txt"
     */
    private String buildObjectName(String originalFilename, String path) {
        if (StringUtils.isBlank(path)) {
            return originalFilename;
        }
        String normalizedPath = path.replaceAll("/+", "/").replaceAll("^/+|/+$", "");
        return normalizedPath + "/" + originalFilename;
    }
}
