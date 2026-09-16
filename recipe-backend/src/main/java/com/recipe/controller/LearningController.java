package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Family;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.LearningRecipe;
import com.recipe.entity.MealRequest;
import com.recipe.mapper.FamilyMapper;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.LearningRecipeMapper;
import com.recipe.mapper.MealRequestMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 待学习菜谱：心愿单「我也不会」移入的个人清单；发布菜谱后自行移除
 */
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningRecipeMapper learningRecipeMapper;
    private final MealRequestMapper mealRequestMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;

    /** 心愿单「我也不会」：心愿移出队伍心愿单，进入我的待学习清单（同人同名幂等） */
    @PostMapping("/from-meal/{mealId}")
    @Transactional
    public ApiResult<Void> fromMeal(@PathVariable Long mealId, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        MealRequest mr = mealRequestMapper.selectById(mealId);
        if (mr == null) throw new BizException(404, "心愿不存在");
        if (!AuthInterceptor.isAdmin(request)) {
            Long cnt = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                    .eq(FamilyMember::getFamilyId, mr.getFamilyId())
                    .eq(FamilyMember::getUserId, uid)
                    .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED));
            if (cnt == null || cnt == 0) throw new BizException(403, "仅队伍成员可操作");
        }
        Long dup = learningRecipeMapper.selectCount(new LambdaQueryWrapper<LearningRecipe>()
                .eq(LearningRecipe::getUserId, uid)
                .eq(LearningRecipe::getDishName, mr.getDishName()));
        if (dup == null || dup == 0) {
            Family f = familyMapper.selectById(mr.getFamilyId());
            LearningRecipe lr = new LearningRecipe();
            lr.setUserId(uid);
            lr.setDishName(mr.getDishName());
            lr.setSourceFamilyId(mr.getFamilyId());
            lr.setSourceFamilyName(f == null ? null : f.getName());
            learningRecipeMapper.insert(lr);
        }
        mealRequestMapper.deleteById(mealId);
        return ApiResult.ok();
    }

    /** 我的待学习清单 */
    @GetMapping
    public ApiResult<List<LearningRecipe>> list(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        return ApiResult.ok(learningRecipeMapper.selectList(new LambdaQueryWrapper<LearningRecipe>()
                .eq(LearningRecipe::getUserId, uid)
                .orderByDesc(LearningRecipe::getId)));
    }

    /** 移除一条（本人） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        LearningRecipe lr = learningRecipeMapper.selectById(id);
        if (lr == null) throw new BizException(404, "条目不存在");
        if (!lr.getUserId().equals(uid)) throw new BizException(403, "无权操作");
        learningRecipeMapper.deleteById(id);
        return ApiResult.ok();
    }
}
