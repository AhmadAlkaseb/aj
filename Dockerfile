FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre-jammy
ARG SERVICE
WORKDIR /app
RUN groupadd --system aj && useradd --system --gid aj aj
COPY --from=build /workspace/${SERVICE}/target/app.jar /app/app.jar
USER aj
ENV PORT=7000
EXPOSE 7000
HEALTHCHECK --interval=10s --timeout=8s --start-period=40s --retries=3 CMD ["java", "-cp", "/app/app.jar", "com.aj.common.HealthProbe"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
