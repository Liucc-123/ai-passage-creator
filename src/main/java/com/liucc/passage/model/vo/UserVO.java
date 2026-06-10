package com.liucc.passage.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户 VO 对象，去除敏感信息
 */
@Schema(title = "用户 VO", description = "用户信息响应对象，不包含密码等敏感字段")
@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "登录账号", example = "zhangsan")
    private String userAccount;

    @Schema(description = "展示用户名", example = "张三")
    private String userName;

    @Schema(description = "头像 URL", example = "https://example.com/avatar.jpg")
    private String userAvatar;

    @Schema(description = "简介", example = "个人简介")
    private String userProfile;

    @Schema(description = "角色", example = "user", allowableValues = {"user", "admin"})
    private String userRole;

    @Schema(description = "剩余配额", example = "5")
    private Integer quota;
}
