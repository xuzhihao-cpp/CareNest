package com.csu.carenest.user.ai;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AiAssistantServiceTest {
    private AuthService auth;
    private AiAssistantRepository repository;
    private AiProvider provider;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        repository = mock(AiAssistantRepository.class);
        provider = mock(AiProvider.class);
        service = new AiAssistantService(auth, repository, provider);

        when(auth.requireCurrentUser("Bearer token"))
                .thenReturn(new AuthService.CurrentUser("elder-user", List.of(RoleCode.ELDER)));
        when(repository.sessionOwner("session-1")).thenReturn("elder-user");
        when(repository.sessionElder("session-1")).thenReturn(Optional.of("elder-1"));
    }

    @Test
    void normalQuestionCallsProviderAndCreatesNoTickets() {
        when(provider.answer("今天怎么安排喝水"))
                .thenReturn(new AiProvider.Result("可以分多次少量饮水。", "NORMAL", "DAILY_CARE", "NORMAL"));

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("今天怎么安排喝水", "TEXT", null));

        assertFalse(result.riskFlag());
        assertNull(result.assistanceTicketId());
        assertFalse(result.customerServiceTicketCreated());
        assertFalse(result.followUpRequired());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(repository, never()).customerTicket(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void followUpQuestionPersistsContextAndCreatesNoTicket() {
        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("我肚子疼", "TEXT", null));

        assertEquals("FOLLOW_UP", result.triageLevel());
        assertEquals("ABDOMINAL_PAIN", result.triageCategory());
        assertTrue(result.followUpRequired());
        assertNull(result.assistanceTicketId());
        verify(repository).updateTriageContext(anyString(), any(AiTriageResult.class), anyString(), anyString());
        verify(provider, never()).answer(anyString());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void medicationQuestionIsWarningWithoutCallingProvider() {
        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("这个药能不能停", "TEXT", null));

        assertEquals("WARNING", result.safetyLevel());
        assertTrue(result.riskFlag());
        assertNull(result.assistanceTicketId());
        verify(provider, never()).answer(anyString());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void criticalCreatesAssistanceAndCustomerServiceTickets() {
        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("我呼吸困难，喘不上气", "TEXT", null));

        assertEquals("CRITICAL", result.safetyLevel());
        assertTrue(result.riskFlag());
        assertTrue(result.customerServiceTicketCreated());
        verify(provider, never()).answer(anyString());
        verify(repository).assistance(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(repository).customerTicket(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void dangerousFollowUpAnswerEscalatesAndClearsContext() {
        when(repository.triageContext("session-1"))
                .thenReturn(Optional.of(new AiAssistantRepository.TriageContext(
                        "FOLLOW_UP", "ABDOMINAL_PAIN", "follow-up", "我肚子疼",
                        "ABDOMINAL_PAIN:old", true)));

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("现在一直呕吐而且便血", "TEXT", null));

        assertEquals("CRITICAL", result.safetyLevel());
        assertTrue(result.customerServiceTicketCreated());
        verify(repository).clearTriageContext("session-1");
        verify(provider, never()).answer(anyString());
    }

    @Test
    void repeatedCriticalMessageDoesNotCreateDuplicateTicket() {
        when(repository.hasUrgentFingerprint(anyString(), anyString())).thenReturn(true);

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1",
                new AiAssistantDtos.MessageRequest("我呼吸困难", "TEXT", null));

        assertEquals("CRITICAL", result.safetyLevel());
        assertFalse(result.customerServiceTicketCreated());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }
}
