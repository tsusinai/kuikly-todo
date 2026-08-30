# 构建阶段:应用 Gradle 打包(application 插件 installDist 产物)
# 注意:installDist 不会产出可执行 fat jar,而是产出 build/install/<name>/ 目录
# (内含 lib/ 下的"应用薄 jar + 全部依赖 jar"以及 bin/ 启动脚本)。
# 因此运行阶段不能用 `java -jar <thin jar>`(会 NoClassDefFoundError),须用 `-cp "app/lib/*"`。
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN gradle installDist --no-daemon

# 运行阶段:JRE17
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/task1-backend/ ./app
EXPOSE 8080
# `app/lib/*` 通配符会把 app jar(名字/版本无关)+ 所有依赖 jar 都放进 classpath,
# 避免 `-jar` 只加载薄 jar 而缺失 Ktor/Netty 等依赖。
ENTRYPOINT ["java", "-cp", "app/lib/*", "com.example.task1.backend.ApplicationKt"]
