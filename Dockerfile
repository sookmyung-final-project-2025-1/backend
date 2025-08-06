# 빌드 스테이지
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Gradle 래퍼와 빌드 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 코드 복사
COPY src src

# 실행 권한 부여 및 빌드
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# 실행 스테이지
FROM eclipse-temurin:17-jre

WORKDIR /app

# 시간대 설정 및 필요한 패키지 설치
ENV TZ=Asia/Seoul
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]