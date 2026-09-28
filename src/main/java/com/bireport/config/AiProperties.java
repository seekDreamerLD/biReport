package com.bireport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bi.ai")
public class AiProperties {

    private String apiKey;
    /** 默认 glm-4.5-flash（免费），可换 glm-4.5 / glm-4-plus 等提升效果 */
    private String model = "glm-4.5-flash";
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    /** 是否关闭深度思考（JSON 生成任务关闭更快更省） */
    private boolean thinkingDisabled = true;
    private int maxTokens = 2048;
    private double temperature = 0.2;
}
