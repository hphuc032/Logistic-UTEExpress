FROM eclipse-temurin:21-jdk AS builder

# Keep Maven Wrapper on the ZIP distribution matching its pinned SHA-256.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app/var/uploads

COPY --from=builder /build/target/uteexpress-0.1.0-SNAPSHOT.jar /app/app.jar

ENV SERVER_PORT=10000
ENV UPLOAD_DIR=/app/var/uploads

EXPOSE 10000

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=65", "-jar", "/app/app.jar"]
