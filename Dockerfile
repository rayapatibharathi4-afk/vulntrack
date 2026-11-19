# syntax=docker/dockerfile:1

# ---- Build: compile the jar with the Maven Wrapper (tests run in CI, not in the image build) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so this layer is reused while only source code changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- Run: a JRE and a non-root user, with only the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# A fixed, unprivileged user and group; no login shell
RUN groupadd --system vulntrack && useradd --system --gid vulntrack --shell /usr/sbin/nologin vulntrack

COPY --from=build --chown=vulntrack:vulntrack /workspace/target/*.jar /app/app.jar

USER vulntrack
EXPOSE 8080

# The API key variables are read from the environment at start-up; nothing secret is baked into the image
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
