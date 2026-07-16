package com.csu.carenest.careadmin.metric;

import com.csu.carenest.careadmin.common.BusinessRuleException;

import java.util.Locale;

/** 阶段34-40使用的固定状态值，必须与成员1数据字典保持一致。 */
final class MetricEnums {

    private MetricEnums() {
    }

    enum MetricType {
        PRE_SERVICE, SERVICE_PROCESS, POST_SERVICE
    }

    enum EvidenceType {
        NONE, PHOTO, FILE, TEXT, VITAL_SIGN
    }

    enum MetricStatus {
        PENDING, SUBMITTED, PASS, MISSING, PENDING_PROOF, EXEMPT_APPROVED, EXEMPT_REJECTED
    }

    enum EvidenceAuditStatus {
        PENDING, APPROVED, REJECTED, NEED_MORE
    }

    enum ProofStatus {
        PENDING, APPROVED, REJECTED
    }

    enum ProofReasonType {
        FORGOT, NOT_REQUIRED, ELDER_REFUSED, OBJECTIVE_IMPOSSIBLE, OTHER
    }

    enum ScoreDecision {
        NO_DEDUCTION, DEDUCT
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException();
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException();
        }
    }
}
