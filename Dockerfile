# syntax=docker/dockerfile:1
# One Dockerfile for all services:  docker build --build-arg SERVICE=order-service -t order-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG SERVICE
# -am builds saga-common as well; tests run in CI, not in the image build
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-exec.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
