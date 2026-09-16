package com.recipe.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台管理鉴权：/admin/** 需 session 含 admin 标志（登录页本身放行）。
 * 与小程序端 JWT 鉴权（AuthInterceptor）互不影响，后台走独立登录会话。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "recipe_admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }
        // AJAX 请求返回 401 JSON，页面请求重定向到登录页
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或会话已过期\"}");
            return false;
        }
        response.sendRedirect("/admin/login");
        return false;
    }
}
