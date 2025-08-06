#!/bin/bash

# SSL 인증서 생성 스크립트
echo "🔐 SSL 인증서 생성 중..."

# SSL 디렉토리 생성
mkdir -p nginx/ssl

# Self-signed SSL 인증서 생성
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout nginx/ssl/key.pem \
    -out nginx/ssl/cert.pem \
    -subj "/C=KR/ST=Seoul/L=Seoul/O=FraudDetection/CN=fraud-detection"

echo "✅ SSL 인증서 생성 완료!"
echo "📂 위치: nginx/ssl/"
ls -la nginx/ssl/
