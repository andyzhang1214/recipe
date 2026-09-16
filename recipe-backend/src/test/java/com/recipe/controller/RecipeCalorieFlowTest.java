package com.recipe.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.entity.Ingredient;
import com.recipe.entity.Recipe;
import com.recipe.mapper.*;
import com.recipe.service.FoodCalorieMatchService;
import com.recipe.service.WxSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布链路热量计算端到端（不起容器）：
 * POST /api/recipes 的 body（小程序 publish.js 组装的结构）→
 * parseIngredients（type/amountGrams 解析）→ calculateAndFill（匹配+计算）→
 * recipe 热量冗余字段 + ingredient.food_calorie_id 回填。
 */
class RecipeCalorieFlowTest {

    private RecipeMapper recipeMapper;
    private IngredientMapper ingredientMapper;
    private RecipeController controller;

    @BeforeEach
    void setUp() {
        // applyCalories 用 LambdaUpdateWrapper（方法引用取列名），纯单测需手动注册实体元数据
        // （生产环境由 MyBatis-Plus 启动时随 Mapper 自动注册）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Recipe.class);
        TableInfoHelper.initTableInfo(assistant, Ingredient.class);

        recipeMapper = mock(RecipeMapper.class);
        ingredientMapper = mock(IngredientMapper.class);
        StepMapper stepMapper = mock(StepMapper.class);
        RatingMapper ratingMapper = mock(RatingMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        RecipeFamilyShareMapper recipeFamilyShareMapper = mock(RecipeFamilyShareMapper.class);
        UserMapper userMapper = mock(UserMapper.class);

        // 真实实例：测试请求无 X-WX-OPENID，内容检查自动跳过
        WxSecurityService wxSecurity = new WxSecurityService();
        FoodCalorieMapper foodCalorieMapper = mock(FoodCalorieMapper.class);
        FoodAliasMapper foodAliasMapper = mock(FoodAliasMapper.class);
        when(foodCalorieMapper.selectList(any())).thenReturn(List.of(
                food(1L, "番茄", 18), food(2L, "鸡蛋", 144)));
        when(foodAliasMapper.selectList(any())).thenReturn(List.of(alias("西红柿", 1L)));
        FoodCalorieMatchService matchService = new FoodCalorieMatchService(foodCalorieMapper, foodAliasMapper);

        controller = new RecipeController(recipeMapper, ingredientMapper, stepMapper, ratingMapper,
                commentMapper, familyMemberMapper, recipeFamilyShareMapper, userMapper, wxSecurity,
                matchService, new ObjectMapper());

        // 模拟 MyBatis-Plus insert 回填自增 id
        when(recipeMapper.insert(any(Recipe.class))).thenAnswer(inv -> {
            inv.getArgument(0, Recipe.class).setId(100L);
            return 1;
        });
        when(ingredientMapper.insert(any(Ingredient.class))).thenReturn(1);
        when(recipeMapper.update(any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class))).thenReturn(1);
    }

    private com.recipe.entity.FoodCalorie food(Long id, String name, int cal) {
        com.recipe.entity.FoodCalorie f = new com.recipe.entity.FoodCalorie();
        f.setId(id);
        f.setName(name);
        f.setCaloriesPer100(cal);
        return f;
    }

    private com.recipe.entity.FoodAlias alias(String name, Long foodId) {
        com.recipe.entity.FoodAlias a = new com.recipe.entity.FoodAlias();
        a.setAlias(name);
        a.setFoodCalorieId(foodId);
        return a;
    }

    private HttpServletRequest req(Long uid) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        if (uid != null) {
            when(request.getAttribute(com.recipe.config.AuthInterceptor.ATTR_UID)).thenReturn(uid);
        }
        return request;
    }

    /** 按小程序 publish.js submit() 的 payload 结构构造 body */
    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "西红柿炒蛋");
        body.put("visibility", "PRIVATE");
        body.put("servings", 2);
        // 前端合并后的 ingredients：FOOD 带 amountGrams，SEASONING 自由文本
        body.put("ingredients", List.of(
                Map.of("name", "鸡蛋", "amount", "50", "unit", "克", "type", "FOOD", "amountGrams", 50),
                Map.of("name", "西红柿", "amount", "100", "unit", "克", "type", "FOOD", "amountGrams", 100),
                Map.of("name", "食用油", "amount", "一勺", "type", "SEASONING")));
        body.put("steps", List.of(Map.of("text", "打蛋", "seasoning", "少许盐")));
        return body;
    }

    @Test
    @DisplayName("发布：热量 = 鸡蛋50g(72) + 西红柿100g(18) = 90，调料不计，每份 45")
    void createCalculatesCalories() {
        controller.create(body(), req(9L));

        // recipe 冗余字段（insert 捕获的同一实体被 applyCalories 原地更新）
        ArgumentCaptor<Recipe> recipeCap = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).insert(recipeCap.capture());
        Recipe saved = recipeCap.getValue();
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getTotalCalories()).isEqualTo(90);
        assertThat(saved.getCaloriesPerServing()).isEqualTo(45);
        assertThat(saved.getCaloriesApprox()).isFalse();

        // ingredient 行：type/克数/匹配 id 回填
        ArgumentCaptor<Ingredient> ingCap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientMapper, times(3)).insert(ingCap.capture());
        List<Ingredient> ings = ingCap.getAllValues();

        assertThat(ings.get(0).getName()).isEqualTo("鸡蛋");
        assertThat(ings.get(0).getType()).isEqualTo("FOOD");
        assertThat(ings.get(0).getAmountGrams()).isEqualTo(50);
        assertThat(ings.get(0).getFoodCalorieId()).isEqualTo(2L);

        assertThat(ings.get(1).getName()).isEqualTo("西红柿");
        assertThat(ings.get(1).getFoodCalorieId()).isEqualTo(1L); // 别名→番茄

        assertThat(ings.get(2).getName()).isEqualTo("食用油");
        assertThat(ings.get(2).getType()).isEqualTo("SEASONING");
        assertThat(ings.get(2).getFoodCalorieId()).isNull(); // 调料不匹配
        assertThat(ings.get(2).getAmount()).isEqualTo("一勺"); // 自由用量原样保留
    }

    @Test
    @DisplayName("热量显式 update（UpdateWrapper 而非 updateById）：清空场景可写 null")
    void caloriePersistedByUpdateWrapper() {
        controller.create(body(), req(9L));
        // applyCalories 必须走 update(Wrapper) 才能将 null 热量写库（updateById 会跳过 null 字段）
        verify(recipeMapper).update(any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("amountGrams 缺失：该食材不计入，热量标记为估算")
    void missingGramsMarksApprox() {
        Map<String, Object> body = body();
        body.put("ingredients", List.of(
                Map.of("name", "鸡蛋", "type", "FOOD", "amountGrams", 50),
                Map.of("name", "鸡胸肉", "type", "FOOD"))); // 未填克数
        controller.create(body, req(9L));

        ArgumentCaptor<Recipe> recipeCap = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).insert(recipeCap.capture());
        assertThat(recipeCap.getValue().getTotalCalories()).isEqualTo(72);
        assertThat(recipeCap.getValue().getCaloriesApprox()).isTrue();
    }

    @Test
    @DisplayName("全调料菜谱：热量为 null（未计算，详情页不展示）")
    void onlySeasoningsNoCalories() {
        Map<String, Object> body = body();
        body.put("ingredients", List.of(Map.of("name", "盐", "type", "SEASONING")));
        controller.create(body, req(9L));

        ArgumentCaptor<Recipe> recipeCap = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).insert(recipeCap.capture());
        assertThat(recipeCap.getValue().getTotalCalories()).isNull();
        assertThat(recipeCap.getValue().getCaloriesPerServing()).isNull();
    }

    @Test
    @DisplayName("amountGrams 兼容小数字符串：'50.5' 解析为 50（Double→int 截断）")
    void amountGramsFromString() {
        Map<String, Object> body = body();
        body.put("ingredients", List.of(Map.of(
                "name", "鸡蛋", "type", "FOOD", "amountGrams", "50.5")));
        controller.create(body, req(9L));

        ArgumentCaptor<Ingredient> ingCap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientMapper).insert(ingCap.capture());
        assertThat(ingCap.getValue().getAmountGrams()).isEqualTo(50);
    }

    @Test
    @DisplayName("ingredients 传空/不传：正常发布，热量为 null")
    void noIngredients() {
        Map<String, Object> body = body();
        body.put("ingredients", List.of());
        controller.create(body, req(9L));
        ArgumentCaptor<Recipe> recipeCap = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).insert(recipeCap.capture());
        assertThat(recipeCap.getValue().getTotalCalories()).isNull();
    }
}
