package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvBatchService {

    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int BATCH_SIZE = 5000;
    private static final String JOB_STATUS_PREFIX = "batch_job:";

    public String startBatchProcessing(MultipartFile file) {
        String jobId = UUID.randomUUID().toString();

        // 초기 상태 저장
        updateJobStatus(jobId, "RUNNING", 0, 0, 0, 0, null);

        // 비동기 처리 시작
        processCsvFileAsync(file, jobId);

        return jobId;
    }

    @Async
    public void processCsvFileAsync(MultipartFile file, String jobId) {
        int totalRecords = 0;
        int successfulRecords = 0;
        int failedRecords = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            log.info("CSV 헤더: {}", headerLine);

            String[] headers = headerLine.split(",");
            boolean isTrainFile = Arrays.asList(headers).contains("isFraud");

            log.info("파일 타입: {}", isTrainFile ? "TRAIN (with isFraud)" : "TEST (without isFraud)");

            List<Transaction> batch = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                totalRecords++;

                try {
                    Transaction transaction = parseCsvLine(line, headers, isTrainFile, totalRecords);
                    batch.add(transaction);

                    // 배치 크기에 도달하면 DB에 저장
                    if (batch.size() >= BATCH_SIZE) {
                        int savedCount = saveBatch(batch);
                        successfulRecords += savedCount;
                        failedRecords += (batch.size() - savedCount);

                        batch.clear();

                        // 진행률 업데이트 (더 자주 업데이트하지 않도록 수정)
                        if (totalRecords % 25000 == 0) {
                            updateJobStatus(jobId, "RUNNING", totalRecords, totalRecords, successfulRecords, failedRecords, null);
                            log.info("배치 처리 진행: {} 건 완료 (성공: {}, 실패: {})",
                                    totalRecords, successfulRecords, failedRecords);
                        }
                    }

                } catch (Exception e) {
                    failedRecords++;
                    log.debug("라인 {} 처리 실패: {}", totalRecords + 1, e.getMessage());
                }
            }

            // 남은 데이터 처리
            if (!batch.isEmpty()) {
                int savedCount = saveBatch(batch);
                successfulRecords += savedCount;
                failedRecords += (batch.size() - savedCount);
            }

            // 완료 상태 업데이트
            String completionMessage = String.format(
                "CSV 파일 업로드 완료. 성공: %d건, 실패: %d건. " +
                "PENDING 상태로 저장되었습니다. " +
                "사기 탐지를 위해 /api/transactions/batch/process-fraud-detection 엔드포인트를 호출하세요.",
                successfulRecords, failedRecords
            );
            updateJobStatus(jobId, "COMPLETED", totalRecords, totalRecords, successfulRecords, failedRecords, completionMessage);

            log.info("CSV 배치 처리 완료 - 파일: {}, 총: {} 건, 성공: {} 건, 실패: {} 건",
                    file.getOriginalFilename(), totalRecords, successfulRecords, failedRecords);

        } catch (Exception e) {
            log.error("CSV 파일 처리 중 오류 발생", e);
            updateJobStatus(jobId, "FAILED", totalRecords, totalRecords, successfulRecords, failedRecords, e.getMessage());
        }
    }

    private Transaction parseCsvLine(String line, String[] headers, boolean isTrainFile, int lineNumber) {
        String[] values = line.split(",");

        if (values.length != headers.length) {
            throw new IllegalArgumentException("컬럼 수 불일치: 예상 " + headers.length + ", 실제 " + values.length);
        }

        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            row.put(headers[i], i < values.length ? values[i] : "");
        }

        try {
            // 기본 필드 매핑
            String transactionId = row.get("TransactionID");
            BigDecimal amount = parseDecimal(row.get("TransactionAmt"));
            Long transactionDT = parseLong(row.get("TransactionDT"));

            // TransactionDT를 LocalDateTime으로 변환 (Unix timestamp라고 가정)
            LocalDateTime transactionTime = transactionDT != null ?
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(transactionDT), ZoneId.systemDefault()) :
                    LocalDateTime.now().minusDays((long) (Math.random() * 365));

            // 사기 여부 (train 파일에만 존재)
            Boolean isFraud = false;
            Boolean goldLabel = null;
            if (isTrainFile && row.containsKey("isFraud")) {
                isFraud = parseBoolean(row.get("isFraud"));
                goldLabel = isFraud; // 골드 라벨로도 설정
            }

            // 익명화된 피처들을 JSON으로 구성
            String anonymizedFeatures = buildAnonymizedFeatures(row, headers);

            // 가맹점 정보 생성 (ProductCD 기반)
            String productCD = row.get("ProductCD");
            String merchant = "MERCHANT_" + (productCD != null ? productCD : "UNKNOWN");
            String merchantCategory = determineMerchantCategory(productCD, amount);

            return Transaction.builder()
                    .externalTransactionId(transactionId)
                    .userId("USER_" + (lineNumber % 10000)) // 사용자 ID 생성
                    .amount(amount)
                    .merchant(merchant)
                    .merchantCategory(merchantCategory)
                    .transactionTime(transactionTime)
                    .virtualTime(transactionTime)
                    .isFraud(isFraud)
                    .goldLabel(goldLabel)
                    .anonymizedFeatures(anonymizedFeatures)
                    .deviceFingerprint(extractDeviceInfo(row))
                    .status(Transaction.TransactionStatus.PENDING)
                    .currency("USD")
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("라인 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildAnonymizedFeatures(Map<String, String> row, String[] headers) {
        Map<String, Object> features = new HashMap<>();

        // V1-V339 피처들
        for (String header : headers) {
            if (header.startsWith("V") || header.startsWith("C") || header.startsWith("D") ||
                header.startsWith("M") || header.startsWith("id") || header.startsWith("card") ||
                header.startsWith("addr") || header.startsWith("dist") || header.contains("emaildomain")) {

                String value = row.get(header);
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        // 숫자인 경우
                        if (value.matches("-?\\d+(\\.\\d+)?")) {
                            features.put(header, Double.parseDouble(value));
                        } else {
                            features.put(header, value);
                        }
                    } catch (NumberFormatException e) {
                        features.put(header, value);
                    }
                }
            }
        }

        // JSON 문자열로 변환
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(features);
        } catch (Exception e) {
            log.warn("JSON 변환 실패, 빈 객체 반환: {}", e.getMessage());
            return "{}";
        }
    }

    private String determineMerchantCategory(String productCD, BigDecimal amount) {
        if (productCD == null) return "UNKNOWN";

        switch (productCD.toUpperCase()) {
            case "W": return "ONLINE_PURCHASE";
            case "H": return "HOSPITALITY";
            case "C": return "CASH_ADVANCE";
            case "R": return "RETAIL";
            case "S": return "SERVICE";
            default:
                // 금액 기반 분류
                if (amount != null) {
                    if (amount.compareTo(new BigDecimal("50")) <= 0) return "SMALL_PURCHASE";
                    else if (amount.compareTo(new BigDecimal("500")) <= 0) return "MEDIUM_PURCHASE";
                    else return "LARGE_PURCHASE";
                }
                return "UNKNOWN";
        }
    }

    private String extractDeviceInfo(Map<String, String> row) {
        String deviceType = row.get("DeviceType");
        String deviceInfo = row.get("DeviceInfo");

        if (deviceType != null && deviceInfo != null) {
            return deviceType + ":" + deviceInfo;
        } else if (deviceType != null) {
            return deviceType;
        } else if (deviceInfo != null) {
            return deviceInfo;
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    @Transactional
    private int saveBatch(List<Transaction> transactions) {
        try {
            // 배치 처리시에는 사기 탐지 없이 순수 데이터만 저장
            // status를 PENDING으로 설정하여 나중에 별도 처리 가능하도록 함
            transactions.forEach(t -> t.setStatus(Transaction.TransactionStatus.PENDING));

            // JPA 배치 삽입 사용
            List<Transaction> saved = transactionRepository.saveAll(transactions);

            // 강제로 플러시하여 데이터베이스에 즉시 반영
            transactionRepository.flush();

            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}", e.getMessage());

            // 배치를 더 작은 청크로 나누어 재시도
            int successCount = 0;
            int chunkSize = 1000;
            for (int i = 0; i < transactions.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, transactions.size());
                List<Transaction> chunk = transactions.subList(i, end);

                try {
                    chunk.forEach(t -> t.setStatus(Transaction.TransactionStatus.PENDING));
                    List<Transaction> chunkSaved = transactionRepository.saveAll(chunk);
                    successCount += chunkSaved.size();
                } catch (Exception ex) {
                    log.warn("청크 저장 실패, 개별 저장 시도: {}", ex.getMessage());

                    // 개별 저장 시도
                    for (Transaction transaction : chunk) {
                        try {
                            transaction.setStatus(Transaction.TransactionStatus.PENDING);
                            transactionRepository.save(transaction);
                            successCount++;
                        } catch (Exception individualEx) {
                            log.debug("개별 거래 저장 실패: ID={}, 오류={}",
                                    transaction.getExternalTransactionId(), individualEx.getMessage());
                        }
                    }
                }
            }
            return successCount;
        }
    }

    private void updateJobStatus(String jobId, String status, int total, int processed, int successful, int failed, String error) {
        Map<String, Object> jobStatus = new HashMap<>();
        jobStatus.put("jobId", jobId);
        jobStatus.put("status", status);
        jobStatus.put("totalRecords", total);
        jobStatus.put("processedRecords", processed);
        jobStatus.put("successfulRecords", successful);
        jobStatus.put("failedRecords", failed);
        jobStatus.put("progressPercentage", total > 0 ? (double) processed / total * 100 : 0.0);
        jobStatus.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        jobStatus.put("completionMessage", error != null ? error : "");

        redisTemplate.opsForHash().putAll(JOB_STATUS_PREFIX + jobId, jobStatus);
        redisTemplate.expire(JOB_STATUS_PREFIX + jobId, 2, TimeUnit.HOURS); // 2시간 후 만료
    }

    public Map<String, Object> getBatchStatus(String jobId) {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(JOB_STATUS_PREFIX + jobId);
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }

        return result;
    }

    @Transactional
    public long clearBatchData() {
        try {
            log.info("배치 업로드 데이터 삭제 시작");

            // PENDING 상태의 모든 거래와 관련 사기 탐지 결과 삭제
            long deletedTransactions = transactionRepository.deleteByStatus(Transaction.TransactionStatus.PENDING);

            log.info("배치 데이터 삭제 완료: {} 건의 거래 삭제", deletedTransactions);

            return deletedTransactions;
        } catch (Exception e) {
            log.error("배치 데이터 삭제 중 오류 발생", e);
            throw new RuntimeException("배치 데이터 삭제 실패: " + e.getMessage(), e);
        }
    }
}