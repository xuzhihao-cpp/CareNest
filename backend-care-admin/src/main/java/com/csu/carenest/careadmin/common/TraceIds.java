package com.csu.carenest.careadmin.common;

import java.util.UUID;

/**
 * 为每次接口响应生成 traceId，便于联调定位请求。
 */
public final class TraceIds {

    private TraceIds() {
    }

    public static String next() {
        return "care-admin-" + UUID.randomUUID().toString().replace("-", "");
    }
}
