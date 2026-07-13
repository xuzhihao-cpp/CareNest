package com.csu.carenest.careadmin.phase.enums;

import com.csu.carenest.careadmin.common.BusinessRuleException;

import java.util.Locale;

/**
 * 培训资格复用审核字典中的状态，避免产生同义状态。
 */
public enum TrainingStatus {
    PENDING,
    APPROVED,
    REJECTED,
    NEED_MORE;

    public static TrainingStatus parse(String value) {
        try {
            return TrainingStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessRuleException();
        }
    }
}
