package com.bireport.auth;

import com.bireport.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = props.getExpireHours() * 3600_000L;
    }

    public record TokenInfo(String token, String jti, long userId) {
    }

    public TokenInfo generate(long userId, String username, String role) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + expireMillis))
                .signWith(key)
                .compact();
        return new TokenInfo(token, jti, userId);
    }

    /** 校验并解析，失败抛 BizException */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new com.bireport.common.BizException(com.bireport.common.Result.CODE_UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    public long expireMillis() {
        return expireMillis;
    }
}
