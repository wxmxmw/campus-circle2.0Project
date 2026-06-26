# Campus Circle 2.0

本仓库是项目的前后端一体仓库，当前结构如下：

- `backend/`: Spring Boot 后端
- `frontend/`: uni-app 前端
- `DEPLOYMENT_GUIDE.md`: 服务器部署说明
- `README.md`: 项目说明


## 当前开发模式

项目当前已切换为：

- 后端默认连接远程 MySQL
- 前端仍然默认请求本机后端：`http://localhost:8080/api`

当前后端默认激活的 profile 是：

- `local-mysql`

对应配置文件：

- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-local-mysql.properties`

## 环境要求

后端：

- JDK 17
- Maven 3.9+
- Redis（可选，默认 `localhost:6379`）

前端：

- HBuilderX

## 后端启动

### 1. 配置远程数据库密码

后端数据库密码不会写死在仓库里，启动前需要先设置环境变量：

```powershell
$env:REMOTE_DB_PASSWORD="你的数据库密码"
```

### 2. 打包

在 `backend/` 目录执行：

```powershell
mvn clean package -DskipTests
```

### 3. 启动

继续在 `backend/` 目录执行：

```powershell
java -jar target\campus-backend-0.0.1-SNAPSHOT.jar
```

说明：

- 当前在 Windows 中文路径环境下，`mvn spring-boot:run` 可能出现主类加载失败
- 因此目前推荐使用 `mvn clean package -DskipTests` + `java -jar` 方式启动

## 当前默认远程数据库

后端当前默认连接的是远程 MySQL：

- Host: `121.199.30.215`
- Port: `3306`
- Database: `campus_circle_wxm_db`
- Username: `wxm`
- Password: 通过 `REMOTE_DB_PASSWORD` 提供

JPA 当前配置：

- `spring.jpa.hibernate.ddl-auto=update`

因此首次启动时会自动建表/更新表结构。

## 如何确认后端启动成功

后端启动后，建议检查：

1. Swagger UI: `http://localhost:8080/swagger-ui.html`
2. 健康检查: `http://localhost:8080/actuator/health`

如果远程数据库连接成功，原本空的 `campus_circle_wxm_db` 会开始出现业务表。

## 前端启动

前端目录：

- `frontend/`

请使用 HBuilderX 打开 `frontend/` 后运行。

前端当前默认请求：

```text
http://localhost:8080/api
```

也就是说，前端本地联调时，需要先启动本机后端。

## 真机调试时修改后端地址

如果使用手机真机调试，`localhost` 指向的是手机自身，不是电脑。

这时需要把前端后端地址切到你电脑的局域网 IP，例如：

```js
import { setDevBackendOrigin } from '@/utils/api'
setDevBackendOrigin('http://192.168.1.23:8080')
```

恢复默认地址：

```js
import { clearDevBackendOrigin } from '@/utils/api'
clearDevBackendOrigin()
```

统一地址配置位置：

- `frontend/utils/api.js`

## 前端统一地址入口

接口基础地址定义在：

- `frontend/utils/api.js`
  - `DEFAULT_BACKEND_ORIGIN`
  - `getApiBaseUrl()`

请求封装在：

- `frontend/utils/request.js`

上传入口在：

- `frontend/utils/request.js`
  - `upload()`
- `frontend/api/common.js`
  - `uploadImage()`
  - `uploadBatchImages()`

后端上传接口对应：

- `backend/src/main/java/com/campus/campus_backend/controller/CommonController.java`

## 协作建议

当前更适合这样协作：

1. 每位成员使用自己的独立远程数据库账号和数据库
2. 接口字段变更时，前后端尽量在同一分支一起改
3. 不要依赖某条测试数据的固定主键值
4. 如果某功能依赖初始化数据，把初始化方式写进代码或文档
5. 提交前说明数据库结构、接口和测试方式的变化

## 部署准备建议

部署前建议确保仓库内不包含以下本地产物：

- 后端构建产物：`backend/target/`
- 运行期目录：`backend/data/`、`backend/logs/`、`backend/uploads/`
- 前端构建产物：`frontend/unpackage/`
- 临时演示或调试产物

正式部署时请以 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) 为准。
