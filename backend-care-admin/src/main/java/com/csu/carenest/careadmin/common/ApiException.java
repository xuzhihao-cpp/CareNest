package com.csu.carenest.careadmin.common;

/**
 * 业务异常基类，code 必须使用全局接口契约中的固定错误码。
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
