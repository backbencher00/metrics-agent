package org.metricsAgent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient metricsWebClient(
            @Value("${metrics.backend.base-url}") String baseUrl,
            @Value("${metrics.backend.api-key:}") String apiKey
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                // Optional: bearer token / API key header if your backend needs it
                .defaultHeader("X-Api-Key", apiKey)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }
}