package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.config.AuthInterceptor;
import com.recipe.config.JwtUtil;
import com.recipe.entity.User;
import com.recipe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 登录：wxlogin（微信一键登录，未配置 appid 时回退 demo openid）。
 * 后台管理员登录走独立 Session 制（AdminLoginController），本类不再提供 admin/login。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final com.recipe.service.WxSecurityService wxSecurity;

    @Value("${recipe.wx.appid:}")
    private String wxAppid;

    @Value("${recipe.wx.secret:}")
    private String wxSecret;

    @Value("${recipe.wx.demo-login-enabled:true}")
    private boolean demoLoginEnabled;

    @PostMapping("/wxlogin")
    public ApiResult<Map<String, Object>> wxlogin(@RequestBody Map<String, String> body,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        // 优先取云托管 callContainer 注入的 X-WX-OPENID（微信官方注入，免公网、免 secret）
        // 无该头时（本地直连联调）回退 jscode2session 用 code 换 openid
        String openid = request.getHeader("X-WX-OPENID");
        if (openid == null || openid.isBlank()) {
            String code = body.get("code");
            if (code == null || code.isBlank()) {
                return ApiResult.err(400, "code 不能为空");
            }
            openid = exchangeOpenid(code);
        }
        if (openid == null) {
            return ApiResult.err(401, "微信登录失败（无法获取 openid，详见服务日志）");
        }
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");
        // 内容安全：昵称违规则忽略昵称（保留默认），不阻塞登录
        if (nickname != null && wxSecurity.checkText(nickname, openid)) {
            nickname = null;
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            userMapper.insert(user);
        } else if (nickname != null && !nickname.isBlank() && !nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
            user.setAvatar(avatar);
            userMapper.updateById(user);
        }

        String token = jwtUtil.createToken(user.getId(), "USER");
        // Map.of 不允许 null 值，nickname/avatar 可能为空，改用 HashMap
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("uid", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        // isNew=true 表示新用户（或资料未完善）：小程序端先静默登录，仅新用户才弹头像昵称收集，再走 /profile 完善资料
        result.put("isNew", user.getNickname() == null || user.getNickname().isBlank());
        return ApiResult.ok(result);
    }

    /** 当前登录用户信息（需登录，仅暴露安全字段，剔除 openid/unionid/phone） */
    @GetMapping("/me")
    public ApiResult<com.recipe.dto.UserDTO> me(jakarta.servlet.http.HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        if (uid == null) {
            return ApiResult.err(401, "未登录");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            return ApiResult.err(404, "用户不存在");
        }
        return ApiResult.ok(com.recipe.dto.UserDTO.of(user));
    }

    /** 更新个人资料（昵称/头像，可只传其一；昵称做内容安全校验） */
    @PutMapping("/profile")
    public ApiResult<com.recipe.dto.UserDTO> updateProfile(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        if (uid == null) {
            return ApiResult.err(401, "未登录");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            return ApiResult.err(404, "用户不存在");
        }
        boolean changed = false;
        if (body.containsKey("nickname")) {
            String nickname = body.get("nickname");
            if (nickname == null || nickname.isBlank()) {
                return ApiResult.err("昵称不能为空");
            }
            String nick = nickname.trim();
            if (nick.length() > 32) {
                return ApiResult.err("昵称过长（最多 32 字）");
            }
            if (wxSecurity.checkText(nick, request.getHeader("X-WX-OPENID"))) {
                return ApiResult.err("昵称包含违规内容");
            }
            user.setNickname(nick);
            changed = true;
        }
        if (body.containsKey("avatar")) {
            user.setAvatar(body.get("avatar"));
            changed = true;
        }
        if (body.containsKey("profileVisibility")) {
            String v = body.get("profileVisibility");
            if (!"PUBLIC".equals(v) && !"PRIVATE".equals(v) && !"FAMILY".equals(v)) {
                return ApiResult.err("主页可见性取值不合法");
            }
            user.setProfileVisibility(v);
            changed = true;
        }
        if (!changed) {
            return ApiResult.err("无可更新内容");
        }
        userMapper.updateById(user);
        return ApiResult.ok(com.recipe.dto.UserDTO.of(user));
    }

    /** 带超时的微信接口客户端，避免微信慢响应卡住登录线程 */
    private RestClient wxClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 用 code 换 openid；未配置 appid 且未禁用 demo 时回退 demo openid（便于本地联调） */
    private String exchangeOpenid(String code) {
        if (wxAppid == null || wxAppid.isBlank() || wxAppid.startsWith("wx000")) {
            if (!demoLoginEnabled) {
                return null;
            }
            return "demo_" + code;
        }
        try {
            RestClient client = wxClient();
            Map<?, ?> resp = client.get()
                    .uri("https://api.weixin.qq.com/sns/jscode2session?appid={a}&secret={s}&js_code={c}&grant_type=authorization_code",
                            wxAppid, wxSecret, code)
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("openid") != null) {
                return (String) resp.get("openid");
            }
            // 微信侧拒绝：40125 secret 错误 / 40029 code 无效或 appid 不匹配 / 40163 code 已使用
            log.warn("jscode2session 未返回 openid: errcode={}, errmsg={}", resp.get("errcode"), resp.get("errmsg"));
        } catch (Exception e) {
            // 容器无公网访问时这里通常是 connect timeout
            log.warn("jscode2session 调用异常（云托管未开公网访问时常见）: {}", e.getMessage());
        }
        return null;
    }
}
