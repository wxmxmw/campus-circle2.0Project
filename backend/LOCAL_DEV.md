# Backend Local Dev

当前后端开发环境已经切换为默认连接远程 MySQL，不再默认使用本地 H2。

## 当前默认配置

默认激活 profile：

- `local-mysql`

对应配置文件：

- `src/main/resources/application.properties`
- `src/main/resources/application-local-mysql.properties`

当前默认数据库连接：

- Host: `121.199.30.215`
- Port: `3306`
- Database: `campus_circle_wxm_db`
- Username: `wxm`
- Password: 通过环境变量 `REMOTE_DB_PASSWORD` 提供

## 启动前准备

后端启动前，先在终端设置数据库密码：

```powershell
$env:REMOTE_DB_PASSWORD="你的数据库密码"
```

Redis 当前默认仍然是本机：

- Host: `localhost`
- Port: `6379`

如果本机没有 Redis，部分在线状态和 token 黑名单能力可能降级，但核心接口通常仍可联调。

## 推荐启动方式

在 `backend/` 目录执行：

```powershell
mvn clean package -DskipTests
java -jar target\campus-backend-0.0.1-SNAPSHOT.jar
```

说明：

- 当前在 Windows 中文路径环境下，`mvn spring-boot:run` 可能出现主类加载失败
- 因此当前推荐使用 `package + java -jar` 方式启动

## 启动成功后检查

建议至少检查以下地址：

1. Swagger UI: `http://localhost:8080/swagger-ui.html`
2. 健康检查: `http://localhost:8080/actuator/health`

如果远程数据库连接成功，JPA 会根据实体自动建表或更新表结构，因为当前配置为：

```properties
spring.jpa.hibernate.ddl-auto=update
```

## 当前运行目录相关说明

即使数据库已经切到远程 MySQL，下面这些内容仍然默认保存在后端本地运行目录：

- 上传文件目录：`./uploads`
- 日志文件：`./logs/campus-backend.log`

也就是说，如果你在本机启动后端，上传图片会保存在本机 `backend/uploads/` 下；以后部署到服务器后，则会保存在服务器上的对应目录。
