FROM eclipse-temurin:17-jdk

WORKDIR /app

# 1. Copia el código fuente y la librería específica
COPY ServidorTheRacoonbank.java .
COPY lib/sqlite-jdbc-3.49.1.0.jar /app/lib/sqlite-jdbc.jar

# 2. Compilación con la versión específica
RUN javac -cp lib/sqlite-jdbc.jar ServidorTheRacoonbank.java

# 3. Puerto expuesto
EXPOSE 12345

# 4. Comando de ejecución (asegúrate que coincida con tu clase principal)
CMD ["java", "-cp", ".:lib/sqlite-jdbc.jar", "ServidorTheRacoonbank"]

# Añade al final del Dockerfile
VOLUME /app/data