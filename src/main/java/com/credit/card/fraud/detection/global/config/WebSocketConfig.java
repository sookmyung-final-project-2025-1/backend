package com.credit.card.fraud.detection.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트에게 메시지를 전달할 topic prefix
        config.enableSimpleBroker("/topic", "/queue");
        
        // 클라이언트가 서버로 메시지를 보낼 때 사용할 prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // 사용자별 개인 메시지를 위한 prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 연결을 위한 endpoint 등록
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 개발 환경용, 운영에서는 구체적인 도메인 설정
                .withSockJS(); // SockJS fallback 옵션 활성화
        
        // 추가적인 endpoint (SockJS 없이)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }
}