package com.csu.carenest.careadmin.training;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.csu.carenest.careadmin.redis.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证阶段49-50文章状态、推荐范围和护理员访问边界。 */
class Phase49To50TrainingServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));
    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));
    private static final CurrentUser OTHER_NURSE = new CurrentUser("nurse_2", List.of(RoleCode.NURSE));

    @Test
    void articleStartsAsDraftAndKeepsNormalizedRecommendationRules() {
        Phase49To50TrainingRepository repository = mock(Phase49To50TrainingRepository.class);
        Phase49To50TrainingService service = new Phase49To50TrainingService(
                repository, new ObjectMapper(), mock(RedisCacheService.class));
        when(repository.hasPermission("admin_1", "TRAINING_ARTICLE_MANAGE")).thenReturn(true);
        when(repository.serviceExists("service_1")).thenReturn(true);
        when(repository.findArticleResponse(anyString())).thenAnswer(invocation -> Optional.of(
                new TrainingDtos.ArticleResponse(invocation.getArgument(0), "术后照护", "摘要",
                        "/articles/1", List.of("术后"), List.of("service_1"),
                        List.of("FALL_RISK"), true, "DRAFT")));
        TrainingDtos.ArticleRequest request = new TrainingDtos.ArticleRequest(
                "  术后照护  ", "摘要", "/articles/1", List.of("术后"),
                List.of("service_1"), List.of("FALL_RISK"), true, "draft");

        TrainingDtos.ArticleResponse response = service.createArticle(ADMIN, request);

        assertEquals("DRAFT", response.status());
        ArgumentCaptor<TrainingDtos.ArticleRequest> normalized =
                ArgumentCaptor.forClass(TrainingDtos.ArticleRequest.class);
        verify(repository).insertArticle(anyString(), normalized.capture(),
                anyString(), anyString());
        assertEquals("术后照护", normalized.getValue().title());
        assertEquals("DRAFT", normalized.getValue().status());
        verify(repository).replaceTagsAndRules(
                anyString(), any(), any(), any(), any());
    }

    @Test
    void duplicateRuleValuesAreRejectedInsteadOfSilentlyChangingContract() {
        Phase49To50TrainingRepository repository = mock(Phase49To50TrainingRepository.class);
        Phase49To50TrainingService service = new Phase49To50TrainingService(
                repository, new ObjectMapper(), mock(RedisCacheService.class));
        when(repository.hasPermission("admin_1", "TRAINING_ARTICLE_MANAGE")).thenReturn(true);
        TrainingDtos.ArticleRequest request = new TrainingDtos.ArticleRequest(
                "标题", null, null, List.of("康复", "康复"),
                List.of(), List.of(), false, "DRAFT");

        assertThrows(BusinessRuleException.class, () -> service.createArticle(ADMIN, request));
    }

    @Test
    void recommendationCanOnlyBeReadByAssignedNurse() {
        Phase49To50TrainingRepository repository = mock(Phase49To50TrainingRepository.class);
        Phase49To50TrainingService service = new Phase49To50TrainingService(
                repository, new ObjectMapper(), mock(RedisCacheService.class));
        when(repository.findOrder("order_1")).thenReturn(Optional.of(
                new Phase49To50TrainingRepository.OrderContext(
                        "order_1", "elder_1", "service_1", "SERVING", "nurse_1")));

        assertEquals(List.of(), service.recommendedArticles(NURSE, "order_1"));
        assertThrows(ForbiddenException.class,
                () -> service.recommendedArticles(OTHER_NURSE, "order_1"));
        when(repository.hasPermission("admin_1", "TRAINING_ARTICLE_MANAGE")).thenReturn(true);
        assertEquals(List.of(), service.recommendedArticles(ADMIN, "order_1"));
        verify(repository, times(2)).findRecommended("order_1", "nurse_1");
    }
}
