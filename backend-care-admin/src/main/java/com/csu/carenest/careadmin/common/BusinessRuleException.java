package com.csu.carenest.careadmin.common;

/**
 * 422 业务规则不满足，例如家属未授权下单。
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException() {
        super(422, "business rule not satisfied");
    }
}
