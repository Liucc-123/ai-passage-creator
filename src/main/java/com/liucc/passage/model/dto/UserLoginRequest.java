package com.liucc.passage.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Schema(title = "用户登录请求对象", description = "用户登录时提交的请求体")
@Data
@Builder
public class UserLoginRequest {

    @Schema(description = "登录账号", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "账号不能为空")
    private String userAccount;

    @Schema(description = "用户密码（6-20 位，需包含字母和数字）", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    private String userPassword;
}
