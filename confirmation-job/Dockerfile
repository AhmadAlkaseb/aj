FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy
ARG SERVICE=confirmation-job
WORKDIR /app
RUN groupadd --system aj && useradd --system --gid aj aj
COPY --from=build /workspace/${SERVICE}/target/app.jar /app/app.jar
USER aj
ENV PORT=7000
EXPOSE 7000
HEALTHCHECK NONE
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
