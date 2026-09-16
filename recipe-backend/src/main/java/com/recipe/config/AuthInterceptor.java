package com.recipe.config;

import com.recipe.common.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 鉴权拦截器：
 * - 完全免鉴权路径（permit-all）：/api/auth/**、/api/banners、/api/food-calories/**、/api/recipes/public/**、/error
 * - 公开只读 GET（游客可调）：banners、food-calories、recipes/public 等
 * - 白名单路径也尽力解析 JWT（有合法 token 就写入 uid/role）：已登录用户访问公开接口时，
 *   Controller 需要 uid 判断关注态（如用户主页 profile 接口的 followed）和隐私可见性
 * - 其余请求必须携带有效 JWT
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_UID = "authUid";
    public static final String ATTR_ROLE = "authRole";

    private final JwtUtil jwtUtil;

    /** 完全免鉴权（含写操作也放行，如登录） */
    private static final List<String> PERMIT_ALL = List.of(
            "/api/auth/wxlogin",
            "/api/banners",
            "/api/food-calories/**",
            "/api/recipes/public/**",
            // 用户主页：公开主页游客可看（Controller 内自行判空 uid 与隐私校验）
            "/api/users/*/profile",
            "/api/users/*/recipes",
            // 关注状态批量查询：游客返回空映射
            "/api/follows/status",
            "/error"
    );

    /** 公开只读 GET：游客可调 */
    private static final Set<String> PUBLIC_GET = Set.of(
            "/api/banners",
            "/api/food-calories",
            "/api/recipes/public"
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        boolean permit = false;
        for (String p : PERMIT_ALL) {
            if (matcher.match(p, uri)) {
                permit = true;
                break;
            }
        }
        // 公开只读 GET 放行
        if (!permit && HttpMethod.GET.matches(method) && PUBLIC_GET.contains(uri)) {
            permit = true;
        }

        // 尽力解析 JWT：合法 token 一律写入 uid/role（token 无效或缺失则保持游客身份）
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(auth.substring(7));
            if (claims != null) {
                request.setAttribute(ATTR_UID, Long.valueOf(claims.getSubject()));
                request.setAttribute(ATTR_ROLE, claims.get("role", String.class));
            }
        }

        if (permit || request.getAttribute(ATTR_UID) != null) {
            return true;
        }
        throw new BizException(401, "未登录或登录已过期");
    }

    public static Long uid(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_UID);
        return v == null ? null : (Long) v;
    }

    public static String role(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_ROLE);
        return v == null ? null : (String) v;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return com.recipe.common.Constants.ROLE_ADMIN.equals(role(request));
    }
}
