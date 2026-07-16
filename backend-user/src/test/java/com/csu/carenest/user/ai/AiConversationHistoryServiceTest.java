package com.csu.carenest.user.ai;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationHistoryServiceTest {
    private AuthService auth;
    private AiAssistantRepository repository;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        repository = mock(AiAssistantRepository.class);
        service = new AiAssistantService(auth, repository, mock(AiProvider.class));
    }

    @Test
    void elderListingIgnoresForeignElderIdAndStaysSelfScoped() {
        when(auth.requireCurrentUser("Bearer elder"))
                .thenReturn(new AuthService.CurrentUser("elder-user", List.of(RoleCode.ELDER)));
        when(repository.elderByUser("elder-user"))
                .thenReturn(Optional.of(new AiAssistantRepository.Elder("elder-1", "Elder", "elder-user")));
        AiAssistantDtos.SessionSummary summary = summary("session-2", "elder-1");
        when(repository.sessionsForElder("elder-1", "elder-user", 20, 0)).thenReturn(List.of(summary));
        when(repository.sessionCountForElder("elder-1", "elder-user")).thenReturn(1L);

        AiAssistantDtos.PageResult<AiAssistantDtos.SessionSummary> result =
                service.listSessions("Bearer elder", "other-elder", 1, 20);

        assertEquals("session-2", result.records().get(0).sessionId());
        verify(repository).sessionsForElder("elder-1", "elder-user", 20, 0);
    }

    @Test
    void familyListingUsesOnlyActiveBoundElderFilter() {
        when(auth.requireCurrentUser("Bearer family"))
                .thenReturn(new AuthService.CurrentUser("family-user", List.of(RoleCode.FAMILY)));
        AiAssistantDtos.SessionSummary summary = summary("session-2", "elder-2");
        when(repository.sessionsForFamily("family-user", "elder-2", 20, 0)).thenReturn(List.of(summary));
        when(repository.sessionCountForFamily("family-user", "elder-2")).thenReturn(1L);

        AiAssistantDtos.PageResult<AiAssistantDtos.SessionSummary> result =
                service.listSessions("Bearer family", "elder-2", 1, 20);

        assertEquals("elder-2", result.records().get(0).elderId());
        verify(repository).sessionsForFamily("family-user", "elder-2", 20, 0);
    }

    @Test
    void rejectsUnauthorizedMessageHistoryAccess() {
        when(auth.requireCurrentUser("Bearer family"))
                .thenReturn(new AuthService.CurrentUser("family-user", List.of(RoleCode.FAMILY)));
        when(repository.sessionOwner("other-session")).thenReturn("other-user");
        when(repository.sessionElder("other-session")).thenReturn(Optional.of("elder-2"));
        when(repository.bound("family-user", "elder-2")).thenReturn(false);

        assertThrows(ApiException.class, () -> service.messages("Bearer family", "other-session"));
    }

    @Test
    void rejectsFamilySessionOwnerWithoutActiveBindingFromMessageHistory() {
        when(auth.requireCurrentUser("Bearer family"))
                .thenReturn(new AuthService.CurrentUser("family-user", List.of(RoleCode.FAMILY)));
        when(repository.sessionOwner("family-session")).thenReturn("family-user");
        when(repository.sessionElder("family-session")).thenReturn(Optional.of("elder-2"));
        when(repository.bound("family-user", "elder-2")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.messages("Bearer family", "family-session"));

        assertEquals(403, exception.code());
    }

    @Test
    void returnsNotFoundWhenSessionDoesNotExistBeforeReadingOwner() {
        when(auth.requireCurrentUser("Bearer elder"))
                .thenReturn(new AuthService.CurrentUser("elder-user", List.of(RoleCode.ELDER)));
        when(repository.sessionElder("missing-session")).thenReturn(Optional.empty());
        when(repository.sessionOwner("missing-session")).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.messages("Bearer elder", "missing-session"));

        assertEquals(404, exception.code());
        assertEquals("session not found", exception.getMessage());
    }

    @Test
    void rejectsFamilySessionOwnerWithoutActiveBindingFromSendingOrClosing() {
        when(auth.requireCurrentUser("Bearer family"))
                .thenReturn(new AuthService.CurrentUser("family-user", List.of(RoleCode.FAMILY)));
        when(repository.sessionOwner("family-session")).thenReturn("family-user");
        when(repository.sessionElder("family-session")).thenReturn(Optional.of("elder-2"));
        when(repository.bound("family-user", "elder-2")).thenReturn(false);

        ApiException sendException = assertThrows(ApiException.class, () -> service.message(
                "Bearer family", "family-session", new AiAssistantDtos.MessageRequest("Question", "TEXT", null)));
        ApiException closeException = assertThrows(ApiException.class,
                () -> service.close("Bearer family", "family-session"));

        assertEquals(403, sendException.code());
        assertEquals(403, closeException.code());
    }

    @Test
    void rejectsInvalidHistoryPagination() {
        when(auth.requireCurrentUser("Bearer elder"))
                .thenReturn(new AuthService.CurrentUser("elder-user", List.of(RoleCode.ELDER)));

        assertThrows(ApiException.class, () -> service.listSessions("Bearer elder", null, 0, 20));
        assertThrows(ApiException.class, () -> service.listSessions("Bearer elder", null, 1, 51));
    }

    @Test
    void returnsMessagesInRepositoryAscendingOrder() {
        when(auth.requireCurrentUser("Bearer elder"))
                .thenReturn(new AuthService.CurrentUser("elder-user", List.of(RoleCode.ELDER)));
        when(repository.sessionOwner("session-2")).thenReturn("elder-user");
        when(repository.sessionElder("session-2")).thenReturn(Optional.of("elder-1"));
        when(repository.elderByUser("elder-user"))
                .thenReturn(Optional.of(new AiAssistantRepository.Elder("elder-1", "Elder", "elder-user")));
        List<AiAssistantDtos.ConversationMessage> messages = List.of(
                new AiAssistantDtos.ConversationMessage("message-1", "USER", "TEXT", "Question", false, "NORMAL", "2026-07-16T10:00:00"),
                new AiAssistantDtos.ConversationMessage("message-2", "ASSISTANT", "TEXT", "Answer", true, "CRITICAL", "2026-07-16T10:00:01"));
        when(repository.messages("session-2")).thenReturn(messages);

        List<AiAssistantDtos.ConversationMessage> result = service.messages("Bearer elder", "session-2");

        assertEquals("ASSISTANT", result.get(1).senderRole());
        assertEquals("CRITICAL", result.get(1).safetyLevel());
    }

    private AiAssistantDtos.SessionSummary summary(String sessionId, String elderId) {
        return new AiAssistantDtos.SessionSummary(sessionId, elderId, "Elder", "Conversation", "ACTIVE", "NORMAL",
                "Latest message", "2026-07-16T10:00:00", "2026-07-16T10:01:00");
    }
}
