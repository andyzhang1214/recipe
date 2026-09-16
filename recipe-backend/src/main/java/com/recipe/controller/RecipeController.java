package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.common.PageResult;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.*;
import com.recipe.mapper.*;
import com.recipe.service.FoodCalorieMatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 菜谱：广场（公开）、详情（四级可见性校验）、发布/编辑/删除、评星、评论
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeMapper recipeMapper;
    private final IngredientMapper ingredientMapper;
    private final StepMapper stepMapper;
    private final RatingMapper ratingMapper;
    private final CommentMapper commentMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final RecipeFamilyShareMapper recipeFamilyShareMapper;
    private final UserMapper userMapper;
    private final com.recipe.service.WxSecurityService wxSecurity;
    private final com.recipe.service.FoodCalorieMatchService calorieMatchService;
    private final ObjectMapper objectMapper;

    /** 广场：公开菜谱分页（游客可调） */
    @GetMapping("/public")
    public ApiResult<PageResult<Map<String, Object>>> publicList(@RequestParam(defaultValue = "0") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getVisibility, Constants.VISIBILITY_PUBLIC)
                .eq(Recipe::getStatus, Constants.RECIPE_PUBLISHED)
                .like(keyword != null && !keyword.isBlank(), Recipe::getTitle, keyword)
                .orderByDesc(Recipe::getId);
        Page<Recipe> p = recipeMapper.selectPage(new Page<>(page, size), qw);
        // 组装广场列表项：recipe 字段平铺 + 作者信息 + 多图数组（小红书瀑布流需要展示作者头像昵称 + 可滑动封面）
        // 作者批量查询（selectBatchIds），避免每条菜谱单查一次用户表（N+1）
        List<Long> authorIds = p.getRecords().stream().map(Recipe::getAuthorId).distinct().toList();
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (Recipe r : p.getRecords()) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("recipe", r);
            item.put("covers", parseCovers(r.getCovers()));
            User author = userMap.get(r.getAuthorId());
            item.put("authorNickname", author == null ? null : author.getNickname());
            item.put("authorAvatar", author == null ? null : author.getAvatar());
            item.put("authorId", r.getAuthorId());
            records.add(item);
        }
        return ApiResult.ok(PageResult.of(records, p.getCurrent(), p.getSize(), p.getTotal()));
    }

    /** 我的菜谱（含草稿，按 status 过滤可选） */
    @GetMapping("/mine")
    public ApiResult<List<Recipe>> mine(@RequestParam(required = false) String status, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        return ApiResult.ok(recipeMapper.selectList(new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getAuthorId, uid)
                .eq(status != null && !status.isBlank(), Recipe::getStatus, status)
                .orderByDesc(Recipe::getId)));
    }

    /** 详情（含食材、步骤；按可见性校验） */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BizException(404, "菜谱不存在");
        }
        checkVisible(recipe, AuthInterceptor.uid(request));

        List<Ingredient> ingredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getRecipeId, id).orderByAsc(Ingredient::getSort));
        List<Step> steps = stepMapper.selectList(
                new LambdaQueryWrapper<Step>().eq(Step::getRecipeId, id).orderByAsc(Step::getOrd));
        User author = userMapper.selectById(recipe.getAuthorId());
        // Map.of 不允许 null 值，author/nickname/avatar 可能为空，改用 HashMap
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("recipe", recipe);
        result.put("covers", parseCovers(recipe.getCovers()));
        if (author == null) {
            result.put("author", null);
        } else {
            Map<String, Object> authorMap = new java.util.HashMap<>();
            authorMap.put("id", author.getId());
            authorMap.put("nickname", author.getNickname());
            authorMap.put("avatar", author.getAvatar());
            result.put("author", authorMap);
        }
        result.put("ingredients", ingredients);
        result.put("steps", steps);
        // 分享范围：编辑回填用
        result.put("shareAll", Boolean.TRUE.equals(recipe.getShareAllFamilies()));
        List<Long> sharedIds = recipeFamilyShareMapper.selectList(new LambdaQueryWrapper<RecipeFamilyShare>()
                        .eq(RecipeFamilyShare::getRecipeId, id))
                .stream().map(RecipeFamilyShare::getFamilyId).distinct().toList();
        if (recipe.getFamilyId() != null && !sharedIds.contains(recipe.getFamilyId())) {
            List<Long> withLegacy = new java.util.ArrayList<>(sharedIds);
            withLegacy.add(recipe.getFamilyId());
            sharedIds = withLegacy;
        }
        result.put("sharedFamilyIds", sharedIds);
        return ApiResult.ok(result);
    }

    /** 发布/保存草稿（需登录） */
    @PostMapping
    @Transactional
    public ApiResult<Long> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Recipe recipe = new Recipe();
        recipe.setAuthorId(uid);
        recipe.setTitle((String) body.get("title"));
        // 展示图多图（最多4张）：covers 数组 → JSON 入库，cover 取第一张兜底
        List<String> covers = parseCovers(body.get("covers"));
        recipe.setCovers(toCoversJson(covers));
        recipe.setCover(covers.isEmpty() ? (String) body.get("cover") : covers.get(0));
        recipe.setVisibility(String.valueOf(body.getOrDefault("visibility", Constants.VISIBILITY_PRIVATE)));
        recipe.setDifficulty((String) body.get("difficulty"));
        recipe.setServings(body.get("servings") == null ? null : Integer.valueOf(body.get("servings").toString()));
        recipe.setStatus(Constants.RECIPE_PUBLISHED.equals(body.get("status")) ? Constants.RECIPE_PUBLISHED : Constants.RECIPE_DRAFT);
        recipe.setAvgStars(BigDecimal.ZERO);
        recipe.setRatingCount(0);
        if (recipe.getTitle() == null || recipe.getTitle().isBlank()) {
            throw new BizException("标题不能为空");
        }
        // 内容安全（云托管链路）：标题 + 展示图 + 食材/步骤文本，openid 取自 X-WX-OPENID
        String wxOpenid = request.getHeader("X-WX-OPENID");
        boolean imgUnsafe = covers.stream().anyMatch(c -> c.startsWith("http") && wxSecurity.checkImage(c, wxOpenid));
        if (wxSecurity.checkText(recipe.getTitle(), wxOpenid)
                || wxSecurity.checkTexts(collectTexts(body, "ingredients", "name"), wxOpenid)
                || wxSecurity.checkTexts(collectTexts(body, "steps", "text"), wxOpenid)
                || wxSecurity.checkTexts(collectTexts(body, "steps", "seasoning"), wxOpenid)
                || imgUnsafe
                || (recipe.getCover() != null && recipe.getCover().startsWith("http")
                    && wxSecurity.checkImage(recipe.getCover(), wxOpenid))) {
            throw new BizException("内容包含违规信息，请修改后重试");
        }
        recipe.setShareAllFamilies(false);
        if (Constants.VISIBILITY_FAMILY.equals(recipe.getVisibility())) {
            boolean shareAll = Boolean.parseBoolean(String.valueOf(body.getOrDefault("shareAll", false)));
            List<Long> familyIds = parseFamilyIds(body);
            if (!shareAll && familyIds.isEmpty()) {
                throw new BizException("请选择分享的队伍");
            }
            if (!shareAll) {
                validateMyFamilies(familyIds, uid);
            }
            recipe.setShareAllFamilies(shareAll);
            recipe.setFamilyId(shareAll ? null : familyIds.get(0));
        }
        recipeMapper.insert(recipe);
        if (Constants.VISIBILITY_FAMILY.equals(recipe.getVisibility())
                && !Boolean.TRUE.equals(recipe.getShareAllFamilies())) {
            saveFamilyShares(recipe.getId(), parseFamilyIds(body));
        }

        // 食材：解析（含 type/克数）→ 热量匹配计算 → 冗余回填 recipe
        List<Ingredient> ings = parseIngredients(recipe.getId(), body.get("ingredients"));
        applyCalories(recipe, ings);
        for (Ingredient ing : ings) {
            ingredientMapper.insert(ing);
        }
        // 步骤
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        if (steps != null) {
            int i = 0;
            for (Map<String, Object> st : steps) {
                Step step = new Step();
                step.setRecipeId(recipe.getId());
                step.setOrd(i++);
                step.setText((String) st.get("text"));
                step.setSeasoning((String) st.get("seasoning"));
                step.setImage((String) st.get("image"));
                step.setDurationSec(st.get("durationSec") == null ? null : Integer.valueOf(st.get("durationSec").toString()));
                stepMapper.insert(step);
            }
        }
        return ApiResult.ok(recipe.getId());
    }

    /** 编辑（仅作者）：标题/封面/可见性/食材/步骤整体替换 */
    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BizException(404, "菜谱不存在");
        }
        if (!recipe.getAuthorId().equals(uid)) {
            throw new BizException(403, "只能编辑自己的菜谱");
        }
        if (body.get("title") != null) recipe.setTitle((String) body.get("title"));
        // 展示图多图更新：传 covers 则整体替换（cover 同步取第一张）
        if (body.get("covers") != null) {
            List<String> covers = parseCovers(body.get("covers"));
            recipe.setCovers(toCoversJson(covers));
            recipe.setCover(covers.isEmpty() ? null : covers.get(0));
        } else if (body.get("cover") != null) {
            recipe.setCover((String) body.get("cover"));
        }
        // 可见性与队伍分享（FAMILY 需 shareAll 或 familyIds）
        String newVisibility = body.get("visibility") == null ? recipe.getVisibility() : (String) body.get("visibility");
        boolean visibilityChanged = !newVisibility.equals(recipe.getVisibility());
        boolean shareChanged = body.containsKey("shareAll") || body.containsKey("familyIds") || body.containsKey("familyId");
        if (Constants.VISIBILITY_FAMILY.equals(newVisibility)) {
            boolean shareAll = body.containsKey("shareAll")
                    ? Boolean.parseBoolean(String.valueOf(body.get("shareAll")))
                    : Boolean.TRUE.equals(recipe.getShareAllFamilies());
            List<Long> familyIds = body.containsKey("familyIds") || body.containsKey("familyId")
                    ? parseFamilyIds(body)
                    : recipeFamilyShareMapper.selectList(new LambdaQueryWrapper<RecipeFamilyShare>()
                            .eq(RecipeFamilyShare::getRecipeId, id))
                            .stream().map(RecipeFamilyShare::getFamilyId).toList();
            if (!shareAll && familyIds.isEmpty() && recipe.getFamilyId() == null) {
                throw new BizException("请选择分享的队伍");
            }
            if (!shareAll && !familyIds.isEmpty()) {
                validateMyFamilies(familyIds, uid);
            }
            if (visibilityChanged || shareChanged) {
                recipe.setShareAllFamilies(shareAll);
                recipe.setFamilyId(shareAll ? null : (!familyIds.isEmpty() ? familyIds.get(0) : recipe.getFamilyId()));
            }
            if (visibilityChanged || shareChanged) {
                saveFamilyShares(id, shareAll ? List.of() : familyIds);
            }
        } else if (visibilityChanged) {
            recipe.setShareAllFamilies(false);
            recipe.setFamilyId(null);
            saveFamilyShares(id, List.of());
        }
        recipe.setVisibility(newVisibility);
        if (body.get("difficulty") != null) recipe.setDifficulty((String) body.get("difficulty"));
        // containsKey 而非 != null：前端清空几人份发 null，null 也要写入（否则每份热量一直按旧人数算）
        if (body.containsKey("servings")) {
            recipe.setServings(body.get("servings") == null ? null : Integer.valueOf(body.get("servings").toString()));
        }
        if (body.get("status") != null) recipe.setStatus((String) body.get("status"));
        recipeMapper.updateById(recipe);
        // updateById 的 NOT_NULL 策略会跳过 null：显式清空 servings 需单独 set null
        if (body.containsKey("servings") && body.get("servings") == null) {
            recipeMapper.update(new LambdaUpdateWrapper<Recipe>()
                    .eq(Recipe::getId, id)
                    .set(Recipe::getServings, null));
        }

        // 食材：传入即整体替换（删旧插新），并重算热量
        if (body.get("ingredients") != null) {
            ingredientMapper.delete(new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getRecipeId, id));
            List<Ingredient> ings = parseIngredients(id, body.get("ingredients"));
            applyCalories(recipe, ings);
            for (Ingredient ing : ings) {
                ingredientMapper.insert(ing);
            }
        }
        if (body.get("steps") != null) {
            stepMapper.delete(new LambdaQueryWrapper<Step>().eq(Step::getRecipeId, id));
            List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
            int i = 0;
            for (Map<String, Object> st : steps) {
                Step step = new Step();
                step.setRecipeId(id);
                step.setOrd(i++);
                step.setText((String) st.get("text"));
                step.setSeasoning((String) st.get("seasoning"));
                step.setImage((String) st.get("image"));
                step.setDurationSec(st.get("durationSec") == null ? null : Integer.valueOf(st.get("durationSec").toString()));
                stepMapper.insert(step);
            }
        }
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BizException(404, "菜谱不存在");
        }
        if (!recipe.getAuthorId().equals(uid) && !AuthInterceptor.isAdmin(request)) {
            throw new BizException(403, "无权限删除");
        }
        recipeMapper.deleteById(id);
        return ApiResult.ok();
    }

    /** 评星（1-5，一人一评，可改评；同步聚合均分） */
    @PostMapping("/{id}/rating")
    @Transactional
    public ApiResult<Void> rate(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Object starsVal = body.get("stars");
        if (starsVal == null) {
            throw new BizException("评分不能为空");
        }
        int stars;
        try {
            stars = Integer.parseInt(String.valueOf(starsVal));
        } catch (NumberFormatException e) {
            throw new BizException("评分需为数字");
        }
        if (stars < 1 || stars > 5) {
            throw new BizException("评分需在 1-5 星之间");
        }
        Rating rating = ratingMapper.selectOne(new LambdaQueryWrapper<Rating>()
                .eq(Rating::getRecipeId, id).eq(Rating::getUserId, uid));
        if (rating == null) {
            rating = new Rating();
            rating.setRecipeId(id);
            rating.setUserId(uid);
            rating.setStars(stars);
            ratingMapper.insert(rating);
        } else {
            rating.setStars(stars);
            ratingMapper.updateById(rating);
        }
        recalcRating(id);
        return ApiResult.ok();
    }

    private void recalcRating(Long recipeId) {
        List<Rating> all = ratingMapper.selectList(new LambdaQueryWrapper<Rating>().eq(Rating::getRecipeId, recipeId));
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) return;
        if (all.isEmpty()) {
            recipe.setAvgStars(BigDecimal.ZERO);
            recipe.setRatingCount(0);
        } else {
            double avg = all.stream().mapToInt(Rating::getStars).average().orElse(0);
            recipe.setAvgStars(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            recipe.setRatingCount(all.size());
        }
        recipeMapper.updateById(recipe);
    }

    /** 评论列表（附带评论者昵称/头像占位字，避免前端直接展示数字 ID） */
    @GetMapping("/{id}/comments")
    public ApiResult<List<Map<String, Object>>> comments(@PathVariable Long id) {
        List<Comment> list = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().eq(Comment::getRecipeId, id).orderByAsc(Comment::getId));
        List<Long> userIds = list.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Comment c : list) {
            User u = userMap.get(c.getUserId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", c.getId());
            item.put("userId", c.getUserId());
            item.put("nickname", u == null ? null : u.getNickname());
            item.put("avatar", u == null ? null : u.getAvatar());
            item.put("avatarChar", com.recipe.dto.UserDTO.avatarChar(u));
            item.put("content", c.getContent());
            item.put("parentId", c.getParentId());
            item.put("createdAt", c.getCreatedAt());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** 发表评论（可楼中楼回复） */
    @PostMapping("/{id}/comments")
    public ApiResult<Long> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        if (wxSecurity.checkText(content, request.getHeader("X-WX-OPENID"))) {
            throw new BizException("评论包含违规内容");
        }
        Comment comment = new Comment();
        comment.setRecipeId(id);
        comment.setUserId(uid);
        comment.setContent(content);
        comment.setParentId(body.get("parentId") == null ? null : Long.valueOf(body.get("parentId").toString()));
        commentMapper.insert(comment);
        return ApiResult.ok(comment.getId());
    }

    /** 可见性校验：PUBLIC 放行；作者始终可看；FAMILY/TEAM 需成员；PRIVATE 仅作者 */
    private void checkVisible(Recipe recipe, Long uid) {
        if (Constants.VISIBILITY_PUBLIC.equals(recipe.getVisibility())) {
            return;
        }
        // 作者本人：任何可见范围（私密/圈内）都始终可看
        if (uid != null && recipe.getAuthorId().equals(uid)) {
            return;
        }
        if (uid == null) {
            throw new BizException(401, "请先登录");
        }
        if (Constants.VISIBILITY_PRIVATE.equals(recipe.getVisibility())) {
            throw new BizException(403, "无权查看");
        }
        // FAMILY / TEAM：全部队伍（作者任一队伍成员可看）或指定队伍（share 表/存量 family_id）
        List<Long> myFamilyIds = myApprovedFamilyIds(uid);
        if (myFamilyIds.isEmpty()) {
            throw new BizException(403, "仅队伍成员可见");
        }
        if (Boolean.TRUE.equals(recipe.getShareAllFamilies())) {
            // 作者的全部队伍：与我的队伍存在交集即可
            Long overlap = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                    .in(FamilyMember::getFamilyId, myFamilyIds)
                    .eq(FamilyMember::getUserId, recipe.getAuthorId())
                    .eq(FamilyMember::getStatus, "APPROVED"));
            if (overlap != null && overlap > 0) {
                return;
            }
        }
        if (!myFamilyIds.isEmpty()) {
            Long shared = recipeFamilyShareMapper.selectCount(new LambdaQueryWrapper<RecipeFamilyShare>()
                    .eq(RecipeFamilyShare::getRecipeId, recipe.getId())
                    .in(RecipeFamilyShare::getFamilyId, myFamilyIds));
            if (shared != null && shared > 0) {
                return;
            }
        }
        if (recipe.getFamilyId() != null && myFamilyIds.contains(recipe.getFamilyId())) {
            return;
        }
        throw new BizException(403, "仅队伍成员可见");
    }

    /** 我已加入（APPROVED）的队伍 id 列表 */
    private List<Long> myApprovedFamilyIds(Long uid) {
        if (uid == null) return List.of();
        return familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, uid)
                        .eq(FamilyMember::getStatus, "APPROVED"))
                .stream().map(FamilyMember::getFamilyId).distinct().toList();
    }

    /** 解析 body 中的分享队伍：兼容 familyIds 数组与旧 familyId 单值 */
    private List<Long> parseFamilyIds(Map<String, Object> body) {
        List<Long> ids = new java.util.ArrayList<>();
        Object raw = body.get("familyIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    Long v = Long.valueOf(String.valueOf(o));
                    if (!ids.contains(v)) ids.add(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (ids.isEmpty() && body.get("familyId") != null) {
            try {
                ids.add(Long.valueOf(body.get("familyId").toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    /** 校验分享队伍均为作者已加入的队伍 */
    private void validateMyFamilies(List<Long> familyIds, Long uid) {
        if (familyIds.isEmpty()) return;
        Long cnt = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .in(FamilyMember::getFamilyId, familyIds)
                .eq(FamilyMember::getUserId, uid)
                .eq(FamilyMember::getStatus, "APPROVED"));
        if (cnt == null || cnt < familyIds.size()) {
            throw new BizException("只能分享到自己所在的队伍");
        }
    }

    /** 全量替换菜谱的队伍分享记录 */
    private void saveFamilyShares(Long recipeId, List<Long> familyIds) {
        recipeFamilyShareMapper.delete(new LambdaQueryWrapper<RecipeFamilyShare>()
                .eq(RecipeFamilyShare::getRecipeId, recipeId));
        for (Long fid : familyIds) {
            RecipeFamilyShare s = new RecipeFamilyShare();
            s.setRecipeId(recipeId);
            s.setFamilyId(fid);
            recipeFamilyShareMapper.insert(s);
        }
    }

    /** 解析 body 中的食材列表（含 type/克数），失败字段静默置空 */
    private List<Ingredient> parseIngredients(Long recipeId, Object raw) {
        List<Ingredient> out = new java.util.ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        int i = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Ingredient ing = new Ingredient();
            ing.setRecipeId(recipeId);
            ing.setName(m.get("name") == null ? null : String.valueOf(m.get("name")));
            ing.setAmount(m.get("amount") == null ? null : String.valueOf(m.get("amount")));
            ing.setUnit(m.get("unit") == null ? null : String.valueOf(m.get("unit")));
            ing.setType("SEASONING".equals(m.get("type")) ? "SEASONING" : "FOOD");
            try {
                ing.setAmountGrams(m.get("amountGrams") == null ? null
                        : Double.valueOf(String.valueOf(m.get("amountGrams"))).intValue());
            } catch (NumberFormatException ignored) {
            }
            ing.setSort(i++);
            out.add(ing);
        }
        return out;
    }

    /**
     * 热量计算并冗余回填 recipe（total=null 表示未计算，如无可计食材）。
     * 用 UpdateWrapper 显式 set 三列：null 也要能覆盖旧值，updateById 会跳过 null 字段。
     */
    private void applyCalories(Recipe recipe, List<Ingredient> ings) {
        FoodCalorieMatchService.CalorieSummary cal = calorieMatchService.calculateAndFill(ings, recipe.getServings());
        recipe.setTotalCalories(cal.totalCalories());
        recipe.setCaloriesPerServing(cal.caloriesPerServing());
        recipe.setCaloriesApprox(cal.approx());
        recipeMapper.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Recipe>()
                .eq(Recipe::getId, recipe.getId())
                .set(Recipe::getTotalCalories, cal.totalCalories())
                .set(Recipe::getCaloriesPerServing, cal.caloriesPerServing())
                .set(Recipe::getCaloriesApprox, cal.approx()));
    }

    /** 从 body 的子列表（如 ingredients/steps）抽取指定字段文本 */
    private List<String> collectTexts(Map<String, Object> body, String listKey, String field) {
        List<String> out = new java.util.ArrayList<>();
        try {
            Object raw = body.get(listKey);
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get(field) != null) {
                        out.add(String.valueOf(m.get(field)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 解析 covers（入参 List 或 JSON 字符串均兼容），非法/超限时收敛：最多4张 */
    private List<String> parseCovers(Object raw) {
        List<String> out = new java.util.ArrayList<>();
        try {
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
                }
            } else if (raw instanceof String s && !s.isBlank()) {
                out = objectMapper.readValue(s, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return out.stream().distinct().limit(4).toList();
    }

    /** covers 列表 → JSON 字符串（空列表存 null） */
    private String toCoversJson(List<String> covers) {
        if (covers == null || covers.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(covers);
        } catch (Exception e) {
            throw new BizException("展示图参数不合法");
        }
    }
}
