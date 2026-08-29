# Molde do kit — copiar para backend/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Camada de dependencias separada: so refaz o download se o pom mudar.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget tzdata \
 && addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/uploads && chown -R app:app /app

COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage vem do compose (JAVA_TOOL_OPTIONS): a JVM respeita o limite
# de memoria do container em vez de enxergar a RAM inteira da VPS.
ENTRYPOINT ["java", "-jar", "app.jar"]
