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
- **모델 API**: `/model/*` - 머신러닝 모델 추론

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