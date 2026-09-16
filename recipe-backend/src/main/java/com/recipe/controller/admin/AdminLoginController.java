package com.recipe.controller.admin;

import com.recipe.config.AdminInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 后台登录（管理员账号 admin/admin123，可通过环境变量 ADMIN_PASSWORD 覆盖）
 * 带 CSRF 防护（AdminCsrfInterceptor）与登录失败限速：同 IP 连续失败 5 次锁定 15 分钟。
 */
@Controller
@RequestMapping("/admin")
public class AdminLoginController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminLoginController.class);

    @Value("${recipe.admin.password:admin123}")
    private String adminPassword;

    /** 同 IP 连续失败 5 次锁定 15 分钟（内存计数，重启清零；防爆破足够） */
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 15 * 60 * 1000L;

    private record FailRecord(int count, long lockUntil) {}

    private final java.util.Map<String, FailRecord> failRecords = new java.util.concurrent.ConcurrentHashMap<>();

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (Boolean.TRUE.equals(session.getAttribute(AdminInterceptor.SESSION_KEY))) {
            return "redirect:/admin";
        }
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model,
                        jakarta.servlet.http.HttpServletRequest request) {
        String ip = clientIp(request);
        FailRecord record = failRecords.get(ip);
        long now = System.currentTimeMillis();
        if (record != null && record.lockUntil() > now) {
            long remainMin = (record.lockUntil() - now) / 60000 + 1;
            model.addAttribute("error", "失败次数过多，请 " + remainMin + " 分钟后再试");
            return "admin/login";
        }
        if ("admin".equals(username) && adminPassword.equals(password)) {
            failRecords.remove(ip);
            session.setAttribute(AdminInterceptor.SESSION_KEY, true);
            return "redirect:/admin";
        }
        int fails = record == null ? 1 : record.count() + 1;
        failRecords.put(ip, new FailRecord(fails, fails >= MAX_FAILS ? now + LOCK_MS : 0));
        if (fails >= MAX_FAILS) {
            log.warn("后台登录 IP={} 连续失败 {} 次，锁定 15 分钟", ip, fails);
        }
        model.addAttribute("error", fails >= MAX_FAILS ? "失败次数过多，账号已锁定 15 分钟" : "账号或密码错误");
        return "admin/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

    /** 云托管网关会传 X-Forwarded-For；本地直连用 remoteAddr */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
