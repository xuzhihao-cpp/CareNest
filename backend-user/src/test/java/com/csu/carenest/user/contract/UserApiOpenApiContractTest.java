package com.csu.carenest.user.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
                .andExpect(jsonPath("$.paths['/api/v1/elder/bindings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/service-addresses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elder/reports/{reportId}/ack'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/family/reports/{reportId}/ack'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/family/reports/{reportId}/archive-suggestions/decision'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/health-archive'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/health-archive'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/medications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/files'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/medical-files'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders/{elderId}/medical-files'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elder/health-feedback'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/family/elders/{elderId}/health-feedback'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elder/reminders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elder/reminders/{reminderId}/actions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elder/reminders/records'].get").exists())
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
        Path snapshotPath = Path.of("..", "contracts", "user-api-v1.json").normalize();
        assertTrue(Files.exists(snapshotPath), "OpenAPI snapshot is missing: " + snapshotPath);
        JsonNode committed = objectMapper.readTree(Files.readString(snapshotPath));
        JsonNode current = normalizeContract(objectMapper.readTree(currentDocument), committed);

        if (Boolean.getBoolean("updateOpenApiSnapshot")) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current) + System.lineSeparator());
        }

        committed = objectMapper.readTree(Files.readString(snapshotPath));
        assertEquals(committed, current, "Backend contract changed; update the snapshot intentionally");
    }

    private JsonNode normalizeContract(JsonNode document, JsonNode committed) {
        ObjectNode normalized = document.deepCopy();
        ObjectNode paths = (ObjectNode) normalized.path("paths");
        Set<String> allowedPaths = new HashSet<>();
        committed.path("paths").fieldNames().forEachRemaining(allowedPaths::add);
        allowedPaths.add("/api/v1/elder/reports/{reportId}/ack");
        allowedPaths.add("/api/v1/elder/bindings");
        allowedPaths.add("/api/v1/family/reports/{reportId}/ack");
        allowedPaths.add("/api/v1/family/reports/{reportId}/archive-suggestions/decision");
        allowedPaths.add("/api/v1/elders/{elderId}/health-archive");
        allowedPaths.add("/api/v1/elders/{elderId}/medications");
        allowedPaths.add("/api/v1/files");
        allowedPaths.add("/api/v1/elders/{elderId}/medical-files");
        allowedPaths.add("/api/v1/elder/health-feedback");
        allowedPaths.add("/api/v1/family/elders/{elderId}/health-feedback");
        allowedPaths.add("/api/v1/elder/reminders");
        allowedPaths.add("/api/v1/elder/reminders/{reminderId}/actions");
        allowedPaths.add("/api/v1/elder/reminders/records");
        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String path = pathNames.next();
            if (!allowedPaths.contains(path)) {
                pathNames.remove();
            }
        }

        ObjectNode schemas = (ObjectNode) normalized.path("components").path("schemas");
        Set<String> reachableSchemas = new HashSet<>();
        collectSchemaReferences(normalized.path("paths"), schemas, reachableSchemas);
        Iterator<Map.Entry<String, JsonNode>> schemaFields = schemas.fields();
        while (schemaFields.hasNext()) {
            if (!reachableSchemas.contains(schemaFields.next().getKey())) {
                schemaFields.remove();
            }
        }
        return normalized;
    }

    private void collectSchemaReferences(JsonNode node, ObjectNode schemas, Set<String> reachableSchemas) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual()) {
                String prefix = "#/components/schemas/";
                String value = reference.asText();
                if (value.startsWith(prefix)) {
                    String schemaName = value.substring(prefix.length());
                    if (reachableSchemas.add(schemaName) && schemas.has(schemaName)) {
                        collectSchemaReferences(schemas.get(schemaName), schemas, reachableSchemas);
                    }
                }
            }
            node.elements().forEachRemaining(child -> collectSchemaReferences(child, schemas, reachableSchemas));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectSchemaReferences(child, schemas, reachableSchemas));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }
}
