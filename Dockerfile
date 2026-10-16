# ── Build ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
RUN mvn -q -B package -DskipTests

# ── Run ───────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /src/target/atelier-api-*.jar app.jar
USER app
EXPOSE 8089
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
