package com.bireport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bi.jwt")
public class JwtProperties {

    private String secret;
    private int expireHours = 24;
}
