package com.recipe.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.entity.*;
import com.recipe.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 后台 JSON 接口（session 鉴权，由 AdminInterceptor 保护）：
 * 菜谱上下架/删除、评论删除、圈层删除、热量库 CRUD、Banner CRUD
 */
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminApiController {

    private final RecipeMapper recipeMapper;
    private final CommentMapper commentMapper;
    private final FamilyMapper familyMapper;
    private final FoodCalorieMapper foodCalorieMapper;
    private final FoodAliasMapper foodAliasMapper;
    private final BannerMapper bannerMapper;

    // ---------- 菜谱 ----------
    /** 上下架：status = PUBLISHED（上架）/ DRAFT（下架） */
    @PostMapping("/recipes/{id}/status")
    public ApiResult<Void> recipeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Recipe r = recipeMapper.selectById(id);
        if (r == null) return ApiResult.err(404, "菜谱不存在");
        String status = body.get("status");
        if (!"PUBLISHED".equals(status) && !"DRAFT".equals(status)) {
            return ApiResult.err("状态不合法");
        }
        r.setStatus(status);
        recipeMapper.updateById(r);
        return ApiResult.ok();
    }

    @DeleteMapping("/recipes/{id}")
    public ApiResult<Void> recipeDelete(@PathVariable Long id) {
        recipeMapper.deleteById(id);
        return ApiResult.ok();
    }

    // ---------- 评论 ----------
    @DeleteMapping("/comments/{id}")
    public ApiResult<Void> commentDelete(@PathVariable Long id) {
        commentMapper.deleteById(id);
        return ApiResult.ok();
    }

    // ---------- 圈层 ----------
    @DeleteMapping("/families/{id}")
    public ApiResult<Void> familyDelete(@PathVariable Long id) {
        familyMapper.deleteById(id);
        return ApiResult.ok();
    }

    // ---------- 热量库 ----------
    @PostMapping("/food-calories")
    public ApiResult<Void> calorieCreate(@RequestBody FoodCalorie c) {
        if (c.getName() == null || c.getName().isBlank() || c.getCaloriesPer100() == null) {
            return ApiResult.err("名称和热量必填");
        }
        foodCalorieMapper.insert(c);
        return ApiResult.ok();
    }

    @PutMapping("/food-calories/{id}")
    public ApiResult<Void> calorieUpdate(@PathVariable Long id, @RequestBody FoodCalorie c) {
        FoodCalorie exist = foodCalorieMapper.selectById(id);
        if (exist == null) return ApiResult.err(404, "记录不存在");
        c.setId(id);
        foodCalorieMapper.updateById(c);
        return ApiResult.ok();
    }

    @DeleteMapping("/food-calories/{id}")
    public ApiResult<Void> calorieDelete(@PathVariable Long id) {
        foodCalorieMapper.deleteById(id);
        // 级联清理指向该条目的别名，避免悬挂引用
        foodAliasMapper.delete(new LambdaQueryWrapper<FoodAlias>()
                .eq(FoodAlias::getFoodCalorieId, id));
        return ApiResult.ok();
    }

    // ---------- 热量库别名 ----------
    /** 某热量条目下的别名列表 */
    @GetMapping("/food-calories/{id}/aliases")
    public ApiResult<List<FoodAlias>> aliasList(@PathVariable Long id) {
        return ApiResult.ok(foodAliasMapper.selectList(new LambdaQueryWrapper<FoodAlias>()
                .eq(FoodAlias::getFoodCalorieId, id).orderByAsc(FoodAlias::getId)));
    }

    /** 新增别名（同一别名全局唯一） */
    @PostMapping("/food-calories/{id}/aliases")
    public ApiResult<Void> aliasCreate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String alias = body.get("alias");
        if (alias == null || alias.isBlank()) {
            return ApiResult.err("别名不能为空");
        }
        if (foodCalorieMapper.selectById(id) == null) {
            return ApiResult.err(404, "热量条目不存在");
        }
        Long cnt = foodAliasMapper.selectCount(new LambdaQueryWrapper<FoodAlias>()
                .eq(FoodAlias::getAlias, alias.trim()));
        if (cnt != null && cnt > 0) {
            return ApiResult.err("别名「" + alias.trim() + "」已存在");
        }
        FoodAlias fa = new FoodAlias();
        fa.setAlias(alias.trim());
        fa.setFoodCalorieId(id);
        foodAliasMapper.insert(fa);
        return ApiResult.ok();
    }

    @DeleteMapping("/food-aliases/{aliasId}")
    public ApiResult<Void> aliasDelete(@PathVariable Long aliasId) {
        foodAliasMapper.deleteById(aliasId);
        return ApiResult.ok();
    }

    // ---------- Banner ----------
    @PostMapping("/banners")
    public ApiResult<Void> bannerCreate(@RequestBody Map<String, Object> body) {
        Banner b = new Banner();
        b.setTitle((String) body.get("title"));
        b.setImageUrl(String.valueOf(body.get("imageUrl")));
        b.setLinkType(String.valueOf(body.getOrDefault("linkType", "NONE")));
        b.setLinkValue(body.get("linkValue") == null ? null : Long.valueOf(body.get("linkValue").toString()));
        b.setStatus(String.valueOf(body.getOrDefault("status", "OFF")));
        b.setSort(body.get("sort") == null ? 0 : Integer.valueOf(body.get("sort").toString()));
        b.setStartAt(parse(body.get("startAt")));
        b.setEndAt(parse(body.get("endAt")));
        if (b.getImageUrl() == null || b.getImageUrl().isBlank()) {
            return ApiResult.err("图片 URL 必填");
        }
        bannerMapper.insert(b);
        return ApiResult.ok();
    }

    @PutMapping("/banners/{id}")
    public ApiResult<Void> bannerUpdate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Banner b = bannerMapper.selectById(id);
        if (b == null) return ApiResult.err(404, "Banner 不存在");
        if (body.get("title") != null) b.setTitle((String) body.get("title"));
        if (body.get("imageUrl") != null) b.setImageUrl(String.valueOf(body.get("imageUrl")));
        if (body.get("linkType") != null) b.setLinkType(String.valueOf(body.get("linkType")));
        if (body.get("linkValue") != null) b.setLinkValue(Long.valueOf(body.get("linkValue").toString()));
        if (body.get("status") != null) b.setStatus(String.valueOf(body.get("status")));
        if (body.get("sort") != null) b.setSort(Integer.valueOf(body.get("sort").toString()));
        if (body.get("startAt") != null) b.setStartAt(parse(body.get("startAt")));
        if (body.get("endAt") != null) b.setEndAt(parse(body.get("endAt")));
        bannerMapper.updateById(b);
        return ApiResult.ok();
    }

    @PostMapping("/banners/{id}/status")
    public ApiResult<Void> bannerStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Banner b = bannerMapper.selectById(id);
        if (b == null) return ApiResult.err(404, "Banner 不存在");
        b.setStatus("ON".equals(body.get("status")) ? "ON" : "OFF");
        bannerMapper.updateById(b);
        return ApiResult.ok();
    }

    @DeleteMapping("/banners/{id}")
    public ApiResult<Void> bannerDelete(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        return ApiResult.ok();
    }

    private LocalDateTime parse(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return LocalDateTime.parse(String.valueOf(v).replace("T", " "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
