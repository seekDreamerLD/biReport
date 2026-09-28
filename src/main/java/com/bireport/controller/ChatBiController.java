package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.dto.ChatBiDTOs.AskReq;
import com.bireport.dto.ChatBiDTOs.ChatAnswer;
import com.bireport.service.ChatBiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbi")
@RequiredArgsConstructor
public class ChatBiController {

    private final ChatBiService chatBiService;

    @PostMapping("/ask")
    public Result<ChatAnswer> ask(@RequestBody AskReq req) {
        return Result.ok(chatBiService.ask(TokenService.currentUser(), req));
    }
}
