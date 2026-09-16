# recipe 项目优化体检报告

> 范围：`recipe-backend`（Spring Boot 3 + MyBatis-Plus + Flyway + MySQL）+ `recipe-miniprogram`（微信小程序）
> 目标：在不改功能的前提下，按"安全 → 性能 → 健壮性 → 体验 → 工程化"排查可优化点。
> 结论：**结构清晰、分层合理，安全风险集中在配置与鉴权，性能短板集中在索引与 setData，体验短板集中在登录态失效处理与主题覆盖。**

---

## 优先级总览

| 优先级 | 维度 | 关键项 | 数量 |
|---|---|---|---|
| 🔴 P0（必修·线上风险） | 安全 / 性能 / 体验 | JWT 密钥硬编码、admin 双鉴权硬编码、CORS 通配+凭据、family_member 缺索引、生产配置明文+debug、小程序 401 不失效、错误静默吞、主题闪白 | 8 |
| 🟠 P1（重要） | 安全 / 性能 / 代码 / 健壮性 | 内容安全默认放行、demo 登录、me 泄露、上传校验、N+1、无分页、Map 入参、魔法数字、并发计数、异常覆盖、setData 全量、缓存去重、导航栏配色、lazy-load | 14 |
| 🟡 P2（工程化） | 工程化 | 分包、常量收敛、事件总线、死代码清理、上传类型映射 | 5 |

---

## 🔴 P0 — 必修（安全 / 性能 / 体验底座）

### 后端
| # | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|
| 1 | JWT 密钥硬编码入仓库 | `application.yml:32` + `JwtUtil.java` | 默认密钥已提交，任何人可签发任意用户（含 ADMIN）token，完全身份伪造 | 密钥仅从环境变量注入；占位值启动即报错；加 refresh / 黑名单 |
| 2 | 后台两套 admin 鉴权，`/api/auth/admin/login` 密码硬编码且 env 覆盖无效 | `AuthController.java:74-84` vs `AdminLoginController.java:17` | 知道 admin123 即可获得全局 ADMIN，调所有业务接口 | 删除业务侧 admin 登录，统一走 Session 制；或同样读 `recipe.admin.password` |
| 3 | CORS `allowedOriginPatterns("*")` + `allowCredentials(true)` | `WebConfig.java:28-32` | 等价于任意源带凭据访问，后台无 CSRF 防护可被冒用 | 限定可信域名；关闭 credentials；admin 接口加 CSRF Token / Cookie `SameSite=Strict` |
| 4 | `family_member` 缺 `(user_id, status)` 索引 | `V1__init.sql:45` | 首页 overview / mine 最高频接口全表扫描 | 新增 `KEY idx_user_status(user_id, status)` |
| 5 | 生产配置不安全 | `application.yml` | 明文密钥 + `logging.level debug` 打印用户数据 SQL + 无 HTTPS/CSRF/限流 | 拆分 `application-prod.yml`；敏感配置走环境变量；生产关 debug 与 SQL 日志；网关层限流 |

### 小程序
| # | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|
| 6 | 401/403 不清理登录态、不弹登录层 | `store.js:36-41` / `app.js:16-20` | token 过期后困在"假登录"，每个请求都浪费一次网络 | 401 分支统一 `clearToken()` + 重置 globalData + 全局事件弹 `login-popup` |
| 7 | 大量请求错误被静默吞（`.catch(()=>{})`） | `plaza.js:64` / `family.js:38` / `mine.js:51` 等 | 加载失败白屏无提示，像卡死 | 抽 `utils/ui.js` 统一 `toast.fail`；关键页加"加载失败·点击重试"空态 |
| 8 | 详情/圈子/做菜页根节点 `wx:if` 包裹主题 | `recipe-detail.wxml:1` 等 | 深色等主题进入瞬间闪白/错配 | 始终渲染 `.page.theme-*` 根节点，内部内容再 `wx:if` |

---

## 🟠 P1 — 重要（性能 / 健壮性 / 体验）

### 后端
| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| 9 | 内容安全默认关闭，微信失败即放行 | `WxSecurityService.java:27-108` | 失败降级为"拦截+人工/告警"，生产必须配置真实 appid |
| 10 | 登录 demo 回退 `demo_`+code 可冒用 | `AuthController.java:96-115` | 生产禁用 demo 回退，缺失 appid 返回 503 |
| 11 | `/api/auth/me` 返回完整 User（含 openid/unionid/phone） | `AuthController.java:87-94` | 返回专用 `UserDTO` 剔除敏感字段 |
| 12 | 上传仅校验扩展名，预签名 ContentType 由客户端传入 | `UploadController.java:51-69` | 服务端强约束 ContentType 集合 + COS 回调 MIME 回检 |
| 13 | 多处 N+1（循环内 selectById） | `MealRequestController` / `FavoriteController` / `FamilyController` / `AdminPageController` | 用 `IN(ids)` 批量查询后 Map 装配或写 join |
| 14 | `meal_request.overview` 无分页 | `MealRequestController.java:43-83` | 分页或限制单圈层条数 + 拆分聚合 |
| 15 | Controller 普遍用 `Map` 入参、缺 `@Valid`、`stars` 缺失 → 500 | `RecipeController.java:192` 等 | 定义 DTO + `@Valid`/`@NotNull`/`@Min`/`@Max`，错误码统一 400 |
| 16 | 魔法数字/状态字符串散落，无枚举 | 多处（720h / size=10,20 / ADMIN 等） | 抽 `Role`/`Status` 枚举与 `PageConst` |
| 17 | 圈层权限校验/查询逻辑复制 | `FamilyController` vs `MealRequestController` | 抽 `MembershipService` |
| 18 | 并发计数非原子（member_count 先查后改） | `FamilyController` doJoin/approve/removeMember | 改原子 SQL `UPDATE ... SET member_count=count+1 WHERE ...` |
| 19 | 全局异常覆盖不全（缺参/JSON 解析/404/405） | `GlobalExceptionHandler.java:12-28` | 补充专门处理，统一 400/404/405 |
| 20 | Flyway：无 FK、逻辑删除 `deleted` 全局配置但无列、baseline-on-migrate 生产风险 | `V1/V2/V3`、`application.yml:14,27` | 补 FK 或文档说明；补 `@TableLogic` 列否则移除配置；生产关 baseline |

### 小程序
| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| 21 | 分页列表每次 `setData` 重发整个数组 | `plaza.js:57` / `search.js` / `family.js` | 增量路径赋值 `recipes[${i}]`，只传新分片 |
| 22 | 请求无缓存/去重，切 tab 反复拉 | `store.js` / `mine.js` / `family.js` | `callApi` 加 GET 内存缓存+TTL；mine 列表 30s 内不重复拉 |
| 23 | 主题未同步原生导航栏/胶囊 | `app.json` window 写死浅色 / `app.js:24-27` | `theme.set` 时 `wx.setNavigationBarColor` |
| 24 | 图片缺 `lazy-load` | `recipe-detail` / `fridge` / `publish` 等 | 列表/多图 `<image>` 统一加 `lazy-load` + 固定宽高容器 |
| 25 | 错误 toast 不统一、散落各页 | 多页 | 抽 `utils/ui.js` `toast.fail/success/loading` |
| 26 | 裸 `wx.getStorageSync('recipe_token')` 多处硬编码 | `recipe-detail.js` / `tools.js` 等 | 收敛到 `store.getToken()` / `store.isLogin()` |
| 27 | 登录失败靠字符串 `includes('登录')` 判断 | `fridge.js:43-47` | `callApi` 401 时 reject 带 `err.code='AUTH'`，业务按 code 判断 |
| 28 | 评星/改范围后整页 `loadDetail` 全量重拉 | `recipe-detail.js:95,141` | 点赞/改范围仅局部 setData |

---

## 🟡 P2 — 工程化（可选）

| # | 问题 | 建议 |
|---|---|---|
| 29 | 未分包，13 页全在主包 | 非 tab 页拆 `subpackages` 降低主包体积 |
| 30 | 魔法数字散落（99/1-99/3 天） | 抽 `utils/config.js` 常量 |
| 31 | `globalData` 跨页隐式契约（plazaDirty） | 改 EventChannel / 事件总线 |
| 32 | 死代码（banner 隐藏、`fetchBanners`、过时注释） | 清理 |
| 33 | `uploadImage` 仅 png/jpeg，webp/gif 误判 | 按完整扩展映射 MIME |

---

## 落地节奏建议（与你分步迭代习惯匹配）
1. **第一批（P0 安全/性能底座）**：#1 #2 #3 #4 #5（后端配置与索引）+ #6 #7 #8（小程序登录态与主题）—— 一个 helper 改 `store.js` 即可覆盖 #6/#7/#27 全局。
2. **第二批（P1 性能/健壮性）**：#13 N+1、#15 DTO 校验、#21 setData 增量、#23 导航栏配色、#24 lazy-load。
3. **第三批（P2 工程化）**：#29 分包、#30 常量、#31 事件总线。

> 注：agent 已确认你特别关心的"点餐 done/delete 越权"是安全的（已校验 owner/admin），风险仅在 #2 的 admin 路径可用硬编码密码获取。SQL 注入面良好（全程参数化，无 `${}`）。

---

## ✅ 实施结果（2026-08-19 已完成）

### 已实施并回归通过（后端 `mvn compile` OK，`test-api.py` 30/30 PASS，点餐链路 PASS）
| 项 | 状态 |
|---|---|
| P0 #1–#5 后端安全/配置 | ✅ JWT 环境变量+启动校验、删 admin 硬编码登录、CORS 限定、prod 配置拆分、V4 索引 |
| P0 #6–#8 小程序 | ✅ store.js 401 清理登录态、错误 toast 统一、主题根节点恒渲染 |
| P1 #9–#20 后端 | ✅ 内容安全 fail-closed、demo 开关、me 返回 UserDTO 脱敏、上传 ContentType 强制、N+1 批量、overview 限 50 条、并发计数原子化、异常处理补全、Constants 枚举收敛 |
| P1 #21–#28 小程序 | ✅ setData 增量、cachedGet 缓存、导航栏配色、lazy-load、token 收敛、AUTH 错误码、点赞局部刷新 |
| P2 #30/#31/#33 | ✅ config.js 常量、app.js 事件总线、上传 MIME 完整映射 |

### 有意保留（低收益 / 高风险，说明）
| 项 | 原因 |
|---|---|
| #29 分包 subpackages | 13 页体积小 + `lazyCodeLoading` 已启用；改路径需真机验证跳转，建议后续单独做 |
| AdminPageController N+1 | 后台低频管理页，收益极低 |
| plazaDirty 未迁事件总线 | 工作正常，`bus` 已就绪备用 |
| banner / fetchBanners | 是完整功能（后台 CRUD 可用），非死代码 |

### 关键验证结论
- 后端 `mvn -o compile` EXIT=0；`test-api.py` 30/30；点餐 overview 批量/群主 done/越权 403 均正常。
- `/api/auth/me` 已脱敏（返回字段仅 avatar/createdAt/id/nickname）。
- `/api/auth/admin/login` 已移除，后台统一走 Session 制（`/admin/login`）。
- 小程序 JS 语法全绿，WXML view/block 标签平衡。
- V4 迁移（`family_member` 加 `(user_id,status)` 索引）已在本地成功应用。
