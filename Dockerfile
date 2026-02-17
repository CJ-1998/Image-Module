# 1단계: 빌드 스테이지
FROM gradle:8.10.1-jdk17 AS build

WORKDIR /app

# build.gradle과 settings.gradle을 먼저 복사하여 종속성 캐싱 활용
ARG FILE_DIRECTORY
# 경로 처리를 위해 COPY 방식을 유지하되, 전체 컨텍스트 복사가 안전할 수 있음
COPY ${FILE_DIRECTORY} /app

# 빌드 (테스트 제외)
RUN gradle clean bootJar -x test

# 2단계: 실행 스테이지
# Alpine은 네이티브 라이브러리(WebP 등) 호환성 문제가 잦으므로 Ubuntu 기반을 사용합니다.
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# [변경 2] WebP 변환을 위한 필수 라이브러리 설치 (apt-get 사용)
# webp 패키지를 설치하면 관련 의존성이 모두 해결됩니다.
RUN apt-get update && apt-get install -y \
    webp \
    libwebp-dev \
    libpng-dev \
    libjpeg-dev \
    && rm -rf /var/lib/apt/lists/*

# build 스테이지에서 생성된 jar 파일 복사
COPY --from=build /app/build/libs/*SNAPSHOT.jar app.jar

# 컨테이너 실행
CMD ["java", "-jar", "app.jar"]
