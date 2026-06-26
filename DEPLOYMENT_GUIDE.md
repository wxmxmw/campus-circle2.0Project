# 项目部署指南

本文档面向 4 位组员，目标是把各自的 `Java 后端 + uni-app H5 前端` 部署到同一台服务器，并通过下面这些地址访问：

- `http://121.199.30.215/ccy/`
- `http://121.199.30.215/cy/`
- `http://121.199.30.215/lqy/`
- `http://121.199.30.215/wxm/`

当前服务器底座已经准备完成：

- JDK 17 已安装
- Nginx 已安装并运行
- MySQL 已安装并运行
- 4 个 Redis 实例已运行
- 4 个项目目录已创建
- 4 个后端 systemd 服务模板已创建
- Nginx 已配置好路径转发和前端静态目录

## 一、每位组员的固定资源

### `ccy`

- 前端访问地址：`http://121.199.30.215/ccy/`
- 后端内部端口：`18081`
- Redis 端口：`6380`
- 数据库：`campus_circle_ccy_db`
- 数据库用户名：`ccy`
- 部署目录：`/srv/apps/ccy/`
- 服务名：`campus-ccy`

### `cy`

- 前端访问地址：`http://121.199.30.215/cy/`
- 后端内部端口：`18082`
- Redis 端口：`6381`
- 数据库：`campus_circle_cy_db`
- 数据库用户名：`cy`
- 部署目录：`/srv/apps/cy/`
- 服务名：`campus-cy`

### `lqy`

- 前端访问地址：`http://121.199.30.215/lqy/`
- 后端内部端口：`18083`
- Redis 端口：`6382`
- 数据库：`campus_circle_lqy_db`
- 数据库用户名：`lqy`
- 部署目录：`/srv/apps/lqy/`
- 服务名：`campus-lqy`

### `wxm`

- 前端访问地址：`http://121.199.30.215/wxm/`
- 后端内部端口：`18084`
- Redis 端口：`6383`
- 数据库：`campus_circle_wxm_db`
- 数据库用户名：`wxm`
- 部署目录：`/srv/apps/wxm/`
- 服务名：`campus-wxm`

## 二、每位组员需要准备什么

每位组员需要准备两份产物：

1. 后端可运行 jar
2. 前端 H5 构建产物 `dist`

如果项目数据库里已经有历史帖子图片、头像等上传文件，还需要额外准备：

3. 历史上传目录 `backend/uploads`

## 三、后端打包

在自己本地项目的 `backend/` 目录执行：

```powershell
mvn clean package -DskipTests
```

成功后会得到类似文件：

```text
target/xxx.jar
```

部署到服务器时，统一重命名为：

```text
app.jar
```

## 四、前端打包

前端必须按各自路径构建，否则静态资源路径会错。

### 路径要求

- `ccy` 前端必须适配 `/ccy/`
- `cy` 前端必须适配 `/cy/`
- `lqy` 前端必须适配 `/lqy/`
- `wxm` 前端必须适配 `/wxm/`

也就是说，前端构建时需要把基础路径配置成自己的部署路径。

如果你们当前前端还没有做 H5 子路径适配，需要先改前端构建配置，再导出 H5。

最终每位组员需要拿到自己的 H5 构建目录：

```text
dist/
```

## 五、上传到服务器

以下命令中的本地路径请替换成你自己的真实路径。

### 很重要：不要漏传历史上传文件

如果数据库里已经存在历史头像、帖子图片等记录，只上传 `jar + dist` 还不够，还必须把本地历史上传目录一起同步到服务器。

否则会出现：

- 旧头像不显示
- 旧帖子图片不显示
- 新上传的图片正常，但历史图片 404

原因是数据库里保存的图片 URL 仍然有效，但服务器上缺少对应文件。

历史上传目录通常是：

```text
backend/uploads/
```

### `ccy`

上传后端：

```powershell
scp D:\your-project\backend\target\your-backend.jar root@121.199.30.215:/srv/apps/ccy/backend/app.jar
```

上传前端：

```powershell
scp -r D:\your-project\frontend\dist\* root@121.199.30.215:/srv/apps/ccy/frontend/dist/
```

上传历史图片/头像：

```powershell
scp -r D:\your-project\backend\uploads\* root@121.199.30.215:/srv/apps/ccy/uploads/
```

### `cy`

```powershell
scp D:\your-project\backend\target\your-backend.jar root@121.199.30.215:/srv/apps/cy/backend/app.jar
scp -r D:\your-project\frontend\dist\* root@121.199.30.215:/srv/apps/cy/frontend/dist/
scp -r D:\your-project\backend\uploads\* root@121.199.30.215:/srv/apps/cy/uploads/
```

### `lqy`

```powershell
scp D:\your-project\backend\target\your-backend.jar root@121.199.30.215:/srv/apps/lqy/backend/app.jar
scp -r D:\your-project\frontend\dist\* root@121.199.30.215:/srv/apps/lqy/frontend/dist/
scp -r D:\your-project\backend\uploads\* root@121.199.30.215:/srv/apps/lqy/uploads/
```

### `wxm`

```powershell
scp D:\your-project\backend\target\your-backend.jar root@121.199.30.215:/srv/apps/wxm/backend/app.jar
scp -r D:\your-project\frontend\dist\* root@121.199.30.215:/srv/apps/wxm/frontend/dist/
scp -r D:\your-project\backend\uploads\* root@121.199.30.215:/srv/apps/wxm/uploads/
```

## 六、检查和修改服务器上的环境变量

每个项目都有自己的环境变量文件：

- `/srv/apps/ccy/config/app.env`
- `/srv/apps/cy/config/app.env`
- `/srv/apps/lqy/config/app.env`
- `/srv/apps/wxm/config/app.env`

每位组员至少要确认这些值：

- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `APP_REDIS_ENABLED`
- 如果项目用了第三方 API Key，也要补进去

例如修改 `ccy`：

```bash
vim /srv/apps/ccy/config/app.env
```

需要重点检查：

```env
SERVER_PORT=18081
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/campus_circle_ccy_db?...
SPRING_DATASOURCE_USERNAME=ccy
SPRING_DATASOURCE_PASSWORD=这里改成真实数据库密码
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6380
SPRING_DATA_REDIS_PASSWORD=
UPLOAD_PATH=/srv/apps/ccy/uploads
LOGGING_FILE_NAME=/srv/apps/ccy/logs/campus-backend.log
JWT_SECRET=这里改成自己的随机密钥
SERVER_URL=http://121.199.30.215/ccy
APP_REDIS_ENABLED=true
```

建议 `JWT_SECRET` 用随机字符串，生成方式：

```bash
openssl rand -base64 32
```

### 如果项目用了 AI，必须补齐模型配置

本项目治理监测、AI 发帖辅助等能力依赖 OpenAI-compatible API。服务器部署时如果漏掉模型配置，或者模型名在当前中转渠道不可用，系统会降级运行，可能出现：

- 执行监测时 AI 实际未成功参与
- 很快生成重复度较高的治理事件
- 日志里出现 `model_not_found` 或 `governance ai request failed`

建议至少补齐下面这些环境变量：

```env
LLM_API_KEY=你的中转 token
LLM_BASE_URL=https://ai.dianhuomao.shop/v1
LLM_MODEL=【🥝-C】gemini-2.5-pro（6）
OPENAI_API_KEY=你的中转 token
OPENAI_BASE_URL=https://ai.dianhuomao.shop/v1
OPENAI_MODEL=【🥝-C】gemini-2.5-pro（6）
LLM_CONNECT_TIMEOUT_SECONDS=15
LLM_REQUEST_TIMEOUT_SECONDS=60
```

说明：

- `API key` 正确不代表模型名一定可用
- 如果日志提示 `model_not_found`，优先更换 `LLM_MODEL` / `OPENAI_MODEL`
- 不要默认使用旧文档中的 `gpt-4.1-mini`，应以当前中转平台实际可用模型为准

如果要检查 AI 是否真的成功调用，可查看：

```bash
tail -n 200 /srv/apps/<your-id>/logs/campus-backend.log
```

重点搜索这些关键词：

- `governance ai request failed`
- `model_not_found`
- `governance ai degraded`

## 七、启用并启动后端服务

### `ccy`

```bash
systemctl enable campus-ccy
systemctl restart campus-ccy
systemctl status campus-ccy --no-pager
```

### `cy`

```bash
systemctl enable campus-cy
systemctl restart campus-cy
systemctl status campus-cy --no-pager
```

### `lqy`

```bash
systemctl enable campus-lqy
systemctl restart campus-lqy
systemctl status campus-lqy --no-pager
```

### `wxm`

```bash
systemctl enable campus-wxm
systemctl restart campus-wxm
systemctl status campus-wxm --no-pager
```

如果启动失败，查看日志：

### `ccy`

```bash
journalctl -u campus-ccy --no-pager -n 100
tail -n 100 /srv/apps/ccy/logs/campus-backend.log
```

其他项目同理，把名称和路径换掉。

## 八、验证部署结果

### 前端访问

浏览器访问：

- `http://121.199.30.215/ccy/`
- `http://121.199.30.215/cy/`
- `http://121.199.30.215/lqy/`
- `http://121.199.30.215/wxm/`

### 后端接口验证

可以分别访问：

- `http://121.199.30.215/ccy/api/actuator/health`
- `http://121.199.30.215/cy/api/actuator/health`
- `http://121.199.30.215/lqy/api/actuator/health`
- `http://121.199.30.215/wxm/api/actuator/health`

如果返回健康状态，说明：

- Nginx 转发正常
- 后端服务正常
- 项目自己的端口配置正常

## 九、常见问题

### 1. 前端页面能打开，但样式/图片/JS 404

通常是前端没有按 `/ccy/`、`/cy/`、`/lqy/`、`/wxm/` 这些子路径构建。

### 2. 前端页面能打开，但接口报 404

检查前端请求地址是不是仍然写成了 `/api`，以及当前项目是否已经适配自己的部署路径。

### 3. 后端启动失败

重点检查：

- `app.jar` 是否上传到正确路径
- `app.env` 里的数据库密码是否正确
- `JWT_SECRET` 是否填写
- 项目是否依赖额外环境变量

### 4. 图片上传后打不开

重点检查：

- `UPLOAD_PATH` 是否正确
- 后端是否已经配置上传文件访问映射
- Nginx 是否需要补静态文件代理

### 5. 新上传图片正常，但旧头像/旧帖子图片不显示

这通常不是前端问题，而是部署时漏传了历史上传目录。

重点检查：

- 是否把本地 `backend/uploads/` 同步到了服务器 `/srv/apps/<your-id>/uploads/`
- 数据库中的旧图片 URL 是否仍然指向 `/api/uploads/...`
- 服务器上对应文件是否真实存在

如果数据库里有历史数据，重新部署时请始终同步 `uploads/`。

### 6. 点击执行监测后很快出现很多重复事件，而且 AI 额度看起来没变化

重点检查：

- `LLM_MODEL` / `OPENAI_MODEL` 是否是当前中转平台真实可用的模型名
- 日志里是否出现 `model_not_found`
- 日志里是否出现 `governance ai request failed`

这类情况通常不是功能没接 AI，而是 AI 请求被中转平台拒绝后，系统走了降级逻辑。

## 十、当前目录结构

```text
/srv/apps/
  ccy/
    backend/
      app.jar
    frontend/
      dist/
    uploads/
    logs/
    config/
      app.env
  cy/
  lqy/
  wxm/
```

## 十一、建议的部署顺序

每位组员按这个顺序做：

1. 本地打包后端 jar
2. 本地构建前端 H5 `dist`
3. 如果已有历史数据，上传 `backend/uploads`
4. 上传后端 jar 到服务器
5. 上传前端 `dist` 到服务器
6. 检查并修改自己的 `app.env`，尤其是数据库、Redis、JWT、AI 模型配置
7. 启动自己的后端 service
8. 浏览器验证页面
9. 验证 `/api/actuator/health`
