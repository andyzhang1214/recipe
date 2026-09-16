package com.recipe.service;

import com.recipe.entity.FoodAlias;
import com.recipe.entity.FoodCalorie;
import com.recipe.entity.Ingredient;
import com.recipe.mapper.FoodAliasMapper;
import com.recipe.mapper.FoodCalorieMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 食材匹配 + 热量计算核心逻辑测试：
 * 精确命中 / 别名命中（西红柿→番茄，不算估算）/ 模糊命中（估算 approx）/
 * 调料不计入 / 缺克数不计入并标记估算 / 每份热量 / 无可计食材返回未计算 / 联想去重。
 */
class FoodCalorieMatchServiceTest {

    private FoodCalorieMapper foodCalorieMapper;
    private FoodAliasMapper foodAliasMapper;
    private FoodCalorieMatchService service;

    @BeforeEach
    void setUp() {
        foodCalorieMapper = mock(FoodCalorieMapper.class);
        foodAliasMapper = mock(FoodAliasMapper.class);
        service = new FoodCalorieMatchService(foodCalorieMapper, foodAliasMapper);

        FoodCalorie fanqie = food(1L, "番茄", 18);
        FoodCalorie jidan = food(2L, "鸡蛋", 144);
        FoodCalorie jirou = food(3L, "鸡胸肉", 133);
        when(foodCalorieMapper.selectList(any())).thenReturn(List.of(fanqie, jidan, jirou));
        // 别名：西红柿/洋柿子 → 番茄(1)
        when(foodAliasMapper.selectList(any())).thenReturn(List.of(alias("西红柿", 1L), alias("洋柿子", 1L)));
    }

    private FoodCalorie food(Long id, String name, int cal) {
        FoodCalorie f = new FoodCalorie();
        f.setId(id);
        f.setName(name);
        f.setCaloriesPer100(cal);
        return f;
    }

    private FoodAlias alias(String name, Long foodId) {
        FoodAlias a = new FoodAlias();
        a.setAlias(name);
        a.setFoodCalorieId(foodId);
        return a;
    }

    private Ingredient food(String name, Integer grams) {
        Ingredient i = new Ingredient();
        i.setName(name);
        i.setType("FOOD");
        i.setAmountGrams(grams);
        return i;
    }

    private Ingredient seasoning(String name) {
        Ingredient i = new Ingredient();
        i.setName(name);
        i.setType("SEASONING");
        return i;
    }

    // ---------- 匹配 ----------

    @Test
    @DisplayName("精确名称命中：番茄")
    void matchExact() {
        FoodCalorieMatchService.MatchedFood m = service.matchOne("番茄");
        assertThat(m).isNotNull();
        assertThat(m.foodCalorieId()).isEqualTo(1L);
        assertThat(m.caloriesPer100()).isEqualTo(18);
        assertThat(m.exact()).isTrue();
    }

    @Test
    @DisplayName("别名精确命中：西红柿→番茄，不算估算")
    void matchAliasExact() {
        FoodCalorieMatchService.MatchedFood m = service.matchOne("西红柿");
        assertThat(m).isNotNull();
        assertThat(m.foodCalorieId()).isEqualTo(1L);
        assertThat(m.exact()).isTrue();
    }

    @Test
    @DisplayName("模糊命中：大西红柿 包含别名 西红柿 → 估算")
    void matchFuzzy() {
        FoodCalorieMatchService.MatchedFood m = service.matchOne("大西红柿");
        assertThat(m).isNotNull();
        assertThat(m.foodCalorieId()).isEqualTo(1L);
        assertThat(m.exact()).isFalse();
    }

    @Test
    @DisplayName("未命中：完全不相关的名称返回 null")
    void matchNone() {
        assertThat(service.matchOne("螺蛳粉")).isNull();
    }

    @Test
    @DisplayName("空名/空白名不匹配")
    void matchBlank() {
        assertThat(service.matchOne(null)).isNull();
        assertThat(service.matchOne("  ")).isNull();
    }

    // ---------- 热量计算 ----------

    @Test
    @DisplayName("精确+别名命中不计估算：鸡蛋50g + 西红柿100g = 72+18 = 90 千卡")
    void calcExactAndAlias() {
        List<Ingredient> ings = List.of(food("鸡蛋", 50), food("西红柿", 100));
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(ings, null);
        assertThat(cal.totalCalories()).isEqualTo(90);
        assertThat(cal.caloriesPerServing()).isNull();
        assertThat(cal.approx()).isFalse();
        // 回填匹配 id
        assertThat(ings.get(0).getFoodCalorieId()).isEqualTo(2L);
        assertThat(ings.get(1).getFoodCalorieId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("调料不计入热量：食用油一勺不参与计算")
    void calcSeasoningExcluded() {
        List<Ingredient> ings = List.of(food("鸡蛋", 50), seasoning("食用油"));
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(ings, null);
        assertThat(cal.totalCalories()).isEqualTo(72);
        assertThat(cal.approx()).isFalse();
        assertThat(ings.get(1).getFoodCalorieId()).isNull();
    }

    @Test
    @DisplayName("缺克数：食材标记估算且不计入")
    void calcMissingGrams() {
        List<Ingredient> ings = List.of(food("鸡蛋", 50), food("鸡胸肉", null));
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(ings, null);
        assertThat(cal.totalCalories()).isEqualTo(72);
        assertThat(cal.approx()).isTrue();
    }

    @Test
    @DisplayName("模糊命中：热量按相似条目估算并标记 approx")
    void calcFuzzyApprox() {
        List<Ingredient> ings = List.of(food("大西红柿", 200));
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(ings, null);
        // 18 kcal/100g × 200g = 36
        assertThat(cal.totalCalories()).isEqualTo(36);
        assertThat(cal.approx()).isTrue();
    }

    @Test
    @DisplayName("每份热量：总量除以人数四舍五入")
    void calcPerServing() {
        // 鸡蛋 50g=72 + 鸡胸肉 100g=133 = 205；2 人份 → 102.5 → 103（HALF_UP 由 Math.round 实现）
        List<Ingredient> ings = List.of(food("鸡蛋", 50), food("鸡胸肉", 100));
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(ings, 2);
        assertThat(cal.totalCalories()).isEqualTo(205);
        assertThat(cal.caloriesPerServing()).isEqualTo(103);
    }

    @Test
    @DisplayName("servings 缺省：每份热量为 null")
    void calcNoServings() {
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(List.of(food("鸡蛋", 50)), null);
        assertThat(cal.caloriesPerServing()).isNull();
    }

    @Test
    @DisplayName("全部未匹配：返回未计算（null），approx=false")
    void calcNothingMatched() {
        FoodCalorieMatchService.CalorieSummary cal =
                service.calculateAndFill(List.of(food("螺蛳粉", 300)), 2);
        assertThat(cal.totalCalories()).isNull();
        assertThat(cal.caloriesPerServing()).isNull();
        assertThat(cal.approx()).isFalse();
    }

    @Test
    @DisplayName("只有调料：返回未计算（不显示热量）")
    void calcOnlySeasonings() {
        FoodCalorieMatchService.CalorieSummary cal =
                service.calculateAndFill(List.of(seasoning("盐"), seasoning("生抽")), 2);
        assertThat(cal.totalCalories()).isNull();
    }

    @Test
    @DisplayName("空食材列表：返回未计算")
    void calcEmpty() {
        FoodCalorieMatchService.CalorieSummary cal = service.calculateAndFill(List.of(), 2);
        assertThat(cal.totalCalories()).isNull();
    }

    // ---------- 联想搜索 ----------

    @Test
    @DisplayName("联想：关键字命中名称与别名，且按热量条目去重")
    void searchDedup() {
        // 西红柿/洋柿子 都指向 番茄(1)，番茄本身也在库 → 只返回 1 条
        List<FoodCalorieMatchService.MatchedFood> res = service.search("西红柿", 10);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).foodCalorieId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("联想：空关键字返回全部（条目级）")
    void searchEmptyKeyword() {
        List<FoodCalorieMatchService.MatchedFood> res = service.search("", 10);
        assertThat(res).hasSize(3);
    }

    @Test
    @DisplayName("联想：limit 生效")
    void searchLimit() {
        assertThat(service.search("", 2)).hasSize(2);
    }

    @Test
    @DisplayName("联想相关度：精确 > 前缀 > 包含（鸡 → 鸡蛋/鸡胸肉 前缀命中排在包含命中的别名之前）")
    void searchRelevanceOrder() {
        // 库：番茄(1)/鸡蛋(2)/鸡胸肉(3)，别名：西红柿/洋柿子→番茄
        // "鸡"：鸡蛋、鸡胸肉为前缀命中(1分)，无包含命中的其它条目 → 依次返回
        List<FoodCalorieMatchService.MatchedFood> res = service.search("鸡", 10);
        assertThat(res).extracting(FoodCalorieMatchService.MatchedFood::matchedName)
                .containsExactly("鸡蛋", "鸡胸肉");
    }

    @Test
    @DisplayName("联想相关度：别名与正式名同级命中时，正式名在前（稳定排序）")
    void searchStableOrderSameScore() {
        // "西红柿" 精确命中别名(0分) 与包含命中的其他条目：别名条目应排最前
        List<FoodCalorieMatchService.MatchedFood> res = service.search("西红", 10);
        assertThat(res.get(0).matchedName()).isEqualTo("西红柿");
        assertThat(res.get(0).foodCalorieId()).isEqualTo(1L);
    }
}
