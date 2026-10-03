FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build/SpringBoot
COPY SpringBoot/pom.xml .
RUN mvn -q dependency:go-offline
COPY SpringBoot/src ./src
COPY sql /build/sql
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-chi-sim && rm -rf /var/lib/apt/lists/*
ENV OCR_COMMAND=tesseract
WORKDIR /app
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
COPY --from=build /build/SpringBoot/target/ai-knowledge-hub-server-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
