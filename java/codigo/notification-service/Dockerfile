FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/notification-service-0.0.1-SNAPSHOT.jar /app/notification-service.jar
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/notification-service.jar"]
