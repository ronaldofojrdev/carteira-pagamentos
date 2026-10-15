# Uso: docker build --build-arg MODULE=account-service -t account-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY contracts contracts
COPY account-service account-service
COPY transaction-service transaction-service
ARG MODULE
RUN mvn -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/${MODULE}-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
