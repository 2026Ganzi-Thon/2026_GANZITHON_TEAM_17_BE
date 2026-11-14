# 한 올 백엔드 이미지. 빌드와 실행을 나눈 멀티스테이지다. (빌드 도구는 최종 이미지에 남기지 않는다)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew --version
COPY src ./src
# 테스트는 CI(ci.yml)가 이미 돌린다.
RUN ./gradlew bootJar --no-daemon -x test

# 헬스체크가 curl을 쓰므로 curl이 들어 있는 Ubuntu 기반 JRE 이미지를 쓴다. (alpine에는 curl이 없다)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# LocalDateTime.now()와 DB 시각을 한국 시간으로 맞춘다. (JAVA_TOOL_OPTIONS로 메모리 옵션을 더할 때도 이 옵션은 유지한다)
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
