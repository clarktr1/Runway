FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S runway && adduser -S runway -G runway
COPY --from=build /app/target/api-*.jar app.jar
USER runway
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
