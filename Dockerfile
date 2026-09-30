# Imagem de produção do Condo+ (usada pelo Render). Build em duas etapas:
# 1) compila o jar com o Maven Wrapper do projeto; 2) roda só com o JRE (imagem menor).

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# Dependências primeiro: ficam em cache enquanto o pom.xml não muda
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Plano gratuito do Render tem 512 MB: limita o heap e usa um GC leve
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
