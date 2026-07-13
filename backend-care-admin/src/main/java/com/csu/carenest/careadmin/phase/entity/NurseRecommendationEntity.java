package com.csu.carenest.careadmin.phase.entity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 可解释护理推荐结果实体。
 */
public record NurseRecommendationEntity(
        String nurseId,
        String nurseName,
        BigDecimal score,
        List<String> matchedSkills,
        String recommendReason,
        boolean available) {
}
