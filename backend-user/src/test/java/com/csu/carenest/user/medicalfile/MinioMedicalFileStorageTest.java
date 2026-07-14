package com.csu.carenest.user.medicalfile;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinioMedicalFileStorageTest {
    @Test
    void signsPreviewUrlAgainstTheBrowserVisibleEndpoint() throws Exception {
        String accessKey = "minioadmin";
        String secretKey = "local-secret";
        MinioMedicalFileStorage storage = new MinioMedicalFileStorage(
                "http://minio:9000", accessKey, secretKey, "smart-nursing", "us-east-1", "http://localhost:19000");
        MinioClient publicSigner = MinioClient.builder().endpoint("http://localhost:19000").region("us-east-1")
                .credentials(accessKey, secretKey).build();

        String expected = publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket("smart-nursing").object("medical/demo.mp3").expiry(600).build());

        assertEquals(expected, storage.presignedGet("medical/demo.mp3", Duration.ofMinutes(10)));
    }
}
