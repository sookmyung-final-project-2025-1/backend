package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "대시보드", description = "대시보드 통계 및 KPI API")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    @Operation(summary = "대시보드 KPI 조회", description = "대시보드의 핵심 성과 지표를 조회합니다")
    public ResponseEntity<Map<String, Object>> getDashboardKPIs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        Map<String, Object> kpis = dashboardService.getDashboardKPIs(startTime, endTime);
        return ResponseEntity.ok(kpis);
    }

    @GetMapping("/stats/hourly")
    @Operation(summary = "시간별 통계 조회", description = "시간별 거래 및 사기 탐지 통계를 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getHourlyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        List<Map<String, Object>> stats = dashboardService.getHourlyTransactionStats(startTime, endTime);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/daily")
    @Operation(summary = "일별 통계 조회", description = "일별 거래 및 사기 통계를 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getDailyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(30);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        List<Map<String, Object>> stats = dashboardService.getDailyTransactionStats(startTime, endTime);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/realtime")
    @Operation(summary = "실시간 메트릭 조회", description = "실시간 대시보드 메트릭과 최근 활동을 조회합니다")
    public ResponseEntity<Map<String, Object>> getRealTimeMetrics() {
        Map<String, Object> metrics = dashboardService.getRealTimeMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/high-risk-transactions")
    @Operation(summary = "고위험 거래 조회", description = "고위험 거래 목록을 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getHighRiskTransactions(
            @RequestParam(defaultValue = "10") Integer limit) {
        
        List<Map<String, Object>> transactions = dashboardService.getTopRiskTransactions(limit);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/system-health")
    @Operation(summary = "시스템 상태 조회", description = "시스템 상태 지표와 성능 메트릭을 조회합니다")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = dashboardService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/alerts")
    @Operation(summary = "최근 알림 조회", description = "최근 시스템 알림과 알림을 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getRecentAlerts(
            @RequestParam(defaultValue = "50") Integer limit) {
        
        // 실제 구현에서는 알림 이력을 저장하고 조회하는 기능이 필요
        // 현재는 시뮬레이션된 알림 데이터를 반환
        
        List<Map<String, Object>> alerts = List.of(
            Map.of(
                "type", "HIGH_RISK_TRANSACTION",
                "message", "High fraud score detected: 0.95",
                "transactionId", 12345L,
                "timestamp", LocalDateTime.now().minusMinutes(5),
                "severity", "HIGH"
            ),
            Map.of(
                "type", "SYSTEM_PERFORMANCE",
                "message", "Processing latency increased to 2.5s",
                "timestamp", LocalDateTime.now().minusMinutes(15),
                "severity", "MEDIUM"
            ),
            Map.of(
                "type", "MODEL_UPDATE",
                "message", "Model weights updated by admin",
                "timestamp", LocalDateTime.now().minusHours(2),
                "severity", "INFO"
            )
        );
        
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/fraud-trends")
    @Operation(summary = "사기 동향 조회", description = "시간에 따른 사기 탐지 동향을 조회합니다")
    public ResponseEntity<Map<String, Object>> getFraudTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "daily") String interval) {
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(30);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        List<Map<String, Object>> trends = interval.equals("hourly") 
            ? dashboardService.getHourlyTransactionStats(startTime, endTime)
            : dashboardService.getDailyTransactionStats(startTime, endTime);
        
        Map<String, Object> response = Map.of(
            "interval", interval,
            "startTime", startTime,
            "endTime", endTime,
            "trends", trends
        );
        
        return ResponseEntity.ok(response);
    }
}