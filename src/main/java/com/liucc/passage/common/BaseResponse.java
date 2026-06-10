package com.liucc.passage.common;

import com.liucc.passage.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应类
 *
 * @param <T> 数据类型
 */
@Schema(title = "统一响应", description = "所有接口返回的统一响应格式")
@Data
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码", example = "0")
    private int code;

    @Schema(description = "数据")
    private T data;

    @Schema(description = "提示信息")
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

    /**
     * 快速响应成功
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), data);
    }
}
