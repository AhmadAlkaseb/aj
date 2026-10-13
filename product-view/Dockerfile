FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/product-view-1.0.0.jar /app/app.jar
EXPOSE 7000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
