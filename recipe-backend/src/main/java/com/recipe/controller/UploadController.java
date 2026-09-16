package com.recipe.controller;

import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片直传 COS：后端返回预签名 PUT URL，小程序 wx.uploadFile 直传，
 * 不经后端转发（省容器算力与公网流量）。需登录。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final long PRESIGN_EXPIRE_MS = 15 * 60 * 1000L;
    private static final java.util.Set<String> ALLOWED_EXT =
            java.util.Set.of("jpg", "jpeg", "png", "webp");

    @Value("${recipe.cos.secret-id:}")
    private String secretId;

    @Value("${recipe.cos.secret-key:}")
    private String secretKey;

    @Value("${recipe.cos.bucket:}")
    private String bucket;

    @Value("${recipe.cos.region:}")
    private String region;

    /** 懒加载单例（COSClient 线程安全；密钥不可变，避免每请求新建/销毁的开销） */
    private volatile COSClient cosClient;

    private COSClient client() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    // 强制 HTTPS：wx.uploadFile 仅接受 https 域名（SDK 默认协议不保证）
                    ClientConfig cfg = new ClientConfig(new Region(region));
                    cfg.setHttpProtocol(com.qcloud.cos.http.HttpProtocol.https);
                    cosClient = new COSClient(new BasicCOSCredentials(secretId, secretKey), cfg);
                }
            }
        }
        return cosClient;
    }

    @PostMapping("/presign")
    public ApiResult<Map<String, Object>> presign(@RequestBody Map<String, Object> body) {
        if (secretId == null || secretId.isBlank() || bucket == null || bucket.isBlank() || region == null || region.isBlank()) {
            throw new BizException(503, "COS 未配置，请联系管理员在 application.yml 配置腾讯云密钥");
        }
        String fileName = String.valueOf(body.getOrDefault("fileName", ""));
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 jpg / png / webp 图片");
        }

        // 按日期分目录，避免单目录文件过多
        LocalDate today = LocalDate.now();
        String key = "recipe/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // 微信 wx.uploadFile 仅支持 POST，故预签名须用 POST（表单上传），不能用 PUT（PUT 会被签名校验拒绝）
        // 注：POST 表单上传不绑定 Content-Type（wx.uploadFile 固定 multipart/form-data）；类型约束靠扩展名白名单 + 随机 key
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.POST);
        req.setExpiration(new Date(System.currentTimeMillis() + PRESIGN_EXPIRE_MS));
        URL url = client().generatePresignedUrl(req);
        String fileUrl = "https://" + bucket + ".cos." + region + ".myqcloud.com/" + key;
        Map<String, Object> result = new HashMap<>();
        result.put("uploadUrl", url.toString());
        result.put("fileUrl", fileUrl);
        result.put("key", key);
        return ApiResult.ok(result);
    }
}
