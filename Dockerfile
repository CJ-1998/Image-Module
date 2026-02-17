# Dockerfile
# 1단계: 빌드 스테이지
FROM gradle:8.10.1-jdk17 AS build

WORKDIR /app

# build.gradle과 settings.gradle을 먼저 복사하여 종속성 캐싱 활용 (최적화)
ARG FILE_DIRECTORY
COPY ${FILE_DIRECTORY}/build.gradle ${FILE_DIRECTORY}/settings.gradle ./
# 빌드에 필요한 소스 코드 복사
COPY ${FILE_DIRECTORY} .

# 테스트 제외하고 빌드 속도 향상
RUN gradle clean bootJar -x test

# 2단계: 실행 스테이지
# openjdk:17-jdk-slim 대신 유지보수가 활발한 eclipse-temurin 사용
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# build 스테이지에서 생성된 jar 파일 복사
COPY --from=build /app/build/libs/*SNAPSHOT.jar app.jar

# 컨테이너 실행 환경 최적화 (메모리 제한 등)
#"-Dspring.profiles.active=prod",
CMD ["java", "-jar", "app.jar"]
