FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 bot
WORKDIR /app
COPY --from=build /app/target/todos-bot.jar app.jar
USER bot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]