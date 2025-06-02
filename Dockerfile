FROM eclipse-temurin:17-jdk

WORKDIR /app

# 1. Copia el código fuente y la librería SQLite
COPY ServidorTheRacoonbank.java .
COPY lib/sqlite-jdbc-3.49.1.0.jar /app/lib/sqlite-jdbc.jar

# 2. Crea directorio para datos persistentes y compila
RUN mkdir -p /app/data && \
    javac -cp lib/sqlite-jdbc.jar ServidorTheRacoonbank.java

# 3. Puerto expuesto (debe coincidir con el de tu servidor Java)
EXPOSE 12345

# 4. Health check para Render (nuevo)
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:12345/health || exit 1

# 5. Volumen para datos persistentes
VOLUME /app/data

# 6. Comando de ejecución optimizado
CMD ["java", "-cp", ".:lib/sqlite-jdbc.jar", "-Djava.net.preferIPv4Stack=true", "ServidorTheRacoonbank"]