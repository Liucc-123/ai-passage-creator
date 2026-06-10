package com.liucc.passage.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Schema(title = "错误码枚举", description = "系统统一错误码定义")
@Getter
public enum ErrorCode {

    @Schema(description = "请求成功")
    SUCCESS(20000, "请求成功"),

    @Schema(description = "请求参数不能为空")
    REQUEST_PARAMS_MUST_NOT_NULL(40000, "请求参数不能为空"),

    @Schema(description = "参数错误")
    PARAMS_ERROR(40001, "参数错误"),

    @Schema(description = "未授权错误")
    NOT_AUTH_ERROR(40101, "未授权错误"),

    @Schema(description = "系统异常")
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
