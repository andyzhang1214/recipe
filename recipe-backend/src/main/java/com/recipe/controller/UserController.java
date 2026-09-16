package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.common.PageResult;
import com.recipe.common.ProfileAccess;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Recipe;
import com.recipe.entity.User;
import com.recipe.entity.UserFollow;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.RecipeMapper;
import com.recipe.mapper.UserFollowMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户主页：主页信息（头像/昵称/关注/粉丝/公开菜谱数）+ 公开菜谱瀑布流。
 * 游客可访问公开主页；私密/仅圈内可见的主页按 ProfileAccess 判定。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final RecipeMapper recipeMapper;
    private final UserFollowMapper followMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final ObjectMapper objectMapper;

    /** 主页信息（游客可访问公开主页） */
    @GetMapping("/{id}/profile")
    public ApiResult<Map<String, Object>> profile(@PathVariable Long id, HttpServletRequest request) {
        Long viewer = AuthInterceptor.uid(request);
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        boolean isSelf = viewer != null && viewer.equals(id);
        boolean locked = !ProfileAccess.accessible(u, viewer, familyMemberMapper);

        Map<String, Object> user = new HashMap<>();
        user.put("id", u.getId());
        user.put("nickname", u.getNickname());
        user.put("avatar", u.getAvatar());
        user.put("profileVisibility", u.getProfileVisibility());

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("isSelf", isSelf);
        result.put("locked", locked);
        result.put("followingCount", followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, id)));
        result.put("fansCount", followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, id)));
        result.put("recipeCount", recipeMapper.selectCount(new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getAuthorId, id)
                .eq(Recipe::getVisibility, Constants.VISIBILITY_PUBLIC)
                .eq(Recipe::getStatus, Constants.RECIPE_PUBLISHED)));
        result.put("followed", !isSelf && viewer != null && followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, viewer).eq(UserFollow::getFolloweeId, id)) > 0);
        return ApiResult.ok(result);
    }

    /** 用户公开菜谱（主页瀑布流，游客可访问） */
    @GetMapping("/{id}/recipes")
    public ApiResult<PageResult<Map<String, Object>>> recipes(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "0") long page,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              HttpServletRequest request) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!ProfileAccess.accessible(u, AuthInterceptor.uid(request), familyMemberMapper)) {
            throw new BizException(403, "该用户主页不可见");
        }
        Page<Recipe> p = recipeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getAuthorId, id)
                        .eq(Recipe::getVisibility, Constants.VISIBILITY_PUBLIC)
                        .eq(Recipe::getStatus, Constants.RECIPE_PUBLISHED)
                        .orderByDesc(Recipe::getId));
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (Recipe r : p.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("recipe", r);
            item.put("covers", parseCovers(r.getCovers()));
            records.add(item);
        }
        return ApiResult.ok(PageResult.of(records, p.getCurrent(), p.getSize(), p.getTotal()));
    }

    private List<String> parseCovers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
