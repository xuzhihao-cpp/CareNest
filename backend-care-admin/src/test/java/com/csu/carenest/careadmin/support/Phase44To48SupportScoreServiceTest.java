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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证阶段44-48最容易越权或重复计分的核心业务规则。 */
class Phase44To48SupportScoreServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));

    @Test
    void appealReviewCannotReplaceOriginalTarget() {
        Phase44To46SupportRepository repository = mock(Phase44To46SupportRepository.class);
        Phase44To46SupportService service = new Phase44To46SupportService(
                repository, new ObjectMapper(), mock(Phase47To48ScoreService.class));
        when(repository.hasPermission("admin_1", "NURSE_APPEAL_REVIEW")).thenReturn(true);
        when(repository.findAppeal("appeal_1")).thenReturn(Optional.of(
                new Phase44To46SupportRepository.AppealContext(
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
    void resolvedComplaintRecalculationCreatesComplaintScoreChange() {
        Phase47To48ScoreRepository repository = mock(Phase47To48ScoreRepository.class);
        Phase47To48ScoreService service = new Phase47To48ScoreService(repository, new ObjectMapper());
        Phase47To48ScoreRepository.ScoreFacts facts = new Phase47To48ScoreRepository.ScoreFacts(
                new BigDecimal("95.00"), 1, new BigDecimal("100.00"), 1,
                null, BigDecimal.ZERO, new BigDecimal("5.00"), BigDecimal.ZERO);
        when(repository.nurseExists("nurse_1")).thenReturn(true);
        when(repository.currentScore("nurse_1")).thenReturn(Optional.of(new BigDecimal("100.00")));
        when(repository.calculateFacts("nurse_1")).thenReturn(facts);
        when(repository.findLogs("nurse_1", 0, 20)).thenReturn(List.of());

        service.recalculateAfterComplaint(ADMIN, "nurse_1", "complaint_1");

        verify(repository).insertChangeLog(
                anyString(), org.mockito.ArgumentMatchers.eq("nurse_1"),
                org.mockito.ArgumentMatchers.eq("COMPLAINT"),
                org.mockito.ArgumentMatchers.eq("complaint_1"),
                any(BigDecimal.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void myScoreRejectsRolesOutsideNurseAndAdminManagementScenario() {
        Phase47To48ScoreService service = new Phase47To48ScoreService(
                mock(Phase47To48ScoreRepository.class), new ObjectMapper());

        CurrentUser family = new CurrentUser("family_1", List.of(RoleCode.FAMILY));
        assertThrows(ForbiddenException.class, () -> service.myScore(family, 1, 20));
    }

    @Test
    void adminReviewsExposeStoredReviewContentWithoutScoreOrApprovalFlow() {
        Phase44To46SupportRepository repository = mock(Phase44To46SupportRepository.class);
        Phase47To48ScoreService scoreService = mock(Phase47To48ScoreService.class);
        Phase44To46SupportService service = new Phase44To46SupportService(
                repository, new ObjectMapper(), scoreService);
        when(repository.hasPermission("admin_1", "COMPLAINT_HANDLE")).thenReturn(true);
        when(repository.findReviews()).thenReturn(List.of(
                new Phase44To46SupportRepository.ReviewRecord(
                        "review_1", "order_1", "基础上门护理", "长辈演示账号",
                        "家属演示账号", "FAMILY", 5, 5,
                        "{\"content\":\"服务很好\",\"tags\":[\"态度认真\"],\"fileIds\":[]}",
                        java.time.LocalDateTime.of(2026, 7, 17, 10, 0))));

        List<SupportDtos.ReviewResponse> reviews = service.reviews(ADMIN);

        assertEquals(1, reviews.size());
        assertEquals("服务很好", reviews.get(0).content());
        assertEquals(List.of("态度认真"), reviews.get(0).tags());
        verify(repository).findReviews();
        verifyNoInteractions(scoreService);
    }
}
