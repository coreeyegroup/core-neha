# core-neha backend image. Tests run in CI (GitHub Actions); this build only packages.
FROM maven:3.9.16-eclipse-temurin-25-noble AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline || true
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package \
 && cp target/core-neha-backend.jar /app.jar

FROM eclipse-temurin:25.0.5_8-jre-noble
COPY --from=build /app.jar /opt/core-neha/app.jar
COPY docker/healthcheck.sh /opt/core-neha/healthcheck.sh
# uid 1000 so the container can read the Compose secret files (mode 600, owner 1000)
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/opt/core-neha/app.jar"]
