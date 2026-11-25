# Stage 1: Build with Maven & JDK 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Minimal non-root runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S veritas && adduser -S veritas -G veritas
COPY --from=builder /app/target/*.jar app.jar
RUN chown veritas:veritas app.jar
USER veritas
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
