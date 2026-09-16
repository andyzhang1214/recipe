# ============================================
# 拾味星球 recipe-backend · 微信云托管 Dockerfile
# 多阶段构建：Maven 构建 → 精简 JRE 运行
# ============================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先拷贝 pom 缓存依赖（利用 Docker layer 缓存）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

# 拷贝源码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 运行阶段（微信云托管实际运行镜像） ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区：国内服务统一东八区
ENV TZ=Asia/Shanghai
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 非 root 用户运行（安全）
RUN useradd -m -u 1001 appuser && chown -R appuser:appuser /app
USER appuser

COPY --from=build /app/target/*.jar app.jar

# 云托管健康检查依赖容器监听 $PORT
EXPOSE 8080

# G1 回收器 + 合理堆内存（云托管默认 256M 起）
# 启动时加载 wxcloud profile（环境变量 MYSQL_ADDRESS 等由云托管注入）
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "/app/app.jar", "--spring.profiles.active=wxcloud"]
