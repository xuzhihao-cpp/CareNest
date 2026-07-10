package com.csu.carenest.careadmin.common;

/**
 * 401 未登录或登录态无效。
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(401, "unauthorized");
    }
}
