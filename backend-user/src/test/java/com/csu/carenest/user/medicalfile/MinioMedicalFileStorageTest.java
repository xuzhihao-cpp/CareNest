package com.csu.carenest.user.medicalfile;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioMedicalFileStorageTest {
    @Test
    void signsPreviewUrlAgainstTheBrowserVisibleEndpoint() throws Exception {
        String accessKey = "minioadmin";
        String secretKey = "local-secret";
        MinioMedicalFileStorage storage = new MinioMedicalFileStorage(
                "http://minio:9000", accessKey, secretKey, "smart-nursing", "us-east-1", "http://localhost:19000");
        URI preview = URI.create(storage.presignedGet("medical/demo.mp3", Duration.ofMinutes(10)));
        String query = preview.getRawQuery();
        String decodedQuery = preview.getQuery();

        assertEquals("http", preview.getScheme());
        assertEquals("localhost", preview.getHost());
        assertEquals(19000, preview.getPort());
        assertEquals("/smart-nursing/medical/demo.mp3", preview.getPath());
        assertTrue(query.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
        assertTrue(decodedQuery.contains("X-Amz-Credential=" + accessKey + "/"));
        assertTrue(decodedQuery.contains("/us-east-1/s3/aws4_request"));
        assertTrue(query.contains("X-Amz-Expires=600"));
        assertTrue(query.contains("X-Amz-SignedHeaders=host"));
        assertTrue(query.contains("X-Amz-Signature="));
    }
}
