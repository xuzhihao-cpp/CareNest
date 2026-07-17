package com.csu.carenest.careadmin.score;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 阶段47-48护理评分重算和展示服务。 */
@Service
public class Phase47To48ScoreService {

    private static final String SCORE_PERMISSION = "NURSE_APPEAL_REVIEW";
    private static final BigDecimal INITIAL_SCORE = new BigDecimal("100.00");

    private final Phase47To48ScoreRepository repository;
    private final ObjectMapper objectMapper;

    public Phase47To48ScoreService(
            Phase47To48ScoreRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScoreDtos.ScoreResponse recalculate(
            CurrentUser user, String nurseId, ScoreDtos.RecalculateRequest request) {
        requireAdminPermission(user);
        if (!nurseId.equals(request.nurseId())) {
            throw new BusinessRuleException();
        }
        return recalculateInternal(user, nurseId, "RECALCULATE", request.sourceEventId());
    }

    @Transactional
    public ScoreDtos.ScoreResponse recalculateAfterAppeal(
            CurrentUser user, String nurseId, String appealId) {
        return recalculateInternal(user, nurseId, "APPEAL", appealId);
    }

    @Transactional(readOnly = true)
    public ScoreDtos.ScoreResponse score(CurrentUser user, String nurseId, int logLimit) {
        requireReadAccess(user, nurseId);
        if (!repository.nurseExists(nurseId)) {
            throw new NotFoundException();
        }
        BigDecimal score = repository.currentScore(nurseId).orElse(INITIAL_SCORE);
        return new ScoreDtos.ScoreResponse(
                nurseId, score, level(score), repository.findLogs(nurseId, 0, logLimit));
    }

    @Transactional(readOnly = true)
    public ScoreDtos.MyScoreResponse myScore(CurrentUser user, int page, int size) {
        String nurseId = user.userId();
        if (!user.hasRole(RoleCode.NURSE)) {
            throw new ForbiddenException();
        }
        if (!repository.nurseExists(nurseId)) {
            throw new NotFoundException();
        }
        BigDecimal score = repository.currentScore(nurseId).orElse(INITIAL_SCORE);
        return new ScoreDtos.MyScoreResponse(
                score, level(score), repository.monthDelta(
                        nurseId, java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay()),
                repository.findLogs(nurseId, (page - 1) * size, size));
    }

    private ScoreDtos.ScoreResponse recalculateInternal(
            CurrentUser user, String nurseId, String sourceType, String sourceId) {
        if (!repository.nurseExists(nurseId)) {
            throw new NotFoundException();
        }
        repository.lockNurse(nurseId);
        // 首次重算也必须从护理员初始分 100 分起算，避免生成 0 分到当前分的错误增分流水。
        BigDecimal before = repository.currentScore(nurseId).orElse(INITIAL_SCORE);
        Phase47To48ScoreRepository.ScoreFacts facts = repository.calculateFacts(nurseId);
        repository.saveScore(nurseId, facts, user.userId());
        if (before.compareTo(facts.totalScore()) != 0) {
            repository.insertChangeLog(
                    nextId("score_log"), nurseId, sourceType, sourceId,
                    before, facts.totalScore(), reason(facts), user.userId());
        }
        repository.insertOperationLog(
                nextId("op"), user.userId(), user.primaryRole(), "RECALCULATE_NURSE_SCORE",
                "NURSE_SCORE", nurseId, writeJson(Map.of("totalScore", before)),
                writeJson(Map.of("totalScore", facts.totalScore())), nextId("trace"));
        return new ScoreDtos.ScoreResponse(
                nurseId, facts.totalScore(), level(facts.totalScore()),
                repository.findLogs(nurseId, 0, 20));
    }

    private void requireReadAccess(CurrentUser user, String nurseId) {
        if (user.hasRole(RoleCode.NURSE) && user.userId().equals(nurseId)) {
            return;
        }
        requireAdminPermission(user);
    }

    private void requireAdminPermission(CurrentUser user) {
        boolean role = user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!role || !repository.hasPermission(user.userId(), SCORE_PERMISSION)) {
            throw new ForbiddenException();
        }
    }

    private String level(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "EXCELLENT";
        }
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "GOOD";
        }
        return "NEEDS_IMPROVEMENT";
    }

    private String reason(Phase47To48ScoreRepository.ScoreFacts facts) {
        return "按当前指标、投诉和申诉事实重算：指标扣分=" + facts.metricDeduction()
                + "，投诉数=" + facts.complaintCount()
                + "，申诉调整=" + facts.appealAdjustment();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize score operation log", exception);
        }
    }

    private String nextId(String prefix) {
        String value = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, Math.min(32, value.length()));
    }
}
