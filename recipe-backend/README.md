# 菜谱共享小程序（独立新项目）

按照《菜谱小程序设计方案.md》生成的 MVP 骨架，**独立新建项目，不依赖/不修改 nutcards 任何代码**。

## 项目结构

```
wbWorkSpaces/
├── recipe-backend/        # 后端：Spring Boot 3.3.5 + MyBatis-Plus + MySQL + Flyway + JWT
│   └── src/main/
│       ├── java/com/recipe/
│       │   ├── controller/   # Auth / Recipe / Family / FoodCalorie / Fridge / Banner
│       │   ├── entity/       # 13 张表实体
│       │   ├── mapper/       # MyBatis-Plus Mapper
│       │   ├── config/       # JwtUtil / AuthInterceptor / WebConfig / MybatisPlusConfig
│       │   └── common/       # ApiResult / PageResult / BizException / 全局异常
│       └── resources/
│           ├── application.yml
│           └── db/migration/ # V1 建 13 表、V2 热量种子数据
└── recipe-miniprogram/    # 小程序：5 TabBar + 13 页面 + 登录弹层组件
    ├── app.json / app.js / app.wxss / project.config.json
    ├── utils/store.js     # 统一请求层（callContainer / wx.request 双模式）
    ├── components/login-popup/  # 头像昵称填写 + 微信一键登录
    └── pages/             # plaza/publish/family/tools/mine + recipe-detail/search/cook-mode/
                           # circle-detail/circle-join/calorie/fridge/timer
```

## 核心功能（已实现接口）

| 模块 | 接口 | 说明 |
|---|---|---|
| 登录 | POST /api/auth/wxlogin | 微信 code 换 openid + JWT（未配 appid 回退 demo openid） |
| 登录 | GET /api/auth/me | 当前用户信息（需登录） |
| 登录 | POST /api/auth/admin/login | 总管理员（admin/admin123，后台预留） |
| 广场 | GET /api/recipes/public | 公开菜谱分页 + 关键词（游客可用） |
| 菜谱 | GET /api/recipes/{id} | 详情，四级可见性校验（PRIVATE/FAMILY/TEAM/PUBLIC） |
| 菜谱 | GET/POST/PUT/DELETE /api/recipes | 我的菜谱 / 发布 / 编辑 / 删除 |
| 评星 | POST /api/recipes/{id}/rating | 1-5 星，一人一评，事务内重算聚合 |
| 评论 | GET/POST /api/recipes/{id}/comments | 评论 + 楼中楼回复 |
| 热量 | GET /api/food-calories | 食品/调料热量查询（游客可用） |
| 圈层 | POST /api/family | 创建（join_code / 上限默认99 / 审批开关） |
| 圈层 | GET /api/family/mine | 我的圈子 |
| 圈层 | GET /api/family/search?joinCode | 搜 ID |
| 圈层 | POST /api/family/join | 凭 ID 加入（模式B：关审批直接加入） |
| 邀请 | POST /api/family/join-by-invite | 凭邀请 token 加入（校验有效期/作废/满员） |
| 邀请 | POST/GET/DELETE /api/family/{id}/invites | 生成(1/7/30天)/列表/作废 |
| 成员 | GET /api/family/{id}/members | 成员列表（含待审） |
| 成员 | POST .../approve?pass= | 审批通过/拒绝 |
| 成员 | DELETE .../members/{id} | 移出成员 |
| 圈层 | PUT /api/family/{id} | 改名 / 改上限 / 切换审批开关 |
| 冰箱 | GET/POST/PUT/DELETE /api/fridge-items | 临期排序（已过期→快过期→新鲜） |
| 收藏 | POST /api/favorites/toggle/{id} | 收藏/取消切换（返回 favorited） |
| 收藏 | GET /api/favorites/mine · /status/{id} | 我的收藏列表（含作者昵称）· 单条状态（未登录返回 null） |
| 安全 | 发布/评论/昵称 | 内容安全 msgSecCheck/imgSecCheck（配置微信 appid/secret 后生效；未配置自动放行） |
| 上传 | POST /api/upload/presign | 图片直传 COS 预签名（需登录，仅 jpg/png/webp，未配密钥返回提示） |
| Banner | GET /api/banners | 隐藏 Banner（ON + 时间窗内才返回，后台推送） |

### COS 直传（图片上传）

小程序端 `store.uploadImage()` 三步完成（不经后端转发，省容器算力与流量）：
`wx.compressImage` 压缩 → `POST /api/upload/presign` 取预签名 PUT URL → `wx.uploadFile` 直传 → 返回 `fileUrl` 存库。

启用前在 `application.yml` 配置（或云托管环境变量 `RECIPE_COS_SECRET_ID` 等）：
```yaml
recipe:
  cos:
    secret-id: "你的SecretId"
    secret-key: "你的SecretKey"
    bucket: "recipe-images-1250000000"   # 建议开启公有读（图片类）或走 CDN
    region: "ap-shanghai"
```
未配置时上传接口返回「COS 未配置」提示，其余功能不受影响。已接入：发布页封面、步骤图、冰箱拍照备注（`photo_url`）。

## 本地运行

### 1. 后端

```bash
# 1) 建库（Flyway 会自动建表 + 种子数据）
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS recipe DEFAULT CHARACTER SET utf8mb4;"

# 2) 必须用 JDK 21.0.8（Lombok 兼容，同 nutcards 经验）
export JAVA_HOME=<你的 JDK 21 安装路径>

# 3) 本地调试端口 8080（沙箱环境变量会覆盖 SERVER__PORT）
cd recipe-backend
SERVER__PORT=8080 mvn spring-boot:run
```

本地 curl 测试（沙箱有代理，需 `--noproxy '*'`）：
```bash
curl --noproxy '*' -X POST localhost:8080/api/auth/wxlogin -H 'Content-Type: application/json' -d '{"code":"demo1"}'
# 返回 token；demo openid = "demo_demo1"，每次不同 code 不同用户
```

### 2. 小程序

1. 用微信开发者工具导入 `recipe-miniprogram/`，AppID 填自己的（或先体验版）。
2. 本地调试：把 `utils/store.js` 里 `API.USE_CLOUD` 改为 `false`（BASE_URL 默认 `http://localhost:8080`，并在开发者工具勾选「不校验合法域名」）。
3. 真机/体验版：`USE_CLOUD=true`，并把 `API.CLOUD_ENV`、`API.CLOUD_SERVICE_NAME` 换成菜谱项目的云托管环境 ID 与服务名。

### 3. 管理后台（Thymeleaf，入口 `/admin`）

```bash
# 启动后访问 http://localhost:8080/admin
# 账号 admin / admin123（可用环境变量 ADMIN_PASSWORD 覆盖）
```

| 模块 | 功能 |
|---|---|
| 仪表盘 | 菜谱/用户/圈层/评论/待审统计 |
| 菜谱管理 | 搜索、上下架（下架从广场消失）、删除 |
| 用户管理 | 用户列表（昵称/OpenID/注册时间） |
| 圈层管理 | 列表（成员/上限/审批/创建人）、删除 |
| 评论管理 | 列表、删除 |
| 热量库 | 食品/调料 CRUD（名称/分类/热量/单位/备注） |
| 首页 Banner | 新建/编辑/上下架/删除 + 投放时间窗 → 小程序广场自动轮播（ON 且时间窗内才展示） |

## 云托管部署要点

- 后端 Dockerfile（可参考 nutcards）打包 `mvn package` 出的 jar；环境变量注入 `MYSQL_HOST/PORT/USER/PASSWORD` 与 `RECIPE_JWT_SECRET`（强烈建议换掉默认密钥）。
- 小程序走 `wx.cloud.callContainer`，需在云托管服务绑定菜谱项目 AppID，调用头自动带 `X-WX-SERVICE: <服务名>`。
- 管理后台（RuoYi-Vue）为独立后续项，本期已预留 `/api/auth/admin/login` + `ADMIN` 角色拦截。

## 当前状态与下一步

- ✅ 已完成：后端可编译骨架 + 全部核心接口 + **COS 预签名直传**（封面/步骤图/冰箱拍照备注）；小程序 13 页骨架（广场/发布/家庭/工具/我的 + 详情/搜索/照着做/圈子详情/圈子加入/热量/冰箱/倒计时）+ 登录弹层。
- ⏳ 待接入：msgSecCheck/imgSecCheck 内容安全、RuoYi-Vue 后台、云托管真实环境变量（COS/JWT/CLOUD_ENV/服务名）。
- 📌 已确认不做：冰箱**拍照识别**（仅保留拍照备注存图）。
- 冷启动：`GET /api/recipes/public` 无数据时广场为空，可先发布几道 PUBLIC 菜谱或写 V3 种子 SQL。
