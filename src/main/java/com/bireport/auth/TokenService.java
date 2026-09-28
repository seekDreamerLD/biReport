package com.bireport.auth;

import com.bireport.common.BizException;
import com.bireport.common.Result;
import com.bireport.entity.User;
import com.bireport.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 登录态签发与校验（JWT + TokenStore）。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final TokenStoreService tokenStore;
    private final UserMapper userMapper;

    public String issue(User user) {
        JwtUtil.TokenInfo info = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        tokenStore.save(info.jti(), user.getId(), Duration.ofMillis(jwtUtil.expireMillis()));
        return info.token();
    }

    public Authentication validate(String token) {
        Claims claims = jwtUtil.parse(token);
        String jti = claims.getId();
        if (!tokenStore.exists(jti)) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "登录已失效，请重新登录");
        }
        long userId = Long.parseLong(claims.getSubject());
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, userId));
        if (user == null || user.getStatus() == 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "账号不存在或已被禁用");
        }
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        var auth = new UsernamePasswordAuthenticationToken(user, token, authorities);
        return auth;
    }

    public void logout(String token) {
        Claims claims = jwtUtil.parse(token);
        tokenStore.remove(claims.getId());
    }

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        throw new BizException(Result.CODE_UNAUTHORIZED, "未登录");
    }
}
