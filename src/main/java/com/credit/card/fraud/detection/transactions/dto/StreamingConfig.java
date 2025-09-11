package com.credit.card.fraud.detection.transactions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamingConfig {
    
    @Builder.Default
    private StreamingMode mode = StreamingMode.REALTIME;
    
    @Builder.Default
    private Double speedMultiplier = 1.0; // 1x = 1초당 1시간 데이터, 10x = 1초당 10시간 데이터
    
    private LocalDateTime startTime; // 타임머신 모드에서 시작 시간
    private LocalDateTime endTime;   // 재생 종료 시간 (선택사항)
    
    @Builder.Default
    private Boolean paused = false;
    
    @Builder.Default
    private Integer batchSize = 100; // 한 번에 처리할 트랜잭션 수
    
    @Builder.Default
    private Long intervalMs = 1000L; // 배치 처리 간격 (밀리초)
    
    public enum StreamingMode {
        REALTIME,    // 현재 시점 기준으로 계속 진행
        TIMEMACHINE  // 과거 특정 시점부터 재생
    }
    
    public Long getAdjustedIntervalMs() {
        return (long) (intervalMs / speedMultiplier);
    }
    
    public LocalDateTime getNextVirtualTime(LocalDateTime currentVirtualTime) {
        // speedMultiplier 적용: 1초 = speedMultiplier 시간
        long hoursToAdd = Math.round(speedMultiplier);
        return currentVirtualTime.plusHours(hoursToAdd);
    }
}