# Build context: the repository root (the image needs both backend/ and data/).
#   docker build -f backend/Dockerfile -t commute-map-backend .

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# download dependencies first, so code changes don't re-download them
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q -DskipTests package && cp target/commute-map-backend-*.jar /app.jar

# ---- run ----
FROM eclipse-temurin:21-jre
RUN useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /app.jar app.jar
COPY data data
ENV COMMUTE_DATA_DIR=/app/data \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
