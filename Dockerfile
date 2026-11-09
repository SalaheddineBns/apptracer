# Demo image: the Spring Boot API also serves the Angular app (one service, one URL, no CORS).
# Build: docker build --build-arg PRIMEUI_LICENSE=... -t apptracer .

# 1. Angular build
FROM node:24-bookworm-slim AS front
WORKDIR /app/frontend
# The PrimeUI key ends up in the JavaScript bundle anyway; it is only kept out of the repository.
ARG PRIMEUI_LICENSE=""
ENV PRIMEUI_LICENSE=${PRIMEUI_LICENSE} \
    CYPRESS_INSTALL_BINARY=0
COPY frontend/package.json frontend/package-lock.json ./
COPY frontend/scripts ./scripts
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Spring Boot build, with the Angular files as static resources
FROM eclipse-temurin:21-jdk AS back
WORKDIR /app/backend
COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=front /app/frontend/dist/frontend/browser ./src/main/resources/static
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /app/app.jar

# 3. Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 apptracer
COPY --from=back /app/app.jar ./app.jar
USER apptracer
EXPOSE 8080
# Sized for a 512 MB free instance; PORT is set by the host (8080 by default)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
