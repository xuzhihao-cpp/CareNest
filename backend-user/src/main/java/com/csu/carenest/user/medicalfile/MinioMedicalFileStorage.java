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
import java.net.URI;

@Component
public class MinioMedicalFileStorage extends MedicalFileStorage {
    private final MinioClient client;
    private final String bucket;
    private final URI publicEndpoint;

    public MinioMedicalFileStorage(
            @Value("${carenest.minio.endpoint}") String endpoint,
            @Value("${carenest.minio.access-key}") String accessKey,
            @Value("${carenest.minio.secret-key}") String secretKey,
            @Value("${carenest.minio.bucket}") String bucket,
            @Value("${carenest.minio.public-endpoint}") String publicEndpoint) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.publicEndpoint = URI.create(publicEndpoint);
    }

    @Override
    public void put(String objectKey, String contentType, MultipartFile file) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectKey).contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1).build());
        } catch (Exception exception) {
            throw new IllegalStateException("文件存储失败", exception);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("文件清理失败", exception);
        }
    }

    @Override
    public String presignedGet(String objectKey, Duration duration) {
        try {
            URI internal = URI.create(client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey)
                    .expiry(Math.toIntExact(duration.toSeconds())).build()));
            return new URI(publicEndpoint.getScheme(), publicEndpoint.getAuthority(),
                    internal.getPath(), internal.getQuery(), null).toString();
        } catch (Exception exception) {
            throw new IllegalStateException("文件预览地址生成失败", exception);
        }
    }
}
