package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.BizException;
import com.bireport.common.Result;
import com.bireport.entity.User;
import com.bireport.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.bireport.auth.TokenStoreService tokenStoreService;

    @GetMapping("/list")
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery().orderByAsc(User::getId));
        users.forEach(u -> u.setPasswordHash(null));
        return Result.ok(users);
    }

    @PostMapping("/create")
    public Result<User> create(@RequestBody CreateUserReq req) {
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        user.setRole(req.getRole() == null ? "viewer" : req.getRole());
        user.setStatus(1);
        userMapper.insert(user);
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateUserReq req) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getRole() != null) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            tokenStoreService.removeAllOfUser(id);
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        User current = TokenService.currentUser();
        if (current.getId().equals(id)) {
            throw new BizException("不能删除自己");
        }
        userMapper.deleteById(id);
        tokenStoreService.removeAllOfUser(id);
        return Result.ok();
    }

    @Data
    public static class CreateUserReq {
        private String username;
        private String password;
        private String nickname;
        private String role;
    }

    @Data
    public static class UpdateUserReq {
        private String nickname;
        private String role;
        private Integer status;
        private String password;
    }
}
