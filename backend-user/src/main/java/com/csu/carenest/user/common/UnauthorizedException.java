package com.csu.carenest.user.common;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(401, "未登录");
    }
}
