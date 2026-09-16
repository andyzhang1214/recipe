package com.recipe.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recipe.entity.*;
import com.recipe.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台页面路由：仪表盘 + 六大管理模块（数据装配直接查库）
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPageController {

    private final RecipeMapper recipeMapper;
    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final CommentMapper commentMapper;
    private final FoodCalorieMapper foodCalorieMapper;
    private final FoodAliasMapper foodAliasMapper;
    private final BannerMapper bannerMapper;

    private void base(Model model, String active) {
        model.addAttribute("active", active);
    }

    /** 仪表盘 */
    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        base(model, "dashboard");
        model.addAttribute("recipeCount", recipeMapper.selectCount(null));
        model.addAttribute("publicCount", recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>().eq(Recipe::getVisibility, "PUBLIC").eq(Recipe::getStatus, "PUBLISHED")));
        model.addAttribute("todayRecipe", recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>().ge(Recipe::getCreatedAt, today)));
        model.addAttribute("userCount", userMapper.selectCount(null));
        model.addAttribute("todayUser", userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, today)));
        model.addAttribute("familyCount", familyMapper.selectCount(null));
        model.addAttribute("pendingCount", familyMemberMapper.selectCount(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getStatus, "PENDING")));
        model.addAttribute("commentCount", commentMapper.selectCount(null));
        return "admin/dashboard";
    }

    /** 菜谱管理 */
    @GetMapping("/recipes")
    public String recipes(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(required = false) String keyword,
                          Model model) {
        base(model, "recipes");
        int size = 20;
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<Recipe>()
                .like(keyword != null && !keyword.isBlank(), Recipe::getTitle, keyword)
                .orderByDesc(Recipe::getId);
        Page<Recipe> p = recipeMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Recipe r : p.getRecords()) {
            User u = userMapper.selectById(r.getAuthorId());
            rows.add(Map.of("recipe", r, "author", u == null ? "-" : u.getNickname()));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("page", p);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "admin/recipes";
    }

    /** 用户管理 */
    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "1") int page, Model model) {
        base(model, "users");
        int size = 20;
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getId));
        model.addAttribute("page", p);
        return "admin/users";
    }

    /** 圈层管理 */
    @GetMapping("/families")
    public String families(@RequestParam(defaultValue = "1") int page, Model model) {
        base(model, "families");
        int size = 20;
        Page<Family> p = familyMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Family>().orderByDesc(Family::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Family f : p.getRecords()) {
            User u = userMapper.selectById(f.getOwnerId());
            rows.add(Map.of("family", f, "owner", u == null ? "-" : u.getNickname()));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("page", p);
        return "admin/families";
    }

    /** 评论管理 */
    @GetMapping("/comments")
    public String comments(@RequestParam(defaultValue = "1") int page, Model model) {
        base(model, "comments");
        int size = 20;
        Page<Comment> p = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Comment c : p.getRecords()) {
            User u = userMapper.selectById(c.getUserId());
            Recipe r = recipeMapper.selectById(c.getRecipeId());
            rows.add(Map.of("comment", c,
                    "user", u == null ? "-" : u.getNickname(),
                    "recipe", r == null ? "-" : r.getTitle()));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("page", p);
        return "admin/comments";
    }

    /** 热量库管理 */
    @GetMapping("/food-calories")
    public String foodCalories(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(required = false) String keyword,
                               Model model) {
        base(model, "food-calories");
        int size = 20;
        LambdaQueryWrapper<FoodCalorie> qw = new LambdaQueryWrapper<FoodCalorie>()
                .like(keyword != null && !keyword.isBlank(), FoodCalorie::getName, keyword)
                .orderByDesc(FoodCalorie::getId);
        Page<FoodCalorie> p = foodCalorieMapper.selectPage(new Page<>(page, size), qw);
        // 当前页各条目的别名（合并展示：西红柿、洋柿子）
        List<Long> ids = p.getRecords().stream().map(FoodCalorie::getId).toList();
        Map<Long, String> aliasMap = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            foodAliasMapper.selectList(new LambdaQueryWrapper<FoodAlias>()
                            .in(FoodAlias::getFoodCalorieId, ids))
                    .forEach(a -> aliasMap.merge(a.getFoodCalorieId(), a.getAlias(), (x, y) -> x + "、" + y));
        }
        model.addAttribute("page", p);
        model.addAttribute("aliasMap", aliasMap);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "admin/food-calories";
    }

    /** Banner 管理 */
    @GetMapping("/banners")
    public String banners(Model model) {
        base(model, "banners");
        model.addAttribute("banners", bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSort)));
        return "admin/banners";
    }
}
