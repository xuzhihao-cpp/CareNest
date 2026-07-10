package com.csu.carenest.careadmin.common;

/**
 * 409 状态冲突，例如订单状态不允许继续流转。
 */
public class ConflictException extends ApiException {

    public ConflictException() {
        super(409, "state conflict");
    }
}
