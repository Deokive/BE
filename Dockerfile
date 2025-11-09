FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
EXPOSE 8080

COPY build/libs/*SNAPSHOT.jar app.jar

# JVM 메모리 제한 설정 (최대 768MB 힙 메모리)
# 서버 메모리가 1.80GB이므로 Java는 768MB, 나머지는 MySQL/Redis/Docker/시스템용으로 할당
# 스왑 메모리 사용을 고려하여 여유있게 설정
ENTRYPOINT ["java", "-Xmx768m", "-Xms384m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-XX:+UseStringDeduplication", "-jar", "/app/app.jar"]
