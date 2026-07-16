package com.csu.carenest.user.ai;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void normalFallbackCreatesNoTickets() {
        when(provider.answer("我可以停药吗"))
                .thenReturn(new AiProvider.Result("请联系医生确认。", "NORMAL", "DAILY_CARE", "NORMAL"));

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1", new AiAssistantDtos.MessageRequest("我可以停药吗", "TEXT", null));

        assertFalse(result.riskFlag());
        assertNull(result.assistanceTicketId());
        assertFalse(result.customerServiceTicketCreated());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(repository, never()).customerTicket(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void warningCreatesNoTickets() {
        when(provider.answer("medication question"))
                .thenReturn(new AiProvider.Result("consult a clinician", "WARNING", "MEDICATION", "NORMAL"));

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1", new AiAssistantDtos.MessageRequest("medication question", "TEXT", null));

        assertTrue(result.riskFlag());
        assertNull(result.assistanceTicketId());
        assertFalse(result.customerServiceTicketCreated());
        verify(repository, never()).assistance(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(repository, never()).customerTicket(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void criticalCreatesAssistanceAndCustomerServiceTickets() {
        when(provider.answer("我想死"))
                .thenReturn(new AiProvider.Result("请立即联系家属或当地急救。", "CRITICAL", "EMERGENCY", "URGENT"));

        AiAssistantDtos.MessageResult result = service.message(
                "Bearer token", "session-1", new AiAssistantDtos.MessageRequest("我想死", "TEXT", null));

        assertTrue(result.riskFlag());
        assertTrue(result.customerServiceTicketCreated());
        verify(repository).assistance(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(repository).customerTicket(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
