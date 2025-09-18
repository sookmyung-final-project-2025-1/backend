# 신용카드 사기거래 탐지 시스템 - 백엔드

## 📋 프로젝트 개요
신용카드 사기거래를 탐지하고 분석하는 대시보드 시스템의 백엔드 API 서버입니다. Spring Boot 기반으로 구축되었으며, 머신러닝 모델과 연동하여 실시간 사기거래 탐지 기능을 제공합니다.

## 🏗️ 시스템 아키텍처

### 전체 구조
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Nginx     │────│  Backend    │────│    MySQL    │
│  (Gateway)  │    │ (Spring)    │    │ (Database)  │
└─────────────┘    └─────────────┘    └─────────────┘
                          │
                   ┌─────────────┐    ┌─────────────┐
                   │   Redis     │    │    Model    │
                   │  (Cache)    │    │  (Python)   │
                   └─────────────┘    └─────────────┘
```

### 주요 컴포넌트
- **Backend (Spring Boot)**: REST API 서버, 인증/인가, 비즈니스 로직
- **MySQL**: 사용자 정보, 거래 데이터, 회사 정보 저장
- **Redis**: 세션 관리, 이메일 인증 코드 캐싱
- **Nginx**: 리버스 프록시, SSL 종료, 로드밸런싱
- **Model Server**: 머신러닝 모델 추론 서버

## 🛠️ 기술 스택

### Backend
- **Java 17** + **Spring Boot 3.3.1**
- **Spring Security** (JWT 인증)
- **Spring Data JPA** (ORM)
- **Spring Mail** (이메일 인증)
- **Gradle** (빌드 도구)

### Database & Cache
- **MySQL 8.0** (메인 데이터베이스)
- **Redis 7** (캐시, 세션 저장소)

### Infrastructure
- **Docker & Docker Compose** (컨테이너화)
- **Nginx** (리버스 프록시)
- **SSL/HTTPS** (보안 통신)

## 📁 프로젝트 구조
```
src/main/java/com/credit/card/fraud/detection/
├── auth/                    # 인증/인가 관련
│   ├── controller/         # 인증 컨트롤러
│   ├── dto/               # 요청/응답 DTO
│   ├── filter/            # JWT 필터
│   └── service/           # 인증, JWT, 이메일 서비스
├── common/                # 공통 엔티티
├── company/               # 회사 관련 도메인
├── global/                # 전역 설정
│   ├── config/           # 보안, Redis, Swagger 설정
│   └── exception/        # 예외 처리
└── CreditCardFraudDetectionApplication.java
```

## 🚀 실행 방법

### 1. 환경 변수 설정
`.env` 파일을 생성하고 다음 변수들을 설정하세요:
```bash
# Database
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=fraud_detection
DB_USERNAME=fraud_user
DB_PASSWORD=your_db_password

# JWT
JWT_SECRET_KEY=your_jwt_secret_key

# Email
MAIL_USERNAME=your_gmail_address
MAIL_PASSWORD=your_gmail_app_password

# Model
MODEL_IMAGE=your_model_image_name

# SSL (선택사항)
ENV_FILE=.env
```

### 2. SSL 인증서 생성 (개발환경)
```bash
# Windows
.\setup-ssl.sh

# Linux/Mac
./setup-ssl.sh
```

### 3. Docker Compose로 실행

#### 개발환경
```bash
docker-compose -f docker-compose.dev.yml up -d
```

#### 운영환경
```bash
# 애플리케이션 빌드
./gradlew build

# Docker 이미지 빌드
docker build -t fraud-detection-app:latest .

# 서비스 시작
docker-compose -f docker-compose.prod.yml up -d
```

### 4. 직접 실행 (개발용)
```bash
# 의존성 설치 및 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

## 🔧 주요 기능

### 인증 시스템
- JWT 기반 인증/인가
- 이메일 인증을 통한 회원가입
- 액세스 토큰 (1시간) / 리프레시 토큰 (7일)

### API 엔드포인트
- **인증**: `/api/auth/*` - 회원가입, 로그인, 토큰 갱신
- **헬스체크**: `/actuator/health` - 서버 상태 확인
- **모델 관리**: `/api/model/*` - 머신러닝 모델 관리 및 설정
- **모델 예측**: `/api/fraud/predict` - 실시간 사기거래 예측

### 보안 기능
- HTTPS 강제 리다이렉트
- CORS 정책 적용
- SQL Injection 방지 (JPA 사용)
- XSS 보호 헤더 설정

## 📋 개발 시 주의사항

### 환경별 설정
- **개발환경**: `application.yml`의 `development` 프로필
- **운영환경**: `application.yml`의 `production` 프로필

### 데이터베이스
- 초기 스키마: `init.sql` 파일로 자동 생성
- DDL 모드: 개발시 `update`, 운영시 `validate` 권장

### 로깅
- 개발환경: 상세 로그 출력
- 운영환경: 필요한 로그만 출력, 민감정보 마스킹

## 🔍 모니터링 & 헬스체크

### Actuator 엔드포인트
- `/actuator/health` - 애플리케이션 상태
- `/actuator/info` - 애플리케이션 정보
- `/actuator/metrics` - 메트릭 정보

### Docker 헬스체크
모든 서비스에 헬스체크가 구성되어 있어 자동으로 상태를 모니터링합니다.

## 🤝 협업 가이드

### 브랜치 전략
- `main`: 운영 배포용 브랜치
- `develop`: 개발 통합 브랜치
- `feature/*`: 기능 개발 브랜치

### 코드 컨벤션
- Java Code Convention 준수
- Lombok 어노테이션 활용
- REST API 설계 원칙 준수

### 커밋 메시지
```
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 수정
style: 코드 포맷팅
refactor: 코드 리팩토링
test: 테스트 코드
chore: 빌드 관련 수정
```

## 🤖 머신러닝 모델 연동

### 개요
이 시스템은 IEEE Card Fraud Detection 데이터셋을 기반으로 훈련된 3개의 앙상블 모델을 사용합니다:
- **LightGBM**: 정밀도가 높은 그래디언트 부스팅 모델
- **XGBoost**: 높은 재현율을 가진 극한 그래디언트 부스팅 모델
- **CatBoost**: 균형잡힌 성능의 카테고리컬 부스팅 모델

### 모델 서버 아키텍처
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │────│   FastAPI       │────│   GitHub        │
│   Backend       │    │   Model Server  │    │   Releases      │
│                 │    │   (ML Models)   │    │   (Model Files) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### FastAPI 모델 서버 연동

#### 엔드포인트 구조
모델 팀에서 제공하는 FastAPI 서버의 엔드포인트:
```
{MODEL_SERVICE_URL}/model/lgbm/predict      # LightGBM 모델
{MODEL_SERVICE_URL}/model/xgboost/predict   # XGBoost 모델
{MODEL_SERVICE_URL}/model/catboost/predict  # CatBoost 모델
```

#### 요청/응답 형식
**요청 형식:**
```json
{
  "transaction_id": "txn_12345",
  "transaction_dt": 1234567890,
  "transaction_amt": 100.50,
  "product_cd": "W",
  "card1": "1234",
  "card2": "567.0",
  "addr1": "123",
  "dist1": 100.5,
  "p_emaildomain": "gmail.com",
  "counting_features": {"C1": 1, "C2": 0, "C3": 1},
  "time_deltas": {"D1": 0.5, "D2": 1.2},
  "match_features": {"M1": "T", "M2": "F"},
  "vesta_features": {"V1": 1.0, "V2": 2.3},
  "identity_features": {"id_01": 0.0, "id_02": 1.0}
}
```

**응답 형식:**
```json
{
  "score": 0.823456,
  "processing_time_ms": 45
}
```

### 모델 버전 관리

#### GitHub Release 기반 모델 관리
- **Data 레포**: `sookmyung-final-project-2025-1/Data`
- **버전 형식**: `v1.0.0`, `v1.0.1`, `v1.1.0` 등
- **릴리즈당 파일**: 5개 파일이 포함
  - `lgbm.pkl`: LightGBM 모델
  - `xgboost.pkl`: XGBoost 모델
  - `catboost.pkl`: CatBoost 모델
  - `preprocessor.pkl`: 전처리기
  - `metadata.json`: 모델 메타데이터

#### 모델 버전 관리 API
```bash
# 사용 가능한 버전 목록 조회
GET /api/model/versions/available

# 특정 버전으로 모델 업데이트
POST /api/model/versions/{version}/deploy

# 특정 버전의 메타데이터 조회
GET /api/model/versions/{version}/metadata

# 현재 모델 서비스 상태 확인
GET /api/model/service/status
```

### 앙상블 예측 방식

#### 1. 기본 앙상블 예측
기본 가중치(LGBM: 0.333, XGBoost: 0.333, CatBoost: 0.334)를 사용한 예측:
```bash
POST /api/fraud/predict
```

#### 2. 사용자 정의 가중치 예측
사용자가 직접 가중치를 조정하여 예측 (총합이 1이 되도록 자동 정규화):
```bash
POST /api/model/predict/custom-weights
```

**요청 예시:**
```json
{
  "transactionRequest": {
    "transactionId": "txn_12345",
    "amount": 100.50,
    "productCode": "W",
    ...
  },
  "lgbmWeight": 0.5,
  "xgboostWeight": 0.3,
  "catboostWeight": 0.2,
  "autoNormalize": true
}
```

#### 3. 단일 모델 예측
특정 모델 하나만 사용한 예측:
```bash
POST /api/model/predict/single/{modelType}
```

### 모델 설정 관리

#### 가중치 관리
```bash
# 현재 가중치 조회
GET /api/model/weights

# 가중치 업데이트
PUT /api/model/weights

# 가중치 유효성 검증
GET /api/model/weights/validation?lgbm=0.5&xgboost=0.3&catboost=0.2
```

#### 임계값 관리
```bash
# 예측 임계값 업데이트 (기본값: 0.5)
PUT /api/model/threshold?threshold=0.7
```

### 환경 설정

#### application.yml 설정
```yaml
model:
  service:
    enabled: ${MODEL_SERVICE_ENABLED:true}
    url: ${MODEL_SERVICE_URL:http://model:8000}
    timeout: ${MODEL_SERVICE_TIMEOUT:30000}
    retry:
      max-attempts: ${MODEL_SERVICE_RETRY_MAX_ATTEMPTS:3}
      delay: ${MODEL_SERVICE_RETRY_DELAY:1000}
    github:
      data-repo: ${MODEL_DATA_REPO:sookmyung-final-project-2025-1/Data}
      default-version: ${MODEL_DEFAULT_VERSION:v1.0.0}
```

#### 환경 변수
```bash
# 모델 서비스 활성화 여부
MODEL_SERVICE_ENABLED=true

# 모델 서비스 URL (개발: http://model:8000, 운영: 실제 IP)
MODEL_SERVICE_URL=http://model:8000

# 모델 서비스 타임아웃 (ms)
MODEL_SERVICE_TIMEOUT=30000

# GitHub Data 레포
MODEL_DATA_REPO=sookmyung-final-project-2025-1/Data

# 기본 모델 버전
MODEL_DEFAULT_VERSION=v1.0.0
```

### 폴백 메커니즘

모델 서비스에 연결할 수 없는 경우, 시스템은 자동으로 IEEE 데이터셋 기반의 시뮬레이션 모드로 전환됩니다:

1. **실제 모델 우선**: 모델 서비스가 정상 동작할 때는 실제 모델 사용
2. **헬스체크**: 주기적으로 모델 서비스 상태 확인
3. **자동 폴백**: 연결 실패 시 시뮬레이션 모드로 자동 전환
4. **로깅**: 모든 모드 전환 상황을 로그로 기록

### 모니터링

#### 모델 서비스 모니터링
```bash
# 모델 서비스 헬스체크
GET /api/model/service/status

# 현재 로드된 모델 정보
GET /api/model/service/version

# 모델 서비스 재로드
POST /api/model/service/reload
```

#### 신뢰도 점수 모니터링
```bash
# 모델 신뢰도 점수 조회
GET /api/model/confidence-score?startTime=2025-09-18T00:00:00&endTime=2025-09-18T23:59:59

# 피처 중요도 분석
GET /api/model/feature-importance?sampleSize=1000
```

### 개발 및 테스트

#### 로컬 개발 환경
개발 환경에서는 `MODEL_SERVICE_ENABLED=false`로 설정하여 시뮬레이션 모드로 개발할 수 있습니다.

#### 운영 환경 배포
1. 모델 서버가 먼저 배포되고 정상 동작 확인
2. 백엔드 서버에서 모델 서버 연결성 테스트
3. 단계적으로 실제 모델 서비스 활성화

#### 성능 최적화
- **병렬 처리**: 3개 모델에 대한 예측을 병렬로 실행
- **연결 풀링**: RestTemplate을 통한 효율적인 HTTP 연결 관리
- **타임아웃 설정**: 모델 서비스 응답 지연 시 빠른 폴백
- **캐싱**: 모델 버전 정보 및 메타데이터 캐싱

### GitHub Secrets 설정

운영 환경 배포를 위해 다음 Secrets를 GitHub 레포지토리에 설정해야 합니다:

#### 기존 Secrets (이미 설정됨)
- `JWT_SECRET_KEY`: JWT 토큰 서명 키
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: 데이터베이스 연결 정보
- `MAIL_USERNAME`, `MAIL_PASSWORD`: 이메일 서비스 계정
- `DOCKERHUB_USER`, `DOCKERHUB_TOKEN`: Docker Hub 계정
- `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, etc.: MySQL 설정

#### 모델 서비스 관련 새 Secrets
```bash
# 운영 환경 모델 서비스 URL (실제 IP 주소)
MODEL_SERVICE_URL=https://211.110.155.54

# 필요시 추가 설정
MODEL_SERVICE_TIMEOUT=30000
MODEL_DATA_REPO=sookmyung-final-project-2025-1/Data
```

이렇게 설정하면:
- **개발 환경**: 기본값으로 `http://model:8000` 사용 (Docker 내부 통신)
- **운영 환경**: GitHub Secrets에서 실제 외부 IP 주소 사용