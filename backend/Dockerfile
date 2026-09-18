# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy wrapper and pom first so dependency resolution is cached
# independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine AS run
WORKDIR /app

RUN addgroup -S flowdesk && adduser -S flowdesk -G flowdesk
USER flowdesk

COPY --from=build /workspace/target/flowdesk-backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
