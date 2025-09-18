package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.global.config.ModelServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelVersionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ModelServiceProperties modelServiceProperties;

    /**
     * GitHub Release에서 사용 가능한 모델 버전 목록 조회
     */
    public List<String> getAvailableVersions() {
        try {
            String githubApiUrl = String.format("https://api.github.com/repos/%s/releases",
                modelServiceProperties.getGithub().getDataRepo());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "fraud-detection-backend");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Fetching available model versions from: {}", githubApiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                githubApiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode releases = objectMapper.readTree(response.getBody());
                List<String> versions = new ArrayList<>();

                for (JsonNode release : releases) {
                    if (!release.get("draft").asBoolean() && !release.get("prerelease").asBoolean()) {
                        versions.add(release.get("tag_name").asText());
                    }
                }

                log.info("Found {} available model versions: {}", versions.size(), versions);
                return versions;
            }

        } catch (Exception e) {
            log.error("Failed to fetch available model versions: {}", e.getMessage(), e);
        }

        // 실패 시 기본 버전 반환
        return List.of(modelServiceProperties.getGithub().getDefaultVersion());
    }

    /**
     * 특정 버전의 모델 파일 URL 정보 조회
     */
    public Map<String, String> getModelFileUrls(String version) {
        try {
            String githubApiUrl = String.format("https://api.github.com/repos/%s/releases/tags/%s",
                modelServiceProperties.getGithub().getDataRepo(), version);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "fraud-detection-backend");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Fetching model file URLs for version {} from: {}", version, githubApiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                githubApiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode release = objectMapper.readTree(response.getBody());
                JsonNode assets = release.get("assets");

                Map<String, String> fileUrls = new HashMap<>();

                for (JsonNode asset : assets) {
                    String fileName = asset.get("name").asText();
                    String downloadUrl = asset.get("browser_download_url").asText();

                    // 예상되는 파일 이름들과 매핑
                    if (fileName.equals("lgbm.pkl")) {
                        fileUrls.put("lgbm", downloadUrl);
                    } else if (fileName.equals("xgboost.pkl")) {
                        fileUrls.put("xgboost", downloadUrl);
                    } else if (fileName.equals("catboost.pkl")) {
                        fileUrls.put("catboost", downloadUrl);
                    } else if (fileName.equals("preprocessor.pkl")) {
                        fileUrls.put("preprocessor", downloadUrl);
                    } else if (fileName.equals("metadata.json")) {
                        fileUrls.put("metadata", downloadUrl);
                    }
                }

                log.info("Found {} model files for version {}: {}", fileUrls.size(), version, fileUrls.keySet());
                return fileUrls;
            }

        } catch (Exception e) {
            log.error("Failed to fetch model file URLs for version {}: {}", version, e.getMessage(), e);
        }

        return new HashMap<>();
    }

    /**
     * 모델 서비스에 새 버전 로드 요청
     */
    public boolean requestModelReload(String version) {
        try {
            Map<String, String> fileUrls = getModelFileUrls(version);

            if (fileUrls.isEmpty()) {
                log.error("No model files found for version: {}", version);
                return false;
            }

            // 모델 서비스에 새 버전 로드 요청
            String reloadEndpoint = modelServiceProperties.getUrl() + "/model/reload";

            Map<String, Object> reloadRequest = new HashMap<>();
            reloadRequest.put("version", version);
            reloadRequest.put("model_urls", fileUrls);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reloadRequest, headers);

            log.info("Requesting model reload for version: {}", version);

            ResponseEntity<String> response = restTemplate.exchange(
                reloadEndpoint, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Model reload request successful for version: {}", version);
                return true;
            } else {
                log.error("Model reload request failed with status: {}", response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to request model reload for version {}: {}", version, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 현재 로드된 모델 버전 정보 조회
     */
    public Map<String, Object> getCurrentModelInfo() {
        try {
            String versionEndpoint = modelServiceProperties.getUrl() + "/model/version";
            ResponseEntity<String> response = restTemplate.getForEntity(versionEndpoint, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode versionJson = objectMapper.readTree(response.getBody());
                Map<String, Object> modelInfo = new HashMap<>();

                modelInfo.put("version", versionJson.path("version").asText("unknown"));
                modelInfo.put("loaded_at", versionJson.path("loaded_at").asText("unknown"));
                modelInfo.put("models_loaded", versionJson.path("models_loaded").asText("unknown"));

                // 모델별 상세 정보가 있다면 추가
                if (versionJson.has("model_details")) {
                    JsonNode modelDetails = versionJson.get("model_details");
                    Map<String, Object> details = new HashMap<>();

                    details.put("lgbm_loaded", modelDetails.path("lgbm").asBoolean(false));
                    details.put("xgboost_loaded", modelDetails.path("xgboost").asBoolean(false));
                    details.put("catboost_loaded", modelDetails.path("catboost").asBoolean(false));
                    details.put("preprocessor_loaded", modelDetails.path("preprocessor").asBoolean(false));

                    modelInfo.put("model_details", details);
                }

                return modelInfo;
            }

        } catch (Exception e) {
            log.error("Failed to get current model info: {}", e.getMessage());
        }

        return Map.of(
            "version", "unknown",
            "error", "Failed to retrieve model information"
        );
    }

    /**
     * 특정 버전의 메타데이터 조회
     */
    public Map<String, Object> getModelMetadata(String version) {
        try {
            Map<String, String> fileUrls = getModelFileUrls(version);
            String metadataUrl = fileUrls.get("metadata");

            if (metadataUrl == null) {
                log.warn("No metadata file found for version: {}", version);
                return new HashMap<>();
            }

            ResponseEntity<String> response = restTemplate.getForEntity(metadataUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode metadata = objectMapper.readTree(response.getBody());
                Map<String, Object> metadataMap = objectMapper.convertValue(metadata, Map.class);

                log.debug("Retrieved metadata for version {}: {}", version, metadataMap.keySet());
                return metadataMap;
            }

        } catch (Exception e) {
            log.error("Failed to get metadata for version {}: {}", version, e.getMessage(), e);
        }

        return new HashMap<>();
    }
}