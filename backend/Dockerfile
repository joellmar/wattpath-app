# ETAPA 1: Compilar con JDK 26
FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
# Hacemos ejecutable el wrapper y compilamos ignorando tests para ir rápido
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# ETAPA 2: Ejecutar con JRE ligero
FROM eclipse-temurin:26-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]