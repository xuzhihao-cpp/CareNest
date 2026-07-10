package com.csu.carenest.user.common;

import java.util.UUID;

final class TraceIds {

    private TraceIds() {
    }

    static String next() {
        return "trace-" + UUID.randomUUID();
    }
}
