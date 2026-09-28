package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.BizException;
import com.bireport.common.Result;
import com.bireport.dto.AuthDTOs.LoginReq;
import com.bireport.dto.AuthDTOs.LoginResp;
import com.bireport.dto.AuthDTOs.RegisterReq;
import com.bireport.dto.AuthDTOs.UserVO;
import com.bireport.entity.User;
import com.bireport.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = TokenService.currentUser();
        return Result.ok(UserVO.from(user));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(value = "currentToken", required = false) String token,
                               jakarta.servlet.http.HttpServletRequest request) {
        String t = token;
        if (t == null) {
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                t = h.substring(7);
            }
        }
        if (t != null) {
            authService.logout(t);
        }
        return Result.ok();
    }
}
