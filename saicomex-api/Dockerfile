# Build stage — produces the fat jar.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
# Copy the pom alone first so the dependency layer is cached across source
# changes; without this every code edit re-downloads the whole dependency tree.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Runs as a non-root user; nothing in this container needs to write outside /tmp.
RUN addgroup -S comex && adduser -S comex -G comex && chown -R comex:comex /app
USER comex

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# JAVA_OPTS is set per environment in docker-compose. The default here is sized
# for the shared VPS this runs on, alongside the fleet system's two backends.
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms256m -Xmx512m -XX:+UseG1GC} -jar app.jar"]
