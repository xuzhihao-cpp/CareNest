package com.csu.carenest.careadmin.support;

import com.csu.carenest.careadmin.common.BusinessRuleException;

import java.util.Locale;

/** 成员1数据字典中阶段44-46的固定枚举。 */
final class SupportEnums {

    private SupportEnums() {
    }

    enum FollowUpType { PHONE, ONLINE, HOME, AI, CUSTOMER_SERVICE }
    enum ComplaintStatus { PENDING, PROCESSING, RESOLVED, REJECTED }
    enum AppealStatus { PENDING, APPROVED, REJECTED }
    enum AppealTarget { COMPLAINT, METRIC, EXCEPTION_PROOF }

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
