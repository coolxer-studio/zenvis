package com.coolxer.service.core;

import io.minio.messages.Bucket;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface MinioService {

    /**
     * 上传文件到 MinIO，保持原始文件名
     *
     * @param file       上传的文件
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @param path       上传路径前缀，如 "docs/reports"，为空则不添加前缀
     * @return 文件对象名称（存储路径）
     */
    String uploadFile(MultipartFile file, String bucketName, String path);

    /**
     * 从 MinIO 下载文件
     *
     * @param objectName 文件对象名称
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return 文件输入流
     */
    InputStream downloadFile(String objectName, String bucketName);

    /**
     * 判断路径是否为文件
     *
     * @param objectName 文件对象名称
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return 是否为文件
     */
    boolean isFile(String objectName, String bucketName);

    /**
     * 判断路径是否为目录（该前缀下存在文件）
     *
     * @param objectName 目录路径
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return 是否为目录
     */
    boolean isDirectory(String objectName, String bucketName);

    /**
     * 列出目录下的所有文件对象名称（递归）
     *
     * @param prefix    目录前缀
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return 文件对象名称列表
     */
    List<String> listObjects(String prefix, String bucketName);

    /**
     * 列出指定目录下的子目录和文件（非递归）
     *
     * @param prefix     目录前缀
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return Map，key 为名称，value 为 Item 信息（type: file/directory, size, lastModified）
     */
    List<Map<String, Object>> listDirectory(String prefix, String bucketName);

    /**
     * 获取文件大小
     *
     * @param objectName 文件对象名称
     * @param bucketName 存储桶名称，为空则使用默认配置
     * @return 文件大小（字节）
     */
    long getFileSize(String objectName, String bucketName);

    /**
     * 删除文件
     *
     * @param objectName 文件对象名称
     * @param bucketName 存储桶名称，为空则使用默认配置
     */
    void deleteFile(String objectName, String bucketName);

    /**
     * 检查存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return 是否存在
     */
    boolean bucketExists(String bucketName);

    /**
     * 创建存储桶
     *
     * @param bucketName 存储桶名称
     */
    void createBucket(String bucketName);

    /**
     * 获取所有存储桶
     *
     * @return 存储桶列表
     */
    List<Bucket> listBuckets();
}
