package com.csu.carenest.careadmin.reminder;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.GlobalExceptionHandler;
import com.csu.carenest.careadmin.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReminderControllerTest {

    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));
    private static final CurrentUser OTHER_NURSE = new CurrentUser("nurse_2", List.of(RoleCode.NURSE));

    @Mock private AuthService authService;

    private MockMvc mockMvc;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=REQUIRED");
        new ResourceDatabasePopulator(
                new ClassPathResource("phase44-55-schema.sql"),
                new ClassPathResource("phase44-55-data.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        ReminderRepository repository = new ReminderRepository(jdbcTemplate);
        ReminderService service = new ReminderService(authService, repository, mock(HomeCacheInvalidator.class));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReminderController(authService, service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void nurseCanListCreateAndUpdateOwnElderReminders() throws Exception {
        when(authService.requireRole("Bearer token", RoleCode.NURSE)).thenReturn(NURSE);
        doNothing().when(authService).requirePermission(any(), anyString());

        mockMvc.perform(get("/api/v1/nurse/elders/elder_1/reminders")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reminderId").value("reminder_1"));

        mockMvc.perform(post("/api/v1/nurse/elders/elder_1/reminders")
                        .header("Authorization", "Bearer token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reminderType":"CUSTOM",
                                  "title":"夜间喝水提醒",
                                  "content":"请在睡前补充温水",
                                  "scheduledAt":"2026-07-18T21:00:00",
                                  "reminderStatus":"PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("夜间喝水提醒"))
                .andExpect(jsonPath("$.data.sourceType").value("NURSE_MANUAL"));

        String newReminderId = jdbcTemplate.queryForObject("""
                SELECT task_id FROM reminder_task WHERE title = '夜间喝水提醒'
                """, String.class);

        mockMvc.perform(put("/api/v1/nurse/elders/elder_1/reminders/" + newReminderId)
                        .header("Authorization", "Bearer token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reminderType":"CUSTOM",
                                  "title":"睡前喝水提醒",
                                  "content":"请在睡前补充温水",
                                  "scheduledAt":"2026-07-18T21:30:00",
                                  "reminderStatus":"PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("睡前喝水提醒"));

        mockMvc.perform(delete("/api/v1/nurse/elders/elder_1/reminders/reminder_1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(delete("/api/v1/nurse/elders/elder_1/reminders/" + newReminderId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminderId").value(newReminderId));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reminder_task WHERE elder_id='elder_1'", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log WHERE biz_type='REMINDER_TASK'", Integer.class));
    }

    @Test
    void permissionAndAssignmentAreBothEnforced() throws Exception {
        when(authService.requireRole("Bearer token", RoleCode.NURSE)).thenReturn(NURSE);
        doThrow(new ForbiddenException()).when(authService).requirePermission(any(), anyString());
        mockMvc.perform(get("/api/v1/nurse/elders/elder_1/reminders")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        when(authService.requireRole("Bearer token-2", RoleCode.NURSE)).thenReturn(OTHER_NURSE);
        mockMvc.perform(get("/api/v1/nurse/elders/elder_1/reminders")
                        .header("Authorization", "Bearer token-2"))
                .andExpect(status().isForbidden());
    }
}
