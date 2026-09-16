# Recipe · 菜谱共享小程序 + 管理后台

微信小程序（菜谱广场 / 队伍协作 / 冰箱 / 点餐 / 热量查询） + Spring Boot 3.3 后端（MyBatis-Plus + Flyway + JWT + Thymeleaf 管理后台）。

无支付、无电商。图片走腾讯云 COS 直传，后端只签发预签名 URL。

```
recipe/
├── README.md                  本文件（含「运行前必须提供什么」）
├── recipe-backend/            Spring Boot 3.3.5 + MyBatis-Plus + Flyway(V1~V12)
│   ├── Dockerfile
│   └── src/main/resources/
│       ├── application.yml          本地开发（默认值，可直接跑）
│       ├── application-prod.yml     生产（全部走环境变量，缺即失败）
│       └── application-wxcloud.yml  微信云托管（走环境变量 + 回退默认值）
├── recipe-miniprogram/        微信小程序（原生，19 页 + 2 组件）
├── assets/test/               测试数据与种子 SQL
├── 拾味星球交接文档.md          最完整的项目说明（架构/API/页面/迁移/备案）
├── 菜谱小程序设计方案.md
├── recipe-optimization-report.md
└── recipe-miniprogram-UI升级报告.md
```

---

## ⚠️ 运行前必须提供什么

代码里所有真实凭据**已被占位符替换**，直接 clone 下来跑不起来，需要先填下面这些值。

### 一、必须提供（缺了跑不起来）

| 配置 | 作用 | 怎么拿到 | 填在哪 |
|---|---|---|---|
| **MySQL 数据库** | 全部业务数据 | 本地装 MySQL，或用云托管 / 云数据库 | 本地：`recipe-backend/src/main/resources/application.yml` 的 `spring.datasource`；生产/云托管：环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`（prod）或 `MYSQL_ADDRESS` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（wxcloud） |
| **微信小程序 AppID** | `wx.login` 换 openid | 微信公众平台 → 开发管理 → 开发设置 | 小程序：`recipe-miniprogram/project.config.json` 的 `appid`；后端：环境变量 `WX_APPID` |
| **微信小程序 AppSecret** | 同上，服务端换取 openid + 内容安全校验 | 同上页面，点「重置」获取 | 后端环境变量 `WX_SECRET`。**严禁写进代码** |
| **JWT 签名密钥** | 登录令牌签名 | `openssl rand -base64 48` 生成 ≥32 字节随机串 | 环境变量 `RECIPE_JWT_SECRET`（prod 必填，缺失直接启动失败） |
| **后台管理员密码** | `/admin` 后台登录 | 自己设一个强密码 | 环境变量 `ADMIN_PASSWORD`（默认 `admin123`，生产必须改） |

### 二、选配（不配则对应功能降级，其余不受影响）

| 配置 | 不配的后果 | 填在哪 |
|---|---|---|
| **腾讯云 COS**（`COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_BUCKET` / `COS_REGION`） | 图片上传接口返回「COS 未配置」，发布菜谱不能传图 | 环境变量，或 `application.yml` 的 `recipe.cos.*` |
| **云托管环境 ID / 服务名** | 小程序线上调不了后端 | `recipe-miniprogram/utils/store.js` 的 `CLOUD_ENV` / `CLOUD_SERVICE_NAME` |
| **`RECIPE_CORS_ORIGINS`** | 浏览器跨域访问被拦 | 环境变量，逗号分隔 |
| `WX_DEMO_LOGIN` | 控制是否允许 demo 登录回退；**生产必须为 false** | 环境变量 |
| `SECURITY_FAIL_OPEN` | 内容安全接口报错时是放行还是拦截；生产建议 `false` | 环境变量 |

> 占位符约定：代码里出现 `REPLACE_WITH_YOUR_XXX` / `<DB_HOST>` / `<你的 …>` 的地方，都是需要你替换的。

### 三、环境要求

- **JDK 21**（Lombok 1.18.x 在更高版本上会报 `TypeTag :: UNKNOWN`，务必用 21）
- **Maven 3.8+**
- **MySQL 8.x**（utf8mb4）。表结构由 Flyway 自动迁移，**不要手动建表**
- 微信开发者工具（小程序侧）

---

## 快速开始

```bash
# 1) 建库（Flyway 会自动建表 + 灌种子数据）
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS recipe DEFAULT CHARACTER SET utf8mb4;"

# 2) 起后端（默认 8080）
cd recipe-backend
SERVER__PORT=8080 mvn spring-boot:run

# 3) 管理后台：http://localhost:8080/admin   默认 admin / admin123

# 4) 小程序：微信开发者工具导入 recipe-miniprogram/
#    本地联调：utils/store.js 里 USE_CLOUD 改 false（走 http://localhost:8080）
#    生产/体验版：USE_CLOUD 保持 true，并填好 CLOUD_ENV / CLOUD_SERVICE_NAME
```

上线部署用 `recipe-backend/Dockerfile` 打镜像，配合 `application-wxcloud.yml`（微信云托管）或 `application-prod.yml`（自建服务器 + 环境变量注入）。

---

## 关于本仓库的脱敏

上传前已移除的内容：小程序真实 AppID、云托管环境 ID、云数据库地址与密码、本地绝对路径。
管理后台默认口令 `admin123`、本地数据库 `root/root` 属于框架默认值，予以保留以便开箱即跑，**上生产前必须改掉**。
