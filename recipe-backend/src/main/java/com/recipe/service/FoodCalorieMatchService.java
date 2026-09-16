package com.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.entity.FoodAlias;
import com.recipe.entity.FoodCalorie;
import com.recipe.entity.Ingredient;
import com.recipe.mapper.FoodAliasMapper;
import com.recipe.mapper.FoodCalorieMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食材名称 → 热量库匹配 + 菜谱总热量计算。
 * 匹配优先级：精确名称 > 别名（food_alias）> 模糊包含（双向 contains，取最长命中）。
 * 热量只累计 type=FOOD 且填写了克数的食材；调料不计入。
 */
@Service
@RequiredArgsConstructor
public class FoodCalorieMatchService {

    private final FoodCalorieMapper foodCalorieMapper;
    private final FoodAliasMapper foodAliasMapper;

    /** 匹配结果：命中条目 id + 每100g热量；exact=false 表示模糊命中（估算，前端展示 ≈） */
    public record MatchedFood(Long foodCalorieId, Integer caloriesPer100, boolean exact, String matchedName) {}

    /** 计算结果：总热量 / 每份热量 / 是否估算值 */
    public record CalorieSummary(Integer totalCalories, Integer caloriesPerServing, boolean approx) {}

    /** 单个食材名称匹配（每次调用单独查库，供联想等低频场景使用） */
    public MatchedFood matchOne(String name) {
        if (name == null || name.isBlank()) return null;
        List<MatchedFood> all = loadAllMatchable();
        return doMatch(all, name.trim());
    }

    /**
     * 批量计算：对每个 FOOD 食材做匹配，回填 foodCalorieId 并返回总热量。
 * 未匹配或未填克数的食材不计入，并标记 approx=true。
     */
    public CalorieSummary calculateAndFill(List<Ingredient> ingredients, Integer servings) {
        List<MatchedFood> all = loadAllMatchable();
        boolean approx = false;
        double total = 0;
        boolean anyCounted = false;
        for (Ingredient ing : ingredients) {
            if (!"FOOD".equals(ing.getType())) {
                continue; // 调料不计入热量
            }
            if (ing.getAmountGrams() == null || ing.getAmountGrams() <= 0) {
                approx = true; // 食材缺克数，无法计入
                continue;
            }
            MatchedFood m = doMatch(all, ing.getName());
            if (m == null || m.caloriesPer100() == null) {
                approx = true; // 未匹配到热量库
                ing.setFoodCalorieId(null);
                continue;
            }
            ing.setFoodCalorieId(m.foodCalorieId());
            if (!m.exact()) {
                approx = true; // 模糊命中，按相似条目估算
            }
            total += m.caloriesPer100() * ing.getAmountGrams() / 100.0;
            anyCounted = true;
        }
        if (!anyCounted) {
            // 没有任何可计入的食材（空/全调料/全未匹配）：视为未计算
            return new CalorieSummary(null, null, false);
        }
        int totalRounded = (int) Math.round(total);
        Integer perServing = (servings != null && servings > 0)
                ? (int) Math.round(total / servings) : null;
        return new CalorieSummary(totalRounded, perServing, approx);
    }

    /**
     * 联想搜索：名称或别名包含关键字（keyword 为空时返回前 N 条），
     * 相关度排序 精确 > 前缀 > 包含（稳定排序，同级正式名在前），再按热量条目去重。
     */
    public List<MatchedFood> search(String keyword, int limit) {
        List<MatchedFood> all = loadAllMatchable();
        String kw = keyword == null ? "" : keyword.trim();
        List<MatchedFood> hits = new java.util.ArrayList<>();
        for (MatchedFood f : all) {
            if (kw.isEmpty() || f.matchedName().contains(kw)) {
                hits.add(f);
            }
        }
        if (!kw.isEmpty()) {
            // List.sort 稳定排序：同分保持原顺序（正式名先于别名）
            hits.sort(java.util.Comparator.comparingInt(f -> relevance(kw, f.matchedName())));
        }
        return hits.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MatchedFood::foodCalorieId, f -> f, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream()
                .limit(limit)
                .toList();
    }

    /** 相关度：精确命中 0 < 前缀命中 1 < 包含命中 2 */
    private static int relevance(String kw, String name) {
        if (name.equals(kw)) return 0;
        if (name.startsWith(kw)) return 1;
        return 2;
    }

    /** 加载全部热量条目 + 别名，展开为一个可匹配列表（库小，全量载入内存） */
    private List<MatchedFood> loadAllMatchable() {
        List<FoodCalorie> foods = foodCalorieMapper.selectList(null);
        List<FoodAlias> aliases = foodAliasMapper.selectList(null);
        Map<Long, FoodCalorie> byId = new HashMap<>();
        for (FoodCalorie f : foods) {
            byId.put(f.getId(), f);
        }
        List<MatchedFood> all = new java.util.ArrayList<>();
        for (FoodCalorie f : foods) {
            all.add(new MatchedFood(f.getId(), f.getCaloriesPer100(), true, f.getName()));
        }
        for (FoodAlias a : aliases) {
            FoodCalorie f = byId.get(a.getFoodCalorieId());
            if (f != null) {
                all.add(new MatchedFood(f.getId(), f.getCaloriesPer100(), true, a.getAlias()));
            }
        }
        return all;
    }

    /** 匹配：精确(名称/别名) > 模糊双向包含（取最长命中条目） */
    private MatchedFood doMatch(List<MatchedFood> all, String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        // 1. 精确
        for (MatchedFood f : all) {
            if (f.exact() && f.matchedName().equals(n)) {
                return f;
            }
        }
        // 2. 模糊：食材名包含库条目名，或库条目名包含食材名（如"西红柿炒蛋"→"西红柿"？不做菜名拆分，仅长度>=2 的包含）
        MatchedFood best = null;
        for (MatchedFood f : all) {
            String m = f.matchedName();
            if (m.length() >= 2 && (n.contains(m) || m.contains(n))) {
                if (best == null || m.length() > best.matchedName().length()) {
                    best = new MatchedFood(f.foodCalorieId(), f.caloriesPer100(), false, m);
                }
            }
        }
        return best;
    }
}
