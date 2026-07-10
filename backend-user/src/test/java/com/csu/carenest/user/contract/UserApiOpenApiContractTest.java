package com.csu.carenest.user.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UserApiOpenApiContractTest.TestApplication.class)
@AutoConfigureMockMvc
class UserApiOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesImplementedUserFlowContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.paths['/api/v1/family/bindings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/family/bindings'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/service-addresses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/demo-data/status'].get").exists());
    }

    @Test
    void matchesCommittedOpenApiSnapshot() throws Exception {
        String currentDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode current = objectMapper.readTree(currentDocument);
        Path snapshotPath = Path.of("..", "contracts", "user-api-v1.json").normalize();

        if (Boolean.getBoolean("updateOpenApiSnapshot")) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current) + System.lineSeparator());
        }

        assertTrue(Files.exists(snapshotPath), "OpenAPI snapshot is missing: " + snapshotPath);
        JsonNode committed = objectMapper.readTree(Files.readString(snapshotPath));
        assertEquals(committed, current, "Backend contract changed; update the snapshot intentionally");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }
}
