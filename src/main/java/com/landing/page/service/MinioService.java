package com.landing.page.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:mission-attachments}")
    private String bucketName;

    @Value("${app.submission.max-file-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${app.submission.allowed-extensions:jpg,jpeg,png,pdf,docx}")
    private String allowedExtensions;

    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO Bucket '{}' created successfully.", bucketName);
            }
        } catch (Exception e) {
            log.error("Error checking or creating MinIO bucket", e);
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("InvalidAccessKeyId") || msg.contains("Access Key")) {
                throw new IllegalArgumentException("Lỗi kết nối MinIO Storage: Access Key ID hoặc Secret Key không tồn tại trên MinIO Server (localhost:9000). Vui lòng kiểm tra lại tài khoản MinIO.");
            }
            throw new IllegalArgumentException("Không thể kết nối lưu trữ MinIO Object Storage: " + msg);
        }
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Check file size (max 10MB)
        long maxSizeBytes = maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File size exceeds limit of " + maxFileSizeMb + " MB per file");
        }

        // Check file extension
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null) {
            throw new IllegalArgumentException("Invalid file without extension");
        }

        List<String> allowedList = Arrays.asList(allowedExtensions.toLowerCase().split(","));
        if (!allowedList.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("File type '." + extension + "' is not supported. Allowed types: " + allowedExtensions);
        }
    }

    public String uploadFile(MultipartFile file, String prefixFolder) {
        validateFile(file);
        ensureBucketExists();

        // Keep only the file name part (some browsers send a full client path)
        String originalFilename = StringUtils.getFilename(StringUtils.cleanPath(file.getOriginalFilename()));
        String objectKey = prefixFolder + "/" + UUID.randomUUID().toString() + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Uploaded file '{}' to MinIO as '{}'", originalFilename, objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return;
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("Deleted object '{}' from MinIO", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object '{}' from MinIO: {}", objectKey, e.getMessage());
        }
    }

    public InputStream getFileStream(String objectKey) {
        ensureBucketExists();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to fetch object '{}' from MinIO", objectKey, e);
            throw new RuntimeException("Error fetching file from MinIO: " + e.getMessage(), e);
        }
    }

    public StatObjectResponse getFileStat(String objectKey) {
        ensureBucketExists();
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to stat object '{}' from MinIO: {}", objectKey, e.getMessage());
            return null;
        }
    }

    public String getFileUrl(String objectKey) {
        // Return direct URL endpoint handled by backend controller
        return "/api/submissions/files/download?key=" + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
    }
}
