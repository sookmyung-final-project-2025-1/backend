-- 데이터베이스 초기화 스크립트
CREATE DATABASE IF NOT EXISTS fraud_detection CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- fraud_user 생성
CREATE USER IF NOT EXISTS 'fraud_user'@'%' IDENTIFIED BY 'rootpassword123';

USE fraud_detection;

-- 시간대 설정
SET time_zone = '+09:00';

-- fraud_user에게 fraud_detection 데이터베이스의 모든 권한 부여
GRANT ALL PRIVILEGES ON fraud_detection.* TO 'fraud_user'@'%';
FLUSH PRIVILEGES;

-- 초기 설정 완료 로그
SELECT 'Database and user initialization completed' as status;