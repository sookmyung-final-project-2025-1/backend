package com.credit.card.fraud.detection.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 개발 환경에서는 모든 origin 허용, 운영에서는 구체적인 도메인 설정 필요
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        
        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // 허용할 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"
        ));
        
        // 응답에 포함할 헤더
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "X-Total-Count"
        ));
        
        // 자격 증명 허용
        configuration.setAllowCredentials(true);
        
        // 캐시 시간 설정 (초)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}