# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Tải phụ thuộc trước để tận dụng cache khi chỉ đổi mã nguồn
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S gym && adduser -S gym -G gym
USER gym

COPY --from=build /app/target/*.jar app.jar

ENV TZ=Asia/Ho_Chi_Minh
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
