FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn test && mvn package dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/classes ./classes
COPY --from=build /workspace/target/dependency ./dependency

ENV HOST=0.0.0.0
ENV PORT=7000
ENV DB_URL=jdbc:postgresql://db:5432/users
ENV DB_USER=postgres
ENV DB_PASSWORD=postgres
ENV HIBERNATE_DDL_AUTO=update
ENV HIBERNATE_SHOW_SQL=false

EXPOSE 7000

CMD ["java", "-cp", "/app/classes:/app/dependency/*", "app.Application"]
