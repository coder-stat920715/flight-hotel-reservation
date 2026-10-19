# Build from the repository root:  docker build -f api-gateway/Dockerfile -t api-gateway .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/*.jar /app/app.jar
USER 1001
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
