package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Favorite;
import com.recipe.entity.Recipe;
import com.recipe.entity.User;
import com.recipe.mapper.FavoriteMapper;
import com.recipe.mapper.RecipeMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏：切换收藏/取消、我的收藏列表、单条收藏状态（详情页用）
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteMapper favoriteMapper;
    private final RecipeMapper recipeMapper;
    private final UserMapper userMapper;

    /** 收藏/取消切换，返回是否已收藏 */
    @PostMapping("/toggle/{recipeId}")
    public ApiResult<Map<String, Object>> toggle(@PathVariable Long recipeId, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new BizException(404, "菜谱不存在");
        }
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getRecipeId, recipeId));
        boolean favorited;
        if (existing == null) {
            Favorite f = new Favorite();
            f.setUserId(uid);
            f.setRecipeId(recipeId);
            favoriteMapper.insert(f);
            favorited = true;
        } else {
            favoriteMapper.deleteById(existing.getId());
            favorited = false;
        }
        return ApiResult.ok(Map.of("favorited", favorited));
    }

    /** 我的收藏列表（仅返回仍存在且可见的菜谱；含作者昵称） */
    @GetMapping("/mine")
    public ApiResult<List<Map<String, Object>>> mine(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).orderByDesc(Favorite::getId));
        if (favorites.isEmpty()) {
            return ApiResult.ok(new java.util.ArrayList<>());
        }
        // 批量查菜谱与作者（消除 N+1）
        List<Long> recipeIds = favorites.stream().map(Favorite::getRecipeId).distinct().toList();
        Map<Long, Recipe> recipeMap = recipeMapper.selectBatchIds(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, r -> r));
        List<Long> authorIds = recipeMap.values().stream().map(Recipe::getAuthorId).distinct().toList();
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Favorite f : favorites) {
            Recipe r = recipeMap.get(f.getRecipeId());
            if (r == null) continue;
            User author = userMap.get(r.getAuthorId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", r.getId());
            item.put("title", r.getTitle());
            item.put("cover", r.getCover());
            item.put("visibility", r.getVisibility());
            item.put("avgStars", r.getAvgStars());
            item.put("ratingCount", r.getRatingCount());
            item.put("author", author == null ? null : author.getNickname());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** 详情页：当前用户是否已收藏（未登录返回 false） */
    @GetMapping("/status/{recipeId}")
    public ApiResult<Map<String, Object>> status(@PathVariable Long recipeId, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        boolean favorited = uid != null && favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getRecipeId, recipeId)) > 0;
        return ApiResult.ok(Map.of("favorited", favorited));
    }
}
