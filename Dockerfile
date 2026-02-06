FROM eclipse-temurin:8-jre
ARG JAR_FILE=target/mercury-auth-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
# Keep JVM heap below container limits while leaving headroom for native memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=50.0"
ENTRYPOINT ["java","-jar","/app.jar"]
