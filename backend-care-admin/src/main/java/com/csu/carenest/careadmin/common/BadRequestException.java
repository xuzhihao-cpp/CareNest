package com.csu.carenest.careadmin.common;

/**
 * 400 参数错误。
 */
public class BadRequestException extends ApiException {

    public BadRequestException() {
        super(400, "bad request");
    }
}
