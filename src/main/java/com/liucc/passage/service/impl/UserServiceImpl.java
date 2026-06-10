package com.liucc.passage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.liucc.passage.enums.UserEnum;
import com.liucc.passage.exception.BusinessException;
import com.liucc.passage.exception.ErrorCode;
import com.liucc.passage.mapper.UserMapper;
import com.liucc.passage.mapstruct.MUserMapper;
import com.liucc.passage.model.dto.UserLoginRequest;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;
import com.liucc.passage.service.IUserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public UserVO register(UserRegisterRequest request) {
        // 1.参数校验
        checkParams(request);
        // 是否已经注册？
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq(User::getUserAccount, request.getUserAccount());
        long count = this.getMapper().selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已注册");
        }
        // 密码加密存储
        String password = DigestUtil.bcrypt(request.getUserPassword());
        // 注册用户
        User user = new User();
        user.setUserAccount(request.getUserAccount());
        user.setUserPassword(password);
        // 用户名默认是账户名
        user.setUserName(request.getUserName());
        if (!StringUtils.hasText(request.getUserName())) {
            user.setUserName(request.getUserAccount());
        }
        user.setUserRole(UserEnum.USER.getCode());
        this.save(user);
       return MUserMapper.INSTANCE.entity2VO(user);
    }

    @Override
    public UserVO login(UserLoginRequest loginRequest) {
        // 1.参数校验
        if (BeanUtil.isEmpty(loginRequest)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMS_MUST_NOT_NULL);
        }
        String account = loginRequest.getUserAccount();
        String password = loginRequest.getUserPassword();
        // 校验账号和密码
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }
        // 2.校验用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq(User::getUserAccount, account);
        User user = this.getOne(queryWrapper);
        if (BeanUtil.isEmpty(user)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        if (!DigestUtil.bcryptCheck(password, user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        return MUserMapper.INSTANCE.entity2VO(user);
    }

    /**
     * 参数合法性校验
     *
     * @param request
     */
    private static void checkParams(UserRegisterRequest request) {
        if (BeanUtil.isEmpty(request)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMS_MUST_NOT_NULL);
        }
        String account = request.getUserAccount();
        String password = request.getUserPassword();
        String confirmPassword = request.getConfirmPassword();
        // 校验账号格式
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能少于 4 位");
        }

        // 校验密码格式
        if (password.length() < 6 || password.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度在 6-20 位之间");
        }
        if (!Pattern.matches(".*[A-Za-z].*[0-9].*|.*[0-9].*[A-Za-z].*", password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码需包含字母和数字");
        }
        // 两次密码是否一致
        if (!StringUtils.pathEquals(password, confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }

    }
}
