package com.csu.carenest.careadmin.support;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.score.Phase47To48ScoreRepository;
import com.csu.carenest.careadmin.score.Phase47To48ScoreService;
import com.csu.carenest.careadmin.score.ScoreDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证阶段43-48最容易越权或重复计分的核心业务规则。 */
class Phase43To48SupportScoreServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));

    @Test
    void ticketMutationMustMatchOriginalElderAndUrgentTicketNeedsFollowUp() {
        Phase43To46SupportRepository repository = mock(Phase43To46SupportRepository.class);
        Phase43To46SupportService service = new Phase43To46SupportService(
                repository, new ObjectMapper(), mock(Phase47To48ScoreService.class));
        when(repository.hasPermission("admin_1", "CUSTOMER_SERVICE_TICKET_HANDLE")).thenReturn(true);
        when(repository.findTicket("ticket_1")).thenReturn(Optional.of(
                new Phase43To46SupportRepository.TicketContext(
                        "ticket_1", "elder_1", "URGENT", "PENDING", null)));

        SupportDtos.TicketRequest wrongElder = new SupportDtos.TicketRequest(
                "elder_2", "咨询", "URGENT", "处理说明", "MANUAL");
        assertThrows(BusinessRuleException.class,
                () -> service.reply(ADMIN, "ticket_1", wrongElder));

        SupportDtos.TicketRequest correctElder = new SupportDtos.TicketRequest(
                "elder_1", "咨询", "URGENT", "处理说明", "MANUAL");
        when(repository.countFollowUps("ticket_1")).thenReturn(0);
        assertThrows(BusinessRuleException.class,
                () -> service.close(ADMIN, "ticket_1", correctElder));
        verify(repository, never()).closeTicket(anyString(), anyString());
    }

    @Test
    void appealReviewCannotReplaceOriginalTarget() {
        Phase43To46SupportRepository repository = mock(Phase43To46SupportRepository.class);
        Phase43To46SupportService service = new Phase43To46SupportService(
                repository, new ObjectMapper(), mock(Phase47To48ScoreService.class));
        when(repository.hasPermission("admin_1", "NURSE_APPEAL_REVIEW")).thenReturn(true);
        when(repository.findAppeal("appeal_1")).thenReturn(Optional.of(
                new Phase43To46SupportRepository.AppealContext(
                        "appeal_1", "nurse_1", "METRIC", "metric_1", "PENDING")));

        SupportDtos.AppealRequest request = new SupportDtos.AppealRequest(
                "APPROVED", "metric_2", "审核通过", List.of());

        assertThrows(BusinessRuleException.class,
                () -> service.reviewAppeal(ADMIN, "appeal_1", request));
        verify(repository, never()).reviewAppeal(
                anyString(), anyString(), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void scoreRecalculationWritesChangeOnlyWhenScoreActuallyChanges() {
        Phase47To48ScoreRepository repository = mock(Phase47To48ScoreRepository.class);
        Phase47To48ScoreService service = new Phase47To48ScoreService(repository, new ObjectMapper());
        Phase47To48ScoreRepository.ScoreFacts facts = new Phase47To48ScoreRepository.ScoreFacts(
                new BigDecimal("90.00"), 3, new BigDecimal("100.00"), 0,
                null, new BigDecimal("10.00"), new BigDecimal("5.00"), BigDecimal.ZERO);
        when(repository.hasPermission("admin_1", "NURSE_APPEAL_REVIEW")).thenReturn(true);
        when(repository.nurseExists("nurse_1")).thenReturn(true);
        when(repository.currentScore("nurse_1")).thenReturn(Optional.of(new BigDecimal("80.00")));
        when(repository.calculateFacts("nurse_1")).thenReturn(facts);
        when(repository.findLogs("nurse_1", 0, 20)).thenReturn(List.of());

        ScoreDtos.ScoreResponse response = service.recalculate(
                ADMIN, "nurse_1", new ScoreDtos.RecalculateRequest("nurse_1", "manual_1"));

        assertEquals(new BigDecimal("90.00"), response.totalScore());
        verify(repository).insertChangeLog(
                anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void myScoreRejectsRolesOutsideNurseAndAdminManagementScenario() {
        Phase47To48ScoreService service = new Phase47To48ScoreService(
                mock(Phase47To48ScoreRepository.class), new ObjectMapper());

        CurrentUser family = new CurrentUser("family_1", List.of(RoleCode.FAMILY));
        assertThrows(ForbiddenException.class, () -> service.myScore(family, 1, 20));
    }
}
