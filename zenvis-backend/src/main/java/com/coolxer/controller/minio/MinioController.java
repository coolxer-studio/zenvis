package com.coolxer.controller.minio;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.core.vo.MinioFileVo;
import com.coolxer.model.core.vo.MinioItemVo;
import com.coolxer.service.core.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1/minio")
@RequiredArgsConstructor
@Tag(name = "MinIO 文件管理", description = "MinIO 文件上传、下载、删除接口")
public class MinioController extends BaseController {

    private final MinioService minioService;

    @PostMapping("/upload")
    @Operation(summary = "文件上传", description = "上传文件到 MinIO 对象存储，支持指定 bucket 和路径，保持原始文件名")
    public ResponseWrap<MinioFileVo> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bucket", required = false) String bucket,
            @RequestParam(value = "path", required = false) String path) {
        String objectName = minioService.uploadFile(file, bucket, path);
        String fileName = file.getOriginalFilename();
        long size = file.getSize();
        String downloadUrl = "/api/v1/minio/download?objectName=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(bucket)) {
            downloadUrl += "&bucket=" + URLEncoder.encode(bucket, StandardCharsets.UTF_8);
        }

        MinioFileVo vo = MinioFileVo.builder()
                .objectName(objectName)
                .fileName(fileName)
                .size(size)
                .url(downloadUrl)
                .build();
        return ResponseWrap.success(vo);
    }

    @GetMapping("/download")
    @Operation(summary = "文件下载", description = "从 MinIO 下载文件，GET 请求。如果路径为目录则压缩为 tar.gz 下载")
    public ResponseEntity<Resource> download(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false) String bucket) {
        try {
            if (minioService.isFile(objectName, bucket)) {
                return downloadSingleFile(objectName, bucket);
            } else if (minioService.isDirectory(objectName, bucket)) {
                return downloadDirectoryAsTar(objectName, bucket);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Download failed: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<Resource> downloadSingleFile(String objectName, String bucket) {
        InputStream inputStream = minioService.downloadFile(objectName, bucket);
        long fileSize = minioService.getFileSize(objectName, bucket);

        String fileName = objectName;
        int lastSlash = objectName.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < objectName.length() - 1) {
            fileName = objectName.substring(lastSlash + 1);
        }

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(new InputStreamResource(inputStream));
    }

    private ResponseEntity<Resource> downloadDirectoryAsTar(String objectName, String bucket) {
        String dirPrefix = objectName.endsWith("/") ? objectName.substring(0, objectName.length() - 1) : objectName;
        List<String> objectNames = minioService.listObjects(dirPrefix, bucket);

        if (objectNames.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String dirName = dirPrefix;
        int lastSlash = dirPrefix.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < dirPrefix.length() - 1) {
            dirName = dirPrefix.substring(lastSlash + 1);
        }
        String archiveName = dirName + ".tar.gz";
        String encodedArchiveName = URLEncoder.encode(archiveName, StandardCharsets.UTF_8).replace("+", "%20");

        PipedInputStream pis = new PipedInputStream(8192);
        try {
            PipedOutputStream pos = new PipedOutputStream(pis);
            Thread tarThread = new Thread(() -> {
                try (TarArchiveOutputStream taos = new TarArchiveOutputStream(
                        new GZIPOutputStream(pos))) {
                    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    for (String obj : objectNames) {
                        InputStream objStream = minioService.downloadFile(obj, bucket);
                        long size = minioService.getFileSize(obj, bucket);
                        String entryName = obj.substring(dirPrefix.length() + 1);
                        TarArchiveEntry entry = new TarArchiveEntry(entryName);
                        entry.setSize(size);
                        taos.putArchiveEntry(entry);
                        try (objStream) {
                            objStream.transferTo(taos);
                        }
                        taos.closeArchiveEntry();
                    }
                } catch (Exception e) {
                    log.error("Failed to create tar.gz: {}", e.getMessage(), e);
                }
            });
            tarThread.setDaemon(true);
            tarThread.start();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedArchiveName + "\"; filename*=UTF-8''" + encodedArchiveName)
                    .body(new InputStreamResource(pis));
        } catch (IOException e) {
            log.error("Failed to setup tar.gz stream: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/list")
    @Operation(summary = "列出目录内容", description = "获取指定目录下的子目录和文件列表（非递归）")
    public ResponseWrap<List<MinioItemVo>> list(
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestParam(value = "bucket", required = false) String bucket) {
        List<Map<String, Object>> items = minioService.listDirectory(path, bucket);
        List<MinioItemVo> voList = items.stream().map(item -> {
            MinioItemVo vo = MinioItemVo.builder()
                    .name((String) item.get("name"))
                    .path((String) item.get("path"))
                    .type((String) item.get("type"))
                    .build();
            if ("file".equals(item.get("type"))) {
                vo.setSize(item.get("size") != null ? ((Number) item.get("size")).longValue() : null);
                vo.setLastModified(item.get("lastModified") != null ? ((Number) item.get("lastModified")).longValue() : null);
            }
            return vo;
        }).collect(Collectors.toList());
        return ResponseWrap.success(voList);
    }

    @DeleteMapping
    @Operation(summary = "文件删除", description = "从 MinIO 删除指定文件")
    public ResponseWrap<Void> delete(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false) String bucket) {
        minioService.deleteFile(objectName, bucket);
        return ResponseWrap.success();
    }
}
