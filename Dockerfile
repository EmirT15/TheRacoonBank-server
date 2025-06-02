# Primera etapa: construcción con Maven
FROM maven:3.8.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY . .
RUN mvn clean package

# Segunda etapa: imagen final más pequeña
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia el JAR construido desde la etapa de construcción
COPY --from=builder /app/target/ServidorTheRacoonbank.jar .

# Puerto que usa tu aplicación
EXPOSE 12345

# Comando para ejecutar
CMD ["java", "-jar", "ServidorTheRacoonbank.jar"]