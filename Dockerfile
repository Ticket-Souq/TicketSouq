# Stage 1: extract layers
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN java --enable-preview -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted
# Stage 2: final lean image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/extracted/dependencies/          ./
COPY --from=builder /app/extracted/spring-boot-loader/    ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/           ./
ENTRYPOINT ["java", "--enable-preview", "org.springframework.boot.loader.launch.JarLauncher"]