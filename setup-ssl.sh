#!/bin/bash

# SSL 인증서 생성 스크립트
echo "🔐 SSL 인증서 생성 중..."

# SSL 디렉토리 생성
mkdir -p nginx/ssl

if [ -f nginx/ssl/cert.pem ] || [ -f nginx/ssl/key.pem ]; then
    echo "기존 SSL 파일 권한 수정..."
    sudo chown $USER:$USER nginx/ssl/* 2>/dev/null || true
    sudo chmod 644 nginx/ssl/* 2>/dev/null || true
fi

# Self-signed SSL 인증서 생성 (SAN에 IP 주소 포함)
cat > nginx/ssl/openssl.conf << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = KR
ST = Seoul
L = Seoul
O = FraudDetection
CN = fraud-detection

[v3_req]
keyUsage = digitalSignature, keyEncipherment, dataEncipherment, keyAgreement
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = smupaypro.vercel.app
IP.1 = 127.0.0.1
IP.2 = 192.168.75.251
IP.3 = 211.110.155.54
EOF

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout nginx/ssl/key.pem \
    -out nginx/ssl/cert.pem \
    -config nginx/ssl/openssl.conf \
    -extensions v3_req

chown $USER:$USER nginx/ssl/*
chmod 644 nginx/ssl/*

echo "✅ SSL 인증서 생성 완료!"
echo "📂 위치: nginx/ssl/"
ls -la nginx/ssl/
