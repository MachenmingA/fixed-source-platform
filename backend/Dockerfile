# Build the jar first:
#   cd backend && mvn -q -DskipTests package
# This Dockerfile uses the locally built jar to keep the offline demo reproducible.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/fixed-source-platform-0.1.0.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m"
ENTRYPOINT ["java", "-jar", "app.jar"]
