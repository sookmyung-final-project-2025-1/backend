FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx4g", "-Xms2g", "-XX:MaxDirectMemorySize=1g", "-jar", "app.jar"]