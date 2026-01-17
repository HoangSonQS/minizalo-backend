# Stage 1: Builder
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw package -Dmaven.test.skip=true

# Stage 2: Runner
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create a non-root user and group
RUN groupadd --system appuser && useradd --system --gid appuser appuser
USER appuser

# Copy the packaged application from the builder stage
COPY --from=builder /app/target/*.jar /app/app.jar

# Expose the port the Spring Boot application runs on
EXPOSE 8080

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]