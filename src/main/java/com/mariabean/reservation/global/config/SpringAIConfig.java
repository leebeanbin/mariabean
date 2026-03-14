package com.mariabean.reservation.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 1.0.0 빈 설정.
 * ChatClient.Builder는 spring-ai-ollama-spring-boot-starter가 자동 제공하며,
 * 여기서 ChatClient 빈을 생성해 @RequiredArgsConstructor 주입을 지원한다.
 */
@Configuration
public class SpringAIConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
