FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S servicepulse \
    && adduser -S servicepulse -G servicepulse

WORKDIR /app

COPY --from=build --chown=servicepulse:servicepulse \
    /workspace/target/servicepulse-0.0.1-SNAPSHOT.jar \
    /app/servicepulse.jar

USER servicepulse

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health \
        > /dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/servicepulse.jar"]
