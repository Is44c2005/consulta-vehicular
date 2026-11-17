# Dockerfile multi-etapa común para los tres microservicios.
# Uso: docker build --build-arg MODULE=ant-service -t ant-service ./backend
ARG MODULE=api-gateway

# ---------- Etapa 1: compilación ----------
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app

# Primero solo los POM para aprovechar la caché de dependencias de Docker.
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY sri-service/pom.xml sri-service/
COPY ant-service/pom.xml ant-service/
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline || true

COPY . .
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/${MODULE}/target/${MODULE}.jar app.jar
USER app
EXPOSE 8080 8081 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
