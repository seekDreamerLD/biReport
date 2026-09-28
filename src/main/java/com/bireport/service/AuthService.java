package com.bireport.service;

import com.bireport.auth.TokenService;
import com.bireport.common.BizException;
import com.bireport.dto.AuthDTOs.LoginReq;
import com.bireport.dto.AuthDTOs.LoginResp;
import com.bireport.dto.AuthDTOs.RegisterReq;
import com.bireport.dto.AuthDTOs.UserVO;
import com.bireport.entity.User;
import com.bireport.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public LoginResp login(LoginReq req) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }
        String token = tokenService.issue(user);
        return LoginResp.of(token, user);
    }

    /** 第一个注册的用户自动成为 admin，其余为 editor */
    public UserVO register(RegisterReq req) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            Long exists = userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
            if (exists != null && exists > 0) {
                throw new BizException("用户名已存在");
            }
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        user.setRole(count == null || count == 0 ? "admin" : "editor");
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public void logout(String token) {
        tokenService.logout(token);
    }
}
