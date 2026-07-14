package com.csu.carenest.user.medicalfile;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@Component
public class MinioMedicalFileStorage extends MedicalFileStorage {
    private final MinioClient storageClient;
    private final MinioClient publicClient;
    private final String bucket;

    public MinioMedicalFileStorage(
            @Value("${carenest.minio.endpoint}") String endpoint,
            @Value("${carenest.minio.access-key}") String accessKey,
            @Value("${carenest.minio.secret-key}") String secretKey,
            @Value("${carenest.minio.bucket}") String bucket,
            @Value("${carenest.minio.region:us-east-1}") String region,
            @Value("${carenest.minio.public-endpoint}") String publicEndpoint) {
        this.storageClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).region(region).build();
        this.publicClient = MinioClient.builder().endpoint(publicEndpoint).credentials(accessKey, secretKey).region(region).build();
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, String contentType, MultipartFile file) {
        try {
            storageClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectKey).contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1).build());
        } catch (Exception exception) {
            throw new IllegalStateException("文件存储失败", exception);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            storageClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("文件清理失败", exception);
        }
    }

    @Override
    public String presignedGet(String objectKey, Duration duration) {
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey)
                    .expiry(Math.toIntExact(duration.toSeconds())).build());
        } catch (Exception exception) {
            throw new IllegalStateException("文件预览地址生成失败", exception);
        }
    }
}
