package com.csu.carenest.careadmin.phase;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioPhase25MedicalFileStorage implements Phase25MedicalFileStorage {
    private final MinioClient client;

    public MinioPhase25MedicalFileStorage(
            @Value("${carenest.minio.endpoint}") String endpoint,
            @Value("${carenest.minio.access-key}") String accessKey,
            @Value("${carenest.minio.secret-key}") String secretKey,
            @Value("${carenest.minio.region:us-east-1}") String region) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).region(region).build();
    }

    @Override
    public byte[] read(String bucket, String objectKey) {
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read approved medical file", exception);
        }
    }
}
