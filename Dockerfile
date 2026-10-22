# API do Juizin (Spring Boot). O jar é compilado com o Maven dentro do Docker.

# 1. Jar
FROM maven:3.9-eclipse-temurin-21 AS jar
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests \
 && cp target/*.jar app.jar

# 2. Execução (opções da JVM via JAVA_TOOL_OPTIONS)
FROM eclipse-temurin:21-jre-noble
RUN useradd --system --user-group --no-create-home juizin
COPY --from=jar /build/app.jar /app/api.jar
USER juizin
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
