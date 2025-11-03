FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
EXPOSE 8080

COPY build/libs/*SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
