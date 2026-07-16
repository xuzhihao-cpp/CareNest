package com.csu.carenest.careadmin.training;

import com.csu.carenest.careadmin.common.BusinessRuleException;

import java.util.Locale;

final class TrainingEnums {

    private TrainingEnums() {
    }

    enum ArticleStatus { DRAFT, PUBLISHED, OFFLINE }
    enum ReadingStatus { UNREAD, READ, CONFIRMED }

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
