package com.csu.carenest.careadmin.training;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 阶段49-50培训文章管理、推荐匹配和阅读记录服务。 */
@Service
public class Phase49To50TrainingService {

    private static final String PERMISSION = "TRAINING_ARTICLE_MANAGE";

    private final Phase49To50TrainingRepository repository;
    private final ObjectMapper objectMapper;

    public Phase49To50TrainingService(
            Phase49To50TrainingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TrainingDtos.ArticleResponse> articles(CurrentUser user) {
        requirePermission(user);
        return repository.findArticles();
    }

    @Transactional
    public TrainingDtos.ArticleResponse createArticle(
            CurrentUser user, TrainingDtos.ArticleRequest request) {
        requirePermission(user);
        TrainingEnums.ArticleStatus status = TrainingEnums.parse(
                TrainingEnums.ArticleStatus.class, request.status());
        if (status != TrainingEnums.ArticleStatus.DRAFT) {
            throw new BusinessRuleException();
        }
        NormalizedArticle normalized = normalize(request);
        String articleId = nextId("article");
        repository.insertArticle(articleId, normalized.request(), normalized.firstServiceId(), user.userId());
        repository.replaceTagsAndRules(
                articleId, normalized.tags(), normalized.riskTags(), normalized.serviceIds(), this::nextId);
        log(user, "CREATE_TRAINING_ARTICLE", articleId, null, Map.of("status", "DRAFT"));
        return new TrainingDtos.ArticleResponse(articleId, "DRAFT");
    }

    @Transactional
    public TrainingDtos.ArticleResponse updateArticle(
            CurrentUser user, String articleId, TrainingDtos.ArticleRequest request) {
        requirePermission(user);
        Phase49To50TrainingRepository.ArticleContext article = requireArticle(articleId);
        if ("PUBLISHED".equals(article.status())) {
            throw new ConflictException();
        }
        TrainingEnums.ArticleStatus status = TrainingEnums.parse(
                TrainingEnums.ArticleStatus.class, request.status());
        if (status == TrainingEnums.ArticleStatus.PUBLISHED) {
            throw new BusinessRuleException();
        }
        NormalizedArticle normalized = normalize(request);
        repository.updateArticle(articleId, normalized.request(), normalized.firstServiceId());
        repository.replaceTagsAndRules(
                articleId, normalized.tags(), normalized.riskTags(), normalized.serviceIds(), this::nextId);
        log(user, "UPDATE_TRAINING_ARTICLE", articleId,
                Map.of("status", article.status()), Map.of("status", status.name()));
        return new TrainingDtos.ArticleResponse(articleId, status.name());
    }

    @Transactional
    public TrainingDtos.ArticleResponse publishArticle(
            CurrentUser user, String articleId, TrainingDtos.ArticleRequest request) {
        requirePermission(user);
        Phase49To50TrainingRepository.ArticleContext article = requireArticle(articleId);
        TrainingEnums.ArticleStatus target = TrainingEnums.parse(
                TrainingEnums.ArticleStatus.class, request.status());
        if (target != TrainingEnums.ArticleStatus.PUBLISHED
                && target != TrainingEnums.ArticleStatus.OFFLINE) {
            throw new BusinessRuleException();
        }
        if (repository.updateArticleStatus(articleId, target.name()) == 0) {
            throw new ConflictException();
        }
        log(user, "PUBLISH_TRAINING_ARTICLE", articleId,
                Map.of("status", article.status()), Map.of("status", target.name()));
        return new TrainingDtos.ArticleResponse(articleId, target.name());
    }

    @Transactional(readOnly = true)
    public List<TrainingDtos.ReadResponse> recommendedArticles(CurrentUser user, String orderId) {
        Phase49To50TrainingRepository.OrderContext order = requireOrder(orderId);
        requireOrderAccess(user, order);
        return repository.findRecommended(orderId, order.nurseId());
    }

    @Transactional
    public TrainingDtos.ReadResponse markRead(
            CurrentUser user, String articleId, TrainingDtos.ReadRequest request) {
        Phase49To50TrainingRepository.OrderContext order = requireOrder(request.orderId());
        requireOrderAccess(user, order);
        Phase49To50TrainingRepository.ArticleContext article = requireArticle(articleId);
        if (!"PUBLISHED".equals(article.status())
                || !repository.isRecommended(order.orderId(), articleId, order.nurseId())) {
            throw new ForbiddenException();
        }
        repository.markRead(nextId("reading"), articleId, order.nurseId());
        log(user, "READ_TRAINING_ARTICLE", articleId, null,
                Map.of("orderId", order.orderId(),
                        "readDurationSeconds", request.readDurationSeconds(), "readStatus", "READ"));
        return new TrainingDtos.ReadResponse(articleId, "READ");
    }

    private NormalizedArticle normalize(TrainingDtos.ArticleRequest request) {
        List<String> tags = normalizeList(request.tags());
        List<String> riskTags = normalizeList(request.riskTags());
        List<String> serviceIds = normalizeList(request.serviceIds());
        if (tags.size() != request.tags().size()
                || riskTags.size() != request.riskTags().size()
                || serviceIds.size() != request.serviceIds().size()) {
            throw new BusinessRuleException();
        }
        // article_tag.tag_code 长度为 64，需要为内部类型前缀预留空间。
        if (tags.stream().anyMatch(tag -> tag.length() > 60)
                || riskTags.stream().anyMatch(tag -> tag.length() > 59)) {
            throw new BusinessRuleException();
        }
        for (String serviceId : serviceIds) {
            if (!repository.serviceExists(serviceId)) {
                throw new NotFoundException();
            }
        }
        TrainingDtos.ArticleRequest normalized = new TrainingDtos.ArticleRequest(
                request.title().trim(), trim(request.summary()), trim(request.contentUrl()),
                tags, serviceIds, riskTags, request.requiredRead(),
                request.status().trim().toUpperCase(java.util.Locale.ROOT));
        return new NormalizedArticle(
                normalized, tags, riskTags, serviceIds,
                serviceIds.isEmpty() ? null : serviceIds.get(0));
    }

    private List<String> normalizeList(List<String> values) {
        return values.stream().map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private Phase49To50TrainingRepository.ArticleContext requireArticle(String articleId) {
        return repository.findArticle(articleId).orElseThrow(NotFoundException::new);
    }

    private Phase49To50TrainingRepository.OrderContext requireOrder(String orderId) {
        return repository.findOrder(orderId).orElseThrow(NotFoundException::new);
    }

    private void requireOrderAccess(
            CurrentUser user, Phase49To50TrainingRepository.OrderContext order) {
        if (user.hasRole(RoleCode.NURSE) && user.userId().equals(order.nurseId())) {
            return;
        }
        if (user.hasRole(RoleCode.ADMIN) && repository.hasPermission(user.userId(), PERMISSION)) {
            return;
        }
        throw new ForbiddenException();
    }

    private void requirePermission(CurrentUser user) {
        boolean role = user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!role || !repository.hasPermission(user.userId(), PERMISSION)) {
            throw new ForbiddenException();
        }
    }

    private void log(CurrentUser user, String operation, String articleId, Object before, Object after) {
        repository.insertOperationLog(
                nextId("op"), user.userId(), user.primaryRole(), operation,
                "TRAINING_ARTICLE", articleId, json(before), json(after), nextId("trace"));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize training operation log", exception);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nextId(String prefix) {
        String value = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, Math.min(32, value.length()));
    }

    private record NormalizedArticle(
            TrainingDtos.ArticleRequest request,
            List<String> tags,
            List<String> riskTags,
            List<String> serviceIds,
            String firstServiceId) {
    }
}
