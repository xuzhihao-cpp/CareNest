package com.csu.carenest.user.medicalfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase20MedicalFileApiTest.TestApplication.class)
@AutoConfigureMockMvc
@Import(Phase20MedicalFileApiTest.TestStorageConfiguration.class)
@Transactional
class Phase20MedicalFileApiTest {

    private static final String DEMO_PASSWORD = "Demo@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MedicalFileStorage medicalFileStorage;

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(pdfUpload(null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedUserUploadsRealPdfAsset() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(pdfUpload(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty())
                .andExpect(jsonPath("$.data.originalName").value("report.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"));

        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_asset WHERE uploaded_by='family-001'", Integer.class));
    }

    @Test
    void familyRegistersThenElderReloadsPersistedMedicalFile() throws Exception {
        String familyToken = loginAndReadToken("family_demo");
        String fileId = uploadAndReadFileId(familyToken);
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", fileId,
                "fileType", "EXAMINATION_REPORT",
                "title", "血压检查报告",
                "occurredAt", "2026-07-01"));

        mockMvc.perform(post("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"));

        String elderToken = loginAndReadToken("elder_demo");
        mockMvc.perform(get("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + elderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("血压检查报告"))
                .andExpect(jsonPath("$.data[0].auditStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].objectKey").doesNotExist());
    }

    @Test
    void nurseCannotReadPendingMedicalFiles() throws Exception {
        String token = loginAndReadToken("nurse_demo");
        mockMvc.perform(get("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void pendingBindingCannotRegisterMedicalFile() throws Exception {
        String token = loginAndReadToken("family_demo");
        String fileId = uploadAndReadFileId(token);
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", fileId, "fileType", "PRESCRIPTION", "title", "处方", "occurredAt", "2026-07-01"));
        mockMvc.perform(post("/api/v1/elders/elder_002/medical-files")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void rejectsEmptyAndDisguisedPdfFiles() throws Exception {
        String token = loginAndReadToken("family_demo");
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "plain text".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void rejectsExtensionMismatchAndIncompletePngSignature() throws Exception {
        String token = loginAndReadToken("family_demo");
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "report.txt", "application/pdf", pdf))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));

        byte[] incompletePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "scan.png", "image/png", incompletePng))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void acceptsCompletePngSignature() throws Exception {
        String token = loginAndReadToken("family_demo");
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "scan.PNG", "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mimeType").value("image/png"));
    }

    @Test
    void acceptsBrowserWebmRecordingWithCodecMimeParameter() throws Exception {
        String token = loginAndReadToken("elder_demo");
        byte[] webm = new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0x01, 0x00, 0x00, 0x00};

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file", "health-feedback.webm", "audio/webm;codecs=opus", webm))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("health-feedback.webm"))
                .andExpect(jsonPath("$.data.mimeType").value("audio/webm"));
    }

    @Test
    void rejectsFilesOverTwentyMebibytes() throws Exception {
        String token = loginAndReadToken("family_demo");
        byte[] oversized = new byte[(20 * 1024 * 1024) + 1];
        byte[] signature = "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, oversized, 0, signature.length);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "oversized.pdf", "application/pdf", oversized))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void removesStoredObjectWhenPresignedResponseCannotBeCreated() throws Exception {
        String token = loginAndReadToken("family_demo");
        org.mockito.Mockito.doThrow(new IllegalStateException("signing unavailable"))
                .when(medicalFileStorage)
                .presignedGet(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        try {
            mockMvc.perform(pdfUpload(token))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));

            org.mockito.Mockito.verify(medicalFileStorage)
                    .remove(org.mockito.ArgumentMatchers.anyString());
        } finally {
            org.mockito.Mockito.reset(medicalFileStorage);
        }
    }

    @Test
    void rejectsDuplicateRegistrationAndAssetOwnedByAnotherUser() throws Exception {
        String token = loginAndReadToken("family_demo");
        String fileId = uploadAndReadFileId(token);
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", fileId, "fileType", "PRESCRIPTION", "title", "处方", "occurredAt", "2026-07-01"));
        mockMvc.perform(post("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        jdbcTemplate.update("""
                INSERT INTO file_asset
                  (file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by)
                VALUES ('foreign_file', 'foreign.pdf', 'application/pdf', 10, 'test-medical', 'foreign.pdf', 'PENDING', 'admin-001')
                """);
        String foreignPayload = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", "foreign_file", "fileType", "PRESCRIPTION", "title", "他人文件", "occurredAt", "2026-07-01"));
        mockMvc.perform(post("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(foreignPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String uploadAndReadFileId(String token) throws Exception {
        String response = mockMvc.perform(pdfUpload(token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("fileId").asText();
    }

    private MockHttpServletRequestBuilder pdfUpload(String token) {
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockHttpServletRequestBuilder request = multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "report.pdf", "application/pdf", pdf))
                .contentType(MediaType.MULTIPART_FORM_DATA);
        request.characterEncoding("UTF-8");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private String loginAndReadToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "password", DEMO_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("token").asText();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }

    @TestConfiguration
    static class TestStorageConfiguration {
        @Bean
        @Primary
        MedicalFileStorage testMedicalFileStorage() {
            return org.mockito.Mockito.mock(MedicalFileStorage.class);
        }
    }
}
