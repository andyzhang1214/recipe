package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.entity.FoodCalorie;
import com.recipe.mapper.FoodCalorieMapper;
import com.recipe.service.FoodCalorieMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 热量查询（公开只读，游客可调） */
@RestController
@RequestMapping("/api/food-calories")
@RequiredArgsConstructor
public class FoodCalorieController {

    private final FoodCalorieMapper foodCalorieMapper;
    private final FoodCalorieMatchService matchService;

    @GetMapping
    public ApiResult<List<FoodCalorie>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String category) {
        LambdaQueryWrapper<FoodCalorie> qw = new LambdaQueryWrapper<FoodCalorie>()
                .like(keyword != null && !keyword.isBlank(), FoodCalorie::getName, keyword)
                .eq(category != null && !category.isBlank(), FoodCalorie::getCategory, category)
                .orderByAsc(FoodCalorie::getId)
                .last("LIMIT 50");
        return ApiResult.ok(foodCalorieMapper.selectList(qw));
    }

    /**
     * 发布页食材联想：名称/别名包含关键字，按热量条目去重（含别名命中，如"西红柿"→番茄）。
     * 返回 [{foodCalorieId, matchedName, caloriesPer100}]
     */
    @GetMapping("/search")
    public ApiResult<List<FoodCalorieMatchService.MatchedFood>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        if (limit <= 0 || limit > 20) {
            limit = 10;
        }
        return ApiResult.ok(matchService.search(keyword, limit));
    }
}
