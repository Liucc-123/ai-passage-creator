package com.liucc.passage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(20000, "请求成功"),

    REQUEST_PARAMS_MUST_NOT_NULL(40000, "请求参数不能为空"),
    PARAMS_ERROR(40001, "参数错误"),

    NOT_AUTH_ERROR(40101, "未授权错误"),

    ;


    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
