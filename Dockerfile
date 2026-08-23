# ==== Stage 1: Build TDLib native for Linux ====
FROM ubuntu:24.04 AS tdlib-builder
WORKDIR /td

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake php-cli gperf zlib1g-dev libssl-dev \
    openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

# TDLib source is provided from the host (pre-cloned)
COPY docker/td-src ./td

RUN cd td && mkdir build && cd build && \
    cmake -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON \
          -DCMAKE_INSTALL_PREFIX:PATH=../../example/java/td .. && \
    cmake --build . --target install -j2 && \
    cd ../../example/java && mkdir build && cd build && \
    cmake -DCMAKE_BUILD_TYPE=Release \
          -DTd_DIR=/td/example/java/td/lib/cmake/Td \
          -DCMAKE_INSTALL_PREFIX:PATH=.. .. && \
    cmake --build . --target install -j2

# ==== Stage 2: Build the application ====
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

# Install TDLib jar into local Maven repo FIRST (it's not on Maven Central)
COPY --from=tdlib-builder /td/example/java/bin/tdlib.jar /build/lib/tdlib.jar
RUN mvn install:install-file -Dfile=/build/lib/tdlib.jar \
    -DgroupId=org.drinkless -DartifactId=tdlib -Dversion=1.8.66 -Dpackaging=jar

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# ==== Stage 3: Runtime ====
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl3 zlib1g curl \
    && rm -rf /var/lib/apt/lists/*

# TDLib native library for Linux
COPY --from=tdlib-builder /td/example/java/bin/libtdjni.so /usr/lib/libtdjni.so
ENV LD_LIBRARY_PATH=/usr/lib

# Application jar
COPY --from=builder /build/target/*.jar app.jar

VOLUME /app/tdlib
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
