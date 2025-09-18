package com.credit.card.fraud.detection.global.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "model.service")
public class ModelServiceProperties {

    /**
     * 모델 서비스 사용 여부
     */
    private boolean enabled = true;

    /**
     * 모델 서비스 URL
     */
    private String url = "http://model:8000";

    /**
     * 모델 서비스 타임아웃 (밀리초)
     */
    private long timeout = 30000;

    /**
     * 재시도 설정
     */
    private Retry retry = new Retry();

    /**
     * GitHub 모델 저장소 설정
     */
    private Github github = new Github();

    @Data
    public static class Retry {
        /**
         * 최대 재시도 횟수
         */
        private int maxAttempts = 3;

        /**
         * 재시도 지연 시간 (밀리초)
         */
        private long delay = 1000;
    }

    @Data
    public static class Github {
        /**
         * Data 레포지토리 (owner/repo 형식)
         */
        private String dataRepo = "sookmyung-final-project-2025-1/Data";

        /**
         * 기본 모델 버전
         */
        private String defaultVersion = "v1.0.0";
    }
}