package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.service.TransactionStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/streaming")
@RequiredArgsConstructor
@Tag(name = "스트리밍 제어", description = "거래 스트리밍 제어 API")
public class StreamingController {

    private final TransactionStreamingService streamingService;

    @PostMapping("/start/realtime")
    @Operation(summary = "실시간 스트리밍 시작", description = "실시간 모드로 거래 스트리밍을 시작합니다")
    @ApiResponse(responseCode = "200", description = "실시간 스트리밍 시작 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Real-time streaming started\", \"mode\": \"REALTIME\"}")))
    public ResponseEntity<Map<String, String>> startRealTimeStreaming() {
        streamingService.startRealTimeStreaming();
        return ResponseEntity.ok(Map.of("status", "Real-time streaming started", "mode", "REALTIME"));
    }

    @PostMapping("/start/timemachine")
    @Operation(summary = "타임머신 스트리밍 시작", description = "특정 시점부터 거래 스트리밍을 시작합니다")
    @ApiResponse(responseCode = "200", description = "타임머신 스트리밍 시작 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Time machine streaming started\", \"mode\": \"TIMEMACHINE\", \"startTime\": \"2017-01-01T00:00:00\", \"speed\": \"1.0x\"}")))
    public ResponseEntity<Map<String, String>> startTimeMachineStreaming(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(defaultValue = "1.0") Double speedMultiplier) {
        
        streamingService.startTimeMachineStreaming(startTime, speedMultiplier);
        
        return ResponseEntity.ok(Map.of(
            "status", "Time machine streaming started", 
            "mode", "TIMEMACHINE",
            "startTime", startTime != null ? startTime.toString() : "2017-01-01T00:00:00",
            "speed", speedMultiplier + "x"
        ));
    }

    @PostMapping("/pause")
    @Operation(summary = "스트리밍 일시정지", description = "현재 스트리밍 세션을 일시정지합니다")
    @ApiResponse(responseCode = "200", description = "스트리밍 일시정지 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Streaming paused\"}")))
    public ResponseEntity<Map<String, String>> pauseStreaming() {
        streamingService.pauseStreaming();
        return ResponseEntity.ok(Map.of("status", "Streaming paused"));
    }

    @PostMapping("/resume")
    @Operation(summary = "스트리밍 재개", description = "일시정지된 스트리밍 세션을 재개합니다")
    @ApiResponse(responseCode = "200", description = "스트리밍 재개 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Streaming resumed\"}")))
    public ResponseEntity<Map<String, String>> resumeStreaming() {
        streamingService.resumeStreaming();
        return ResponseEntity.ok(Map.of("status", "Streaming resumed"));
    }

    @PostMapping("/stop")
    @Operation(summary = "스트리밍 중지", description = "현재 스트리밍 세션을 중지합니다")
    @ApiResponse(responseCode = "200", description = "스트리밍 중지 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Streaming stopped\"}")))
    public ResponseEntity<Map<String, String>> stopStreaming() {
        streamingService.stopStreaming();
        return ResponseEntity.ok(Map.of("status", "Streaming stopped"));
    }

    @PutMapping("/speed")
    @Operation(summary = "스트리밍 속도 변경", description = "스트리밍 속도 배율을 변경합니다")
    @ApiResponse(responseCode = "200", description = "속도 변경 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Speed updated\", \"speed\": \"2.0x\"}")))
    public ResponseEntity<Map<String, String>> updateSpeed(@RequestParam Double speedMultiplier) {
        streamingService.updateSpeed(speedMultiplier);
        return ResponseEntity.ok(Map.of(
            "status", "Speed updated", 
            "speed", speedMultiplier + "x"
        ));
    }

    @PostMapping("/jump")
    @Operation(summary = "시간 이동", description = "특정 시점으로 이동합니다 (타임머신 모드 전용)")
    @ApiResponse(responseCode = "200", description = "시간 이동 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Jumped to time\", \"targetTime\": \"2017-06-15T14:30:00\"}")))
    public ResponseEntity<Map<String, String>> jumpToTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {
        
        streamingService.jumpToTime(targetTime);
        return ResponseEntity.ok(Map.of(
            "status", "Jumped to time", 
            "targetTime", targetTime.toString()
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "스트리밍 상태 조회", description = "현재 스트리밍 상태와 설정을 조회합니다")
    @ApiResponse(responseCode = "200", description = "스트리밍 상태 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"isStreaming\": true, \"mode\": \"TIMEMACHINE\", \"speed\": 2.0, \"currentTime\": \"2017-06-15T14:30:00\"}")))
    public ResponseEntity<Map<String, Object>> getStreamingStatus() {
        Map<String, Object> status = streamingService.getStreamingStatus();
        return ResponseEntity.ok(status);
    }
}