package com.liucc.passage.utils;

import com.liucc.passage.common.BaseResponse;
import com.liucc.passage.exception.ErrorCode;

/**
 * 快速构建响应结果的工具类
 *
 * @author shuiguan
 * @date 2026 年 6 月 10 日
 */
public class ResultUtil {

    public static <T> BaseResponse<T> success(T data){
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), data);
    }

    public static BaseResponse<?> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

    public static BaseResponse<?> error(ErrorCode errorCode,String message){
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
