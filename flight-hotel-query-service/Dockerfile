# Build from the repository root:  docker build -f flight-hotel-query-service/Dockerfile -t flight-hotel-query-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl flight-hotel-query-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app
WORKDIR /app
COPY --from=build /workspace/flight-hotel-query-service/target/*.jar /app/app.jar
USER 1001
EXPOSE 8082 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
