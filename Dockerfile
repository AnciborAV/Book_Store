# Build the executable Spring Boot JAR with the project's Java version.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Tests run in GitHub Actions; image builds do not require a database.
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

# Run with a smaller Java runtime and an unprivileged user.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system bookstore && useradd --system --gid bookstore --home-dir /app bookstore \
    && mkdir -p /app/data && chown -R bookstore:bookstore /app
COPY --from=build --chown=bookstore:bookstore /build/target/book-store-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    PORT=10000 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
USER bookstore
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
