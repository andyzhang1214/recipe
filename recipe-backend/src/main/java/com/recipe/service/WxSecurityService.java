package com.recipe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 微信内容安全：msgSecCheck（文本）/ imgSecCheck（图片）
 * 走微信云托管「开放接口服务」：容器内以 HTTP 协议调用 api.weixin.qq.com，平台自动注入鉴权
 * （免 access_token、免公网出口、免 appid/secret）。需在控制台-云调用开启开关并重建服务版本。
 * openid 是 msg_sec_check v2 必填参数，取云托管注入的 X-WX-OPENID 请求头；
 * 无 openid（本地直连联调/管理后台来源）时跳过检查。
 */
@Service
public class WxSecurityService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WxSecurityService.class);

    /**
     * 内容安全：微信接口调用失败时是否放行（true=放行，false=拦截）
     * 默认放行：接口出错（开放接口服务未开启/网络异常）不应阻断所有发布；
     * 微信明确判定违规（87014 / suggest=risky）时无论该值如何都拦截。
     */
    @Value("${recipe.security.fail-open:true}")
    private boolean failOpen;

    /** 带超时的微信接口客户端 */
    private RestClient client() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 文本检查。违规返回 true；openid 缺失（本地联调/后台）跳过。
     * version=2 返回 result.suggest：pass / review / risky
     */
    public boolean checkText(String content, String openid) {
        if (content == null || content.isBlank() || openid == null || openid.isBlank()) return false;
        try {
            Map<?, ?> resp = client().post()
                    .uri("http://api.weixin.qq.com/wxa/msg_sec_check")
                    .body(Map.of("content", content.length() > 2000 ? content.substring(0, 2000) : content,
                            "version", 2, "scene", 2, "openid", openid))
                    .retrieve().body(Map.class);
            return interpret("msg_sec_check", resp);
        } catch (Exception e) {
            // 公网未开且「开放接口服务」未开启时，这里通常是 connect timeout
            log.warn("msg_sec_check 调用异常（请确认已开启开放接口服务并重建版本）: {}", e.getMessage());
            return !failOpen;
        }
    }

    /**
     * 图片检查（imgSecCheck：媒体文件 URL，jpg/png 5M 内）。
     * 违规返回 true；openid 缺失时跳过。
     */
    public boolean checkImage(String mediaUrl, String openid) {
        if (mediaUrl == null || mediaUrl.isBlank() || openid == null || openid.isBlank()) return false;
        try {
            Map<?, ?> resp = client().post()
                    .uri("http://api.weixin.qq.com/wxa/img_sec_check")
                    .body(Map.of("media_url", mediaUrl, "media_type", 2, "version", 2, "scene", 2, "openid", openid))
                    .retrieve().body(Map.class);
            return interpret("img_sec_check", resp);
        } catch (Exception e) {
            log.warn("img_sec_check 调用异常（请确认已开启开放接口服务并重建版本）: {}", e.getMessage());
            return !failOpen;
        }
    }

    /** 批量文本检查，任一违规返回 true */
    public boolean checkTexts(List<String> contents, String openid) {
        if (contents == null) return false;
        for (String c : contents) {
            if (checkText(c, openid)) return true;
        }
        return false;
    }

    /** version=2 统一应答解析：87014 命中违规；result.suggest=risky 违规；其他错误按 fail-open 配置 */
    private boolean interpret(String api, Map<?, ?> resp) {
        if (resp == null) return !failOpen;
        Object errcode = resp.get("errcode");
        if (errcode != null && ((Number) errcode).intValue() != 0) {
            int code = ((Number) errcode).intValue();
            if (code == 87014) return true; // 命中违规
            // 40001/41001 等：请求未走开放接口服务链路（开关未开启或版本未重建）
            log.warn("{} errcode={}, errmsg={}", api, code, resp.get("errmsg"));
            return !failOpen;
        }
        Object result = resp.get("result");
        if (result instanceof Map<?, ?> r) {
            return "risky".equalsIgnoreCase(String.valueOf(r.get("suggest")));
        }
        return false;
    }
}
