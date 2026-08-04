# syntax=docker/dockerfile:1.7

FROM node:24-alpine3.23 AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.16-eclipse-temurin-21-alpine AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /workspace/frontend/dist/comercio-flex-frontend/browser ./src/main/resources/static
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21.0.11_10-jre-alpine-3.22
RUN addgroup -S comercioflex && adduser -S -G comercioflex comercioflex
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/comercio-flex-backend-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data/media && chown -R comercioflex:comercioflex /app
USER comercioflex
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
