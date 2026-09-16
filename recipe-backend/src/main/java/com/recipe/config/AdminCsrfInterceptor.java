package com.recipe.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;

/**
 * 后台管理 CSRF 防护：/admin/** 的写请求（POST/PUT/DELETE）校验 Origin/Referer 与 Host 同源。
 * 覆盖登录 POST 与登录后的删除等操作；浏览器同源表单/fetch 均会携带 Origin（或 Referer 兜底），
 * 跨站伪造请求的 Origin 与目标 Host 不一致即拒绝。缺失时按拒绝处理（fail-closed）。
 */
@Component
public class AdminCsrfInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) {
            source = request.getHeader("Referer");
        }
        boolean sameOrigin = false;
        if (source != null && !source.isBlank()) {
            try {
                URI uri = URI.create(source);
                String host = uri.getHost();
                if (host != null) {
                    String authority = uri.getPort() == -1 ? host : host + ":" + uri.getPort();
                    sameOrigin = authority.equalsIgnoreCase(request.getHeader("Host"));
                }
            } catch (IllegalArgumentException ignored) {
                // 解析失败的来源按不同源处理
            }
        }
        if (!sameOrigin) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"跨站请求被拒绝\"}");
            return false;
        }
        return true;
    }
}
