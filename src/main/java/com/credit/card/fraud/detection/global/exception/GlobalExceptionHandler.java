package com.credit.card.fraud.detection.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 입력 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("입력 값이 올바르지 않습니다.")
                .details(errors)
                .build();

        log.warn("입력 검증 실패: {}", errors);
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(errorResponse);
    }

    /**
     * 파일 업로드 크기 초과 예외 처리
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {

        // 상세한 에러 정보 수집
        String maxFileSize = getSpringProperty("spring.servlet.multipart.max-file-size", "알 수 없음");
        String maxRequestSize = getSpringProperty("spring.servlet.multipart.max-request-size", "알 수 없음");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("File Upload Error")
                .message("파일 크기가 허용된 최대 크기를 초과했습니다.")
                .details(Map.of(
                    "maxUploadSize", ex.getMaxUploadSize(),
                    "configuredMaxFileSize", maxFileSize,
                    "configuredMaxRequestSize", maxRequestSize,
                    "errorCause", ex.getCause() != null ? ex.getCause().getMessage() : "Unknown"
                ))
                .build();

        log.error("파일 업로드 크기 초과 - MaxUploadSize: {}, 설정된 max-file-size: {}, 설정된 max-request-size: {}, Exception: {}",
                ex.getMaxUploadSize(), maxFileSize, maxRequestSize, ex.getMessage());

        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(errorResponse);
    }

    private String getSpringProperty(String propertyName, String defaultValue) {
        try {
            return System.getProperty(propertyName, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Business Logic Error")
                .message(ex.getMessage())
                .build();

        log.warn("비즈니스 로직 오류: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(errorResponse);
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("서버 내부 오류가 발생했습니다.")
                .build();

        log.error("서버 내부 오류", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(errorResponse);
    }

    /**
     * 에러 응답 DTO
     */
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private Object details;

        public ErrorResponse() {}

        public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, Object details) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.details = details;
        }

        public static ErrorResponseBuilder builder() {
            return new ErrorResponseBuilder();
        }

        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }

        public static class ErrorResponseBuilder {
            private LocalDateTime timestamp;
            private int status;
            private String error;
            private String message;
            private Object details;

            ErrorResponseBuilder() {}

            public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorResponseBuilder status(int status) {
                this.status = status;
                return this;
            }

            public ErrorResponseBuilder error(String error) {
                this.error = error;
                return this;
            }

            public ErrorResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ErrorResponseBuilder details(Object details) {
                this.details = details;
                return this;
            }

            public ErrorResponse build() {
                return new ErrorResponse(timestamp, status, error, message, details);
            }
        }
    }
}