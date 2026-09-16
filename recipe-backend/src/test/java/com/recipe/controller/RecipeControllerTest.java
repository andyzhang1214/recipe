package com.recipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.Recipe;
import com.recipe.mapper.*;
import com.recipe.service.FoodCalorieMatchService;
import com.recipe.service.WxSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 菜谱可见性（checkVisible）分支测试：PUBLIC / PRIVATE（作者、他人、游客）/
 * FAMILY（shareAll 交集、指定队伍分享、存量 familyId、非成员、游客）。
 * 通过公开端点 detail() 驱动，不起 Spring 容器。
 */
class RecipeControllerTest {

    private RecipeMapper recipeMapper;
    private FamilyMemberMapper familyMemberMapper;
    private RecipeFamilyShareMapper recipeFamilyShareMapper;
    private RecipeController controller;

    @BeforeEach
    void setUp() {
        recipeMapper = mock(RecipeMapper.class);
        IngredientMapper ingredientMapper = mock(IngredientMapper.class);
        StepMapper stepMapper = mock(StepMapper.class);
        RatingMapper ratingMapper = mock(RatingMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        familyMemberMapper = mock(FamilyMemberMapper.class);
        recipeFamilyShareMapper = mock(RecipeFamilyShareMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        // JDK25 下 Byte Buddy 无法 inline mock WxSecurityService，改用真实实例：
        // 测试请求无 X-WX-OPENID 头，内容检查自动跳过，行为与放行的 mock 等价
        WxSecurityService wxSecurity = new WxSecurityService();
        // 真实匹配服务 + 空 mapper：可见性用例不涉热量计算，空库即返回未计算
        FoodCalorieMapper foodCalorieMapper = mock(FoodCalorieMapper.class);
        FoodAliasMapper foodAliasMapper = mock(FoodAliasMapper.class);
        when(foodCalorieMapper.selectList(any())).thenReturn(List.of());
        when(foodAliasMapper.selectList(any())).thenReturn(List.of());
        FoodCalorieMatchService calorieMatchService = new FoodCalorieMatchService(foodCalorieMapper, foodAliasMapper);
        controller = new RecipeController(recipeMapper, ingredientMapper, stepMapper, ratingMapper,
                commentMapper, familyMemberMapper, recipeFamilyShareMapper, userMapper, wxSecurity,
                calorieMatchService, new ObjectMapper());
        // detail() 组装所需的基础查询（Mockito 默认即空集合，这里显式声明意图）
        when(ingredientMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(recipeFamilyShareMapper.selectList(any())).thenReturn(List.of());
    }

    private HttpServletRequest req(Long uid) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        if (uid != null) {
            when(request.getAttribute(AuthInterceptor.ATTR_UID)).thenReturn(uid);
        }
        return request;
    }

    private Recipe recipe(Long id, Long authorId, String visibility) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setAuthorId(authorId);
        r.setVisibility(visibility);
        return r;
    }

    private FamilyMember approvedMember(Long familyId, Long userId) {
        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        m.setUserId(userId);
        m.setStatus(Constants.MEMBER_APPROVED);
        return m;
    }

    // ---------- PUBLIC ----------

    @Test
    @DisplayName("PUBLIC 菜谱：游客可看")
    void publicRecipe_visibleToVisitor() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_PUBLIC));
        var result = controller.detail(7L, req(null));
        assertThat(result.getData()).isNotNull();
    }

    // ---------- PRIVATE ----------

    @Test
    @DisplayName("PRIVATE 菜谱：作者本人可看")
    void privateRecipe_visibleToAuthor() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_PRIVATE));
        var result = controller.detail(7L, req(100L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("PRIVATE 菜谱：其他登录用户 403")
    void privateRecipe_forbiddenToOthers() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_PRIVATE));
        assertThatThrownBy(() -> controller.detail(7L, req(200L)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(403);
    }

    @Test
    @DisplayName("PRIVATE 菜谱：游客 401")
    void privateRecipe_requiresLogin() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_PRIVATE));
        assertThatThrownBy(() -> controller.detail(7L, req(null)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(401);
    }

    // ---------- FAMILY：shareAll（作者全部队伍） ----------

    @Test
    @DisplayName("FAMILY+shareAll：与作者同队可看")
    void familyShareAll_overlapWithAuthor() {
        Recipe r = recipe(7L, 100L, Constants.VISIBILITY_FAMILY);
        r.setShareAllFamilies(true);
        when(recipeMapper.selectById(7L)).thenReturn(r);
        // 我在队伍 10；作者也在队伍 10（overlap 计数 > 0）
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(approvedMember(10L, 200L)));
        when(familyMemberMapper.selectCount(any())).thenReturn(1L);
        var result = controller.detail(7L, req(200L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("FAMILY+shareAll：与作者不同队但被指定分享到我的队伍可看")
    void familyShareAll_noOverlapButSharedToMyFamily() {
        Recipe r = recipe(7L, 100L, Constants.VISIBILITY_FAMILY);
        r.setShareAllFamilies(true);
        when(recipeMapper.selectById(7L)).thenReturn(r);
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(approvedMember(20L, 200L)));
        // 第一次 selectCount：与作者交集 = 0；第二次：分享表命中 = 1
        when(familyMemberMapper.selectCount(any())).thenReturn(0L);
        when(recipeFamilyShareMapper.selectCount(any())).thenReturn(1L);
        var result = controller.detail(7L, req(200L));
        assertThat(result.getData()).isNotNull();
    }

    // ---------- FAMILY：指定队伍分享 ----------

    @Test
    @DisplayName("FAMILY 指定分享：分享到我的队伍可看")
    void familySharedToMyFamily_visible() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_FAMILY));
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(approvedMember(20L, 200L)));
        when(recipeFamilyShareMapper.selectCount(any())).thenReturn(1L);
        var result = controller.detail(7L, req(200L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("FAMILY 存量 family_id：成员可看（兼容旧数据）")
    void familyLegacyFamilyId_visible() {
        Recipe r = recipe(7L, 100L, Constants.VISIBILITY_FAMILY);
        r.setFamilyId(20L);
        when(recipeMapper.selectById(7L)).thenReturn(r);
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(approvedMember(20L, 200L)));
        // shareAll=false 且分享表未命中，靠存量 familyId 放行
        when(recipeFamilyShareMapper.selectCount(any())).thenReturn(0L);
        var result = controller.detail(7L, req(200L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("FAMILY：无任何队伍成员身份 403")
    void family_noMembership_forbidden() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_FAMILY));
        when(familyMemberMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> controller.detail(7L, req(200L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅队伍成员可见");
    }

    @Test
    @DisplayName("FAMILY：不在分享队伍内的成员 403")
    void family_otherFamilyMember_forbidden() {
        when(recipeMapper.selectById(7L)).thenReturn(recipe(7L, 100L, Constants.VISIBILITY_FAMILY));
        // 我在队伍 30，但菜谱只分享到队伍 20
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(approvedMember(30L, 200L)));
        when(recipeFamilyShareMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> controller.detail(7L, req(200L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅队伍成员可见");
    }
}
