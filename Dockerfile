# ==== Stage 1: Build ====
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
COPY libs ./libs
RUN ./mvnw package -DskipTests -B

# ==== Stage 2: Runtime ====
FROM eclipse-temurin:25-jre
WORKDIR /app

# Install TDLib native library dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl3 \
    zlib1g \
    && rm -rf /var/lib/apt/lists/*

# Copy the TDLib native library (adjust path if needed)
COPY libs/libtdjni.so /usr/lib/libtdjni.so
ENV LD_LIBRARY_PATH=/usr/lib

COPY --from=builder /build/target/*.jar app.jar

# TDLib data directories
VOLUME /app/tdlib

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
