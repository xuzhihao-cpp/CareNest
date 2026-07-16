package com.csu.carenest.careadmin.delivery;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.score.Phase47To48ScoreRepository;
import com.csu.carenest.careadmin.score.Phase47To48ScoreService;
import com.csu.carenest.careadmin.score.ScoreDtos;
import com.csu.carenest.careadmin.support.Phase43To46SupportRepository;
import com.csu.carenest.careadmin.support.Phase43To46SupportService;
import com.csu.carenest.careadmin.support.SupportDtos;
import com.csu.carenest.careadmin.training.Phase49To50TrainingRepository;
import com.csu.carenest.careadmin.training.Phase49To50TrainingService;
import com.csu.carenest.careadmin.training.TrainingDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 使用真实 Repository SQL 串联验证阶段43-55的成员3数据流。 */
class Phase43To55RepositoryIntegrationTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));
    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));
    private static final CurrentUser FAMILY = new CurrentUser("family_1", List.of(RoleCode.FAMILY));

    private JdbcTemplate jdbcTemplate;
    private Phase43To46SupportService supportService;
    private Phase47To48ScoreService scoreService;
    private Phase49To50TrainingService trainingService;
    private Phase51To55DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=REQUIRED");
        new ResourceDatabasePopulator(
                new ClassPathResource("phase43-55-schema.sql"),
                new ClassPathResource("phase43-55-data.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        scoreService = new Phase47To48ScoreService(
                new Phase47To48ScoreRepository(jdbcTemplate), objectMapper);
        supportService = new Phase43To46SupportService(
                new Phase43To46SupportRepository(jdbcTemplate), objectMapper, scoreService);
        trainingService = new Phase49To50TrainingService(
                new Phase49To50TrainingRepository(jdbcTemplate), objectMapper);
        deliveryService = new Phase51To55DeliveryService(
                new Phase51To55DeliveryRepository(jdbcTemplate),
                mock(DemoDataSeedExecutor.class), objectMapper);
    }

    @Test
    void ticketFollowUpReviewAndCloseUseFrozenStatusChain() {
        SupportDtos.FollowUpResponse followUp = supportService.addFollowUp(
                ADMIN, "ticket_1", new SupportDtos.FollowUpRequest(
                        "PHONE", "Family contacted", null, "RESOLVED"));
        SupportDtos.TicketResponse closed = supportService.close(
                ADMIN, "ticket_1", new SupportDtos.TicketRequest(
                        "elder_1", "CONSULT", "URGENT", "Closed after follow-up", "MANUAL"));
        SupportDtos.ReviewComplaintResponse review = supportService.submitReview(
                FAMILY, "order_1", new SupportDtos.ReviewComplaintRequest(
                        5, List.of("PROFESSIONAL"), "Good service", null, List.of("file_family")));

        assertEquals("RESOLVED", followUp.ticketStatus());
        assertEquals("CLOSED", closed.status());
        assertNotNull(review.reviewId());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_message WHERE ticket_id='ticket_1'", Integer.class));
    }

    @Test
    void scoreIsRebuiltFromCurrentMetricComplaintAndApprovedAppealFacts() {
        ScoreDtos.ScoreResponse response = scoreService.recalculate(
                ADMIN, "nurse_1", new ScoreDtos.RecalculateRequest("nurse_1", "manual_1"));

        assertEquals(new BigDecimal("95.00"), response.totalScore());
        assertEquals("EXCELLENT", response.level());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nurse_score_change_log WHERE nurse_id='nurse_1'", Integer.class));

        scoreService.recalculate(
                ADMIN, "nurse_1", new ScoreDtos.RecalculateRequest("nurse_1", "manual_2"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nurse_score_change_log WHERE nurse_id='nurse_1'", Integer.class));
    }

    @Test
    void publishedArticleMatchesServiceAndRiskThenCanBeMarkedRead() {
        TrainingDtos.ArticleRequest draft = articleRequest("DRAFT");
        TrainingDtos.ArticleResponse created = trainingService.createArticle(ADMIN, draft);
        trainingService.publishArticle(ADMIN, created.articleId(), articleRequest("PUBLISHED"));

        List<TrainingDtos.ReadResponse> recommended =
                trainingService.recommendedArticles(NURSE, "order_1");
        assertTrue(recommended.stream().anyMatch(item -> item.articleId().equals(created.articleId())));

        TrainingDtos.ReadResponse read = trainingService.markRead(
                NURSE, created.articleId(), new TrainingDtos.ReadRequest("order_1", 120));
        assertEquals("READ", read.readStatus());
        assertEquals("READ", jdbcTemplate.queryForObject(
                "SELECT reading_status FROM nurse_article_reading WHERE article_id=?",
                String.class, created.articleId()));

        trainingService.publishArticle(ADMIN, created.articleId(), articleRequest("OFFLINE"));
        assertFalse(trainingService.recommendedArticles(NURSE, "order_1").stream()
                .anyMatch(item -> item.articleId().equals(created.articleId())));
    }

    @Test
    void followUpDashboardAndDemoReadinessUseRealBusinessTables() {
        DeliveryDtos.FollowUpResponse followUp = deliveryService.createFollowUp(
                ADMIN, new DeliveryDtos.FollowUpRequest(
                        "elder_1", "order_1", "PHONE", "Continue observation",
                        LocalDateTime.now().plusDays(1), true));
        LocalDate today = LocalDate.now();
        DeliveryDtos.BasicStatisticsResponse basic =
                deliveryService.basicStatistics(ADMIN, today.minusDays(1), today.plusDays(1));
        DeliveryDtos.QualityStatisticsResponse quality =
                deliveryService.qualityStatistics(ADMIN, today.minusDays(1), today.plusDays(1));
        DeliveryDtos.DemoDataStatusResponse status = deliveryService.demoDataStatus(ADMIN);

        assertNotNull(followUp.createdReminderTaskId());
        assertEquals(new BigDecimal("100.00"), basic.serviceCompletionRate());
        assertEquals(new BigDecimal("100.00"), basic.reminderDoneRate());
        assertEquals(new BigDecimal("100.00"), quality.archiveCompleteRate());
        assertTrue(status.ready());
        assertEquals(8, status.scenarioCount());
    }

    private TrainingDtos.ArticleRequest articleRequest(String status) {
        return new TrainingDtos.ArticleRequest(
                "Fall prevention", "Summary", "/articles/fall", List.of("SAFETY"),
                List.of("service_1"), List.of("FALL_RISK"), true, status);
    }
}
