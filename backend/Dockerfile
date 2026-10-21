# ---------- Stage 1: build the app ----------
# Start from an image that already has Java 21 and Maven installed
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download libraries.
# Docker caches this step, so later builds are faster if pom.xml didn't change.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the source code and build the .jar file (tests already ran on your computer)
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Stage 2: run the app ----------
# A smaller image with only the Java runtime (no Maven, no source code)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Use at most 75% of the server's memory (the free plan has little memory)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]