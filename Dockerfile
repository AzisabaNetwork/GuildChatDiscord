FROM eclipse-temurin:17-jre AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:17-jre AS runner
WORKDIR /app
COPY --from=builder /app/build/libs/GuildChatDiscord.jar .
CMD [ "java", "-jar", "GuildChatDiscord.jar"]
