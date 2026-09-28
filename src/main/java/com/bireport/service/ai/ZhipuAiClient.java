package com.bireport.service.ai;

import com.bireport.common.BizException;
import com.bireport.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 智谱开放平台 GLM 对话客户端（v4 OpenAI 兼容接口）。
 */
@Slf4j
@Service
public class ZhipuAiClient {

    public record Msg(String role, String content) {

        public static Msg system(String c) {
            return new Msg("system", c);
        }

        public static Msg user(String c) {
            return new Msg("user", c);
        }

        public static Msg assistant(String c) {
            return new Msg("assistant", c);
        }
    }

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ZhipuAiClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(90_000);
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 单轮对话，返回 assistant 内容 */
    public String chat(List<Msg> messages) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        if (props.isThinkingDisabled()) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        try {
            String resp = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BizException("AI 未返回内容，请稍后重试");
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("智谱 AI 调用失败: {}", root.getMessage());
            throw new BizException("AI 服务调用失败: " + root.getMessage());
        }
    }
}
