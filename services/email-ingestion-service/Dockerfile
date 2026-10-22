FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/email-ingestion-service-*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
