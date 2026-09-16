package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.entity.Banner;
import com.recipe.mapper.BannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 首页隐藏 Banner（公开只读；仅返回 ON + 投放时间窗内，无数据返回空数组） */
@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerMapper bannerMapper;

    @GetMapping
    public ApiResult<List<Banner>> list() {
        LocalDateTime now = LocalDateTime.now();
        List<Banner> banners = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, "ON")
                .and(w -> w.isNull(Banner::getStartAt).or().le(Banner::getStartAt, now))
                .and(w -> w.isNull(Banner::getEndAt).or().ge(Banner::getEndAt, now))
                .orderByAsc(Banner::getSort));
        return ApiResult.ok(banners);
    }
}
