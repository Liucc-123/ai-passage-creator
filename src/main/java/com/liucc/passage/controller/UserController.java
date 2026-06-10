package com.liucc.passage.controller;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSONObject;
import com.liucc.passage.common.BaseResponse;
import com.liucc.passage.model.vo.UserVO;
import com.liucc.passage.model.dto.UserLoginRequest;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.liucc.passage.constants.UserConstant.USER_LOGIN_STATE;

/**
 * 用户接口
 */
@Tag(name = "用户接口", description = "用户注册、登录、管理等接口")
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册账号，自动登录并返回用户信息")
    public BaseResponse<UserVO> register(
            @Parameter(description = "注册信息", required = true)
            @RequestBody UserRegisterRequest registerRequest,
            HttpServletRequest request) {
        log.info("用户注册接口开始:request: {}", JSONObject.toJSONString(registerRequest));
        IUserService userService = SpringUtil.getBean(IUserService.class);
        UserVO userVO = userService.register(registerRequest);
        // 注册成功，保存信息到 session
        request.getSession().setAttribute(USER_LOGIN_STATE, userVO);
        log.info("用户注册接口结束:userVO: {}", JSONObject.toJSONString(userVO));
        return BaseResponse.success(userVO);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口，返回用户信息和登录状态")
    public BaseResponse<UserVO> login(
            @Parameter(description = "登录账密", required = true)
            @RequestBody UserLoginRequest loginRequest,
            HttpServletRequest request){
        log.info("用户登录接口开始:loginRequest: {}", JSONObject.toJSONString(loginRequest));
        IUserService userService = SpringUtil.getBean(IUserService.class);
        UserVO loginUser = userService.login(loginRequest);
         // 保存登录信息到 session
        request.getSession().setAttribute(USER_LOGIN_STATE, loginUser);
        log.info("用户登录接口结束:userVO: {}", JSONObject.toJSONString(loginUser));
        return BaseResponse.success(loginUser);
    }
}
