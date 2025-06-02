# Usar imagen con Java 17
FROM eclipse-temurin:17-jdk

# Crear directorio de trabajo
WORKDIR /app

# Copiar el código fuente
COPY ServidorTheRacoonbank.java .

# Compilar el servidor (usando javac directamente)
RUN javac ServidorTheRacoonbank.java

# Puerto que usa tu servidor
EXPOSE 12345

# Comando para ejecutar
CMD ["java", "ServidorTheRacoonbank"]