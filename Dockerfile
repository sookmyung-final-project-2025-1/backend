FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx3g", "-Xms1g", "-XX:MaxDirectMemorySize=512m", "-XX:+UseG1GC", "-jar", "app.jar"]