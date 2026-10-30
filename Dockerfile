# Образ приложения (spec 04/02). Собирается внутри Docker: на хосте нужен только Docker.
# Три стадии: собрать продакшн-jar, разложить его на слои, скопировать слои поверх JRE.

FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
COPY . .
# Кэши Gradle и Vaadin (Node, npm) сохраняются между сборками
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.vaadin \
    ./gradlew --no-daemon -Pvaadin.productionMode=true bootJar \
    && cp build/libs/timesheets-*.jar /app.jar

FROM eclipse-temurin:21.0.12.1_1-jre-noble AS extract
WORKDIR /extract
COPY --from=build /app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination layers

FROM eclipse-temurin:21.0.12.1_1-jre-noble
# /app принадлежит пользователю app: Jmix создаёт там рабочий каталог /app/.jmix
RUN useradd --system --uid 1001 --home-dir /app app \
    && mkdir -p /app/files \
    && chown app /app /app/files
WORKDIR /app
COPY --chmod=0755 docker/app-entrypoint.sh /app/entrypoint.sh
# От редко меняемых к часто меняемым: правка кода пересобирает только последний слой
COPY --from=extract /extract/layers/dependencies/ ./
COPY --from=extract /extract/layers/spring-boot-loader/ ./
COPY --from=extract /extract/layers/snapshot-dependencies/ ./
COPY --from=extract /extract/layers/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
