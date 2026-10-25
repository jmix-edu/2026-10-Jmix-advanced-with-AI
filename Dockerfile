# Application image (spec 04/02). Built inside Docker: the host needs only Docker.
# Three stages: build the production jar, split it into layers, copy the layers onto a JRE.

FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
COPY . .
# Gradle and Vaadin (Node, npm) caches survive between builds
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.vaadin \
    ./gradlew --no-daemon -Pvaadin.productionMode=true bootJar \
    && cp build/libs/timesheets-*.jar /app.jar

FROM eclipse-temurin:21.0.12.1_1-jre-noble AS extract
WORKDIR /extract
COPY --from=build /app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination layers

FROM eclipse-temurin:21.0.12.1_1-jre-noble
# /app is the app user's: Jmix creates its work directory /app/.jmix there
RUN useradd --system --uid 1001 --home-dir /app app \
    && mkdir -p /app/files \
    && chown app /app /app/files
WORKDIR /app
COPY --chmod=0755 docker/app-entrypoint.sh /app/entrypoint.sh
# From the least to the most often changed: a code change rebuilds only the last layer
COPY --from=extract /extract/layers/dependencies/ ./
COPY --from=extract /extract/layers/spring-boot-loader/ ./
COPY --from=extract /extract/layers/snapshot-dependencies/ ./
COPY --from=extract /extract/layers/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
