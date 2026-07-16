package com.csu.carenest.user.reminder;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ReminderApiTest.TestApplication.class)
@AutoConfigureMockMvc
@Transactional
class ReminderApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void elderCanListAndExecuteReminderAndReadRecord() throws Exception {
        String token = login("elder_demo");
        mockMvc.perform(get("/api/v1/elder/reminders").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
        mockMvc.perform(post("/api/v1/elder/reminders/reminder_001/actions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("action", "DONE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reminder.status").value("DONE"));
        mockMvc.perform(get("/api/v1/elder/reminders/records").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].toStatus").value("DONE"));
        assertEquals("DONE", jdbc.queryForObject("SELECT reminder_status FROM reminder_task WHERE task_id='reminder_001'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='REMINDER_ACTION'", Integer.class));
    }

    @Test
    void nonElderCannotReadOrActAndInvalidActionDoesNotChangeState() throws Exception {
        String family = login("family_demo");
        mockMvc.perform(get("/api/v1/elder/reminders").header("Authorization", bearer(family))).andExpect(status().isForbidden());
        String elder = login("elder_demo");
        mockMvc.perform(post("/api/v1/elder/reminders/reminder_001/actions").header("Authorization", bearer(elder))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("action", "INVALID"))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("PENDING", jdbc.queryForObject("SELECT reminder_status FROM reminder_task WHERE task_id='reminder_001'", String.class));
    }

    @Test
    void snoozeRequiresBoundedMinutesAndCreatesExecutionRecord() throws Exception {
        String elder = login("elder_demo");
        mockMvc.perform(post("/api/v1/elder/reminders/reminder_001/actions").header("Authorization", bearer(elder))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("action", "SNOOZE", "snoozeMinutes", 30))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reminder.status").value("SNOOZED"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reminder_record WHERE result='SNOOZED'", Integer.class));
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "Demo@123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {}
}
