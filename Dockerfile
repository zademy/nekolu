FROM maven:3-eclipse-temurin-26 AS builder
WORKDIR /build

COPY lib/tdlib-linux.jar /build/lib/tdlib.jar
RUN mvn install:install-file \
    -Dfile=/build/lib/tdlib.jar \
    -DgroupId=org.drinkless -DartifactId=tdlib -Dversion=1.8.66 -Dpackaging=jar

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:26-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl3 zlib1g curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/*.jar app.jar
COPY lib/libtdjni.so /usr/lib/libtdjni.so

ENV LD_LIBRARY_PATH=/usr/lib
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"

VOLUME /app/tdlib
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
