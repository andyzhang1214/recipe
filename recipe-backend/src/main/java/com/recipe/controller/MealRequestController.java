package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Family;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.MealRequest;
import com.recipe.entity.User;
import com.recipe.mapper.FamilyMapper;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.MealRequestMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 群员点餐「我想吃」：
 * - POST /api/meal-requests       成员提交（校验圈层成员 + 内容安全）
 * - GET  /api/meal-requests/overview  我所在圈层的点餐汇总（一次取全，驱动家庭页 + Tab 角标）
 * - POST /api/meal-requests/{id}/done  群主标记已做
 * - DELETE /api/meal-requests/{id}     本人或群主删除
 */
@RestController
@RequestMapping("/api/meal-requests")
@RequiredArgsConstructor
public class MealRequestController {

    private final MealRequestMapper mealRequestMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final UserMapper userMapper;
    private final com.recipe.service.WxSecurityService wxSecurity;

    /** 我所在圈层的点餐汇总：每个圈层返回角色 + 点餐列表（PENDING 在前，含点餐人昵称/是否我的） */
    @GetMapping("/overview")
    public ApiResult<List<Map<String, Object>>> overview(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<FamilyMember> members = familyMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, uid)
                        .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED)
                        .orderByAsc(FamilyMember::getId));
        if (members.isEmpty()) {
            return ApiResult.ok(new ArrayList<>());
        }
        // 批量查圈层（消除 N+1）
        List<Long> familyIds = members.stream().map(FamilyMember::getFamilyId).distinct().toList();
        Map<Long, Family> familyMap = familyMapper.selectBatchIds(familyIds).stream()
                .collect(Collectors.toMap(Family::getId, f -> f));
        List<Map<String, Object>> out = new ArrayList<>();
        for (FamilyMember m : members) {
            Family f = familyMap.get(m.getFamilyId());
            if (f == null) continue;
            // 字母序 DONE < PENDING，倒序 → PENDING（待做）在前；限制单圈层条数防超大结果
            List<MealRequest> list = mealRequestMapper.selectList(
                    new LambdaQueryWrapper<MealRequest>()
                            .eq(MealRequest::getFamilyId, f.getId())
                            .orderByDesc(MealRequest::getStatus)
                            .orderByDesc(MealRequest::getId)
                            .last("LIMIT 50"));
            // 批量查点餐人（消除 N+1）
            List<Long> userIds = list.stream().map(MealRequest::getUserId).distinct().toList();
            Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                    : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
            List<Map<String, Object>> items = new ArrayList<>();
            for (MealRequest r : list) {
                User u = userMap.get(r.getUserId());
                Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("dishName", r.getDishName());
            item.put("note", r.getNote());
            item.put("status", r.getStatus());
            item.put("recipeId", r.getRecipeId());
            item.put("userId", r.getUserId());
                item.put("userName", u == null ? "-" : u.getNickname());
                item.put("createdAt", r.getCreatedAt());
                item.put("mine", r.getUserId().equals(uid));
                items.add(item);
            }
            Map<String, Object> circle = new HashMap<>();
            circle.put("familyId", f.getId());
            circle.put("familyName", f.getName());
            circle.put("role", m.getRole());
            circle.put("list", items);
            out.add(circle);
        }
        return ApiResult.ok(out);
    }

    /** 提交点餐（圈层 APPROVED 成员） */
    @PostMapping
    public ApiResult<Long> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Object fid = body.get("familyId");
        if (fid == null) throw new BizException("请选择圈子");
        Long familyId;
        try {
            familyId = Long.valueOf(fid.toString());
        } catch (NumberFormatException e) {
            throw new BizException("圈子参数不合法");
        }
        String dishName = (String) body.get("dishName");
        if (dishName == null || dishName.isBlank()) throw new BizException("想吃什么不能为空");
        Long cnt = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId)
                .eq(FamilyMember::getUserId, uid)
                .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED));
        if (cnt == null || cnt == 0) throw new BizException(403, "仅圈层成员可点餐");
        if (wxSecurity.checkText(dishName, request.getHeader("X-WX-OPENID"))) throw new BizException("内容包含违规信息");
        MealRequest mr = new MealRequest();
        mr.setFamilyId(familyId);
        mr.setUserId(uid);
        mr.setDishName(dishName.trim());
        mr.setNote((String) body.get("note"));
        mr.setStatus(Constants.MEAL_PENDING);
        mealRequestMapper.insert(mr);
        return ApiResult.ok(mr.getId());
    }

    /** 标记完成（心愿单-去完成：任何圈层成员发布菜谱后均可标记，可携带菜谱 id；总管理员亦可） */
    @PostMapping("/{id}/done")
    public ApiResult<Void> done(@PathVariable Long id,
                                @RequestBody(required = false) Map<String, Object> body,
                                HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        MealRequest mr = mealRequestMapper.selectById(id);
        if (mr == null) throw new BizException(404, "点餐不存在");
        if (!AuthInterceptor.isAdmin(request)) {
            Long cnt = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                    .eq(FamilyMember::getFamilyId, mr.getFamilyId())
                    .eq(FamilyMember::getUserId, uid)
                    .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED));
            if (cnt == null || cnt == 0) {
                throw new BizException(403, "仅圈层成员可标记完成");
            }
        }
        if (body != null && body.get("recipeId") != null) {
            try {
                mr.setRecipeId(Long.valueOf(body.get("recipeId").toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        mr.setStatus(Constants.MEAL_DONE);
        mealRequestMapper.updateById(mr);
        return ApiResult.ok();
    }

    /** 删除（点餐人本人 或 群主 或 总管理员） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        MealRequest mr = mealRequestMapper.selectById(id);
        if (mr == null) throw new BizException(404, "点餐不存在");
        Family f = familyMapper.selectById(mr.getFamilyId());
        boolean canDelete = mr.getUserId().equals(uid)
                || (f != null && f.getOwnerId().equals(uid))
                || AuthInterceptor.isAdmin(request);
        if (!canDelete) throw new BizException(403, "无权限删除");
        mealRequestMapper.deleteById(id);
        return ApiResult.ok();
    }
}
