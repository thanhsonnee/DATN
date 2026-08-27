package com.gym.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Cấu hình riêng của ứng dụng, đọc từ khối {@code app} trong application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Storage storage) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Storage(String endpoint, String accessKey, String secretKey,
                          String bucket, Duration presignedUrlTtl) {}
}
