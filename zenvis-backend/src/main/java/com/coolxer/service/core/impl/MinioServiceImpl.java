package com.coolxer.service.core.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.minio.MinioProperties;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void init() {
        try {
            String bucketName = minioProperties.getBucketName();
            if (StringUtils.isBlank(bucketName)) {
                log.warn("MinIO bucket name is not configured, skip auto-creation");
                return;
            }
            if (!bucketExists(bucketName)) {
                createBucket(bucketName);
                log.info("MinIO bucket '{}' created successfully", bucketName);
            } else {
                log.info("MinIO bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO initialization failed: {}", e.getMessage(), e);
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
