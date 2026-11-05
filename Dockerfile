# Builder: Java 21 + sbt 1.x. The project pins sbt 1.11.7 in build.properties.
FROM sbtscala/scala-sbt:eclipse-temurin-21_1.x AS builder
WORKDIR /workspace

COPY project/ ./project/
COPY build.sbt ./
RUN sbt -batch update

COPY src/ ./src/
RUN sbt -batch assembly

# Runtime contains only Java and the runnable application.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/target/scala-3.3.9/taskflow.jar /app/taskflow.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/taskflow.jar"]
