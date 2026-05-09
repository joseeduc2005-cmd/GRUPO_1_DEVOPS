FROM eclipse-temurin:25-jdk-alpine

WORKDIR /app

COPY . .

RUN chmod +x gradlew && ./gradlew  bootJar --no-daemon
RUN cp build/libs/user-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
