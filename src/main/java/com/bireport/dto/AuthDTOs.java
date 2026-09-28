package com.bireport.dto;

import com.bireport.entity.User;
import lombok.Data;

public class AuthDTOs {

    @Data
    public static class LoginReq {
        @jakarta.validation.constraints.NotBlank(message = "不能为空")
        private String username;
        @jakarta.validation.constraints.NotBlank(message = "不能为空")
        private String password;
    }

    @Data
    public static class RegisterReq {
        @jakarta.validation.constraints.NotBlank(message = "不能为空")
        @jakarta.validation.constraints.Size(min = 2, max = 32, message = "长度需在 2-32 之间")
        private String username;
        @jakarta.validation.constraints.NotBlank(message = "不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 64, message = "长度需在 6-64 之间")
        private String password;
        private String nickname;
    }

    @Data
    public static class LoginResp {
        private String token;
        private UserVO user;

        public static LoginResp of(String token, User user) {
            LoginResp r = new LoginResp();
            r.token = token;
            r.user = UserVO.from(user);
            return r;
        }
    }

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String nickname;
        private String role;

        public static UserVO from(User u) {
            UserVO vo = new UserVO();
            vo.id = u.getId();
            vo.username = u.getUsername();
            vo.nickname = u.getNickname();
            vo.role = u.getRole();
            return vo;
        }
    }
}
