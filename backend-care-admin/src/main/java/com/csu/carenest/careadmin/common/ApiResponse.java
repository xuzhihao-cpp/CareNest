package com.csu.carenest.careadmin.common;

/**
 * 全局统一响应结构：{code,message,data,traceId}。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, TraceIds.next());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceIds.next());
    }
}
