package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关注：关注/取关切换、我的关注与粉丝列表、他人主页的关注/粉丝列表、批量关注状态。
 * 他人列表受主页可见性约束；游客调用 status 返回空映射。
 */
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final UserFollowMapper followMapper;
    private final UserMapper userMapper;
    private final RecipeMapper recipeMapper;
    private final FamilyMemberMapper familyMemberMapper;

    /** 关注/取关切换 */
    @PostMapping("/{uid}/toggle")
    public ApiResult<Map<String, Object>> toggle(@PathVariable Long uid, HttpServletRequest request) {
        Long me = AuthInterceptor.uid(request);
        if (me == null) {
            throw new BizException(401, "请先登录");
        }
        if (me.equals(uid)) {
            throw new BizException("不能关注自己");
        }
        User target = userMapper.selectById(uid);
        if (target == null) {
            throw new BizException(404, "用户不存在");
        }
        UserFollow exist = followMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, me).eq(UserFollow::getFolloweeId, uid));
        boolean followed;
        if (exist == null) {
            UserFollow f = new UserFollow();
            f.setFollowerId(me);
            f.setFolloweeId(uid);
            followMapper.insert(f);
            followed = true;
        } else {
            followMapper.deleteById(exist.getId());
            followed = false;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed);
        result.put("fansCount", followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, uid)));
        return ApiResult.ok(result);
    }

    /** 我的关注/粉丝列表 */
    @GetMapping("/my")
    public ApiResult<List<Map<String, Object>>> my(@RequestParam(defaultValue = "following") String type,
                                                   HttpServletRequest request) {
        Long me = AuthInterceptor.uid(request);
        if (me == null) {
            throw new BizException(401, "请先登录");
        }
        return ApiResult.ok(listOf(me, "fans".equals(type), me));
    }

    /** 他人主页的关注/粉丝列表（受主页可见性约束） */
    @GetMapping("/{uid}/list")
    public ApiResult<List<Map<String, Object>>> list(@PathVariable Long uid,
                                                     @RequestParam(defaultValue = "following") String type,
                                                     HttpServletRequest request) {
        User target = userMapper.selectById(uid);
        if (target == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!ProfileAccess.accessible(target, AuthInterceptor.uid(request), familyMemberMapper)) {
            throw new BizException(403, "该用户主页不可见");
        }
        return ApiResult.ok(listOf(uid, "fans".equals(type), AuthInterceptor.uid(request)));
    }

    /** 批量查关注状态（游客返回空映射；不校验目标用户隐私，只暴露当前登录者自己的关注关系） */
    @GetMapping("/status")
    public ApiResult<Map<String, Boolean>> status(@RequestParam(required = false) String uids,
                                                  HttpServletRequest request) {
        Map<String, Boolean> result = new HashMap<>();
        Long me = AuthInterceptor.uid(request);
        if (me == null || uids == null || uids.isBlank()) {
            return ApiResult.ok(result);
        }
        for (String s : uids.split(",")) {
            Long id;
            try {
                id = Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (id.equals(me)) {
                continue;
            }
            result.put(String.valueOf(id), followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, me).eq(UserFollow::getFolloweeId, id)) > 0);
        }
        return ApiResult.ok(result);
    }

    /** 组装关注/粉丝列表：用户信息 + 公开菜谱数 + 当前查看者是否已关注 */
    private List<Map<String, Object>> listOf(Long uid, boolean fans, Long viewer) {
        List<UserFollow> relations = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(fans ? UserFollow::getFolloweeId : UserFollow::getFollowerId, uid)
                .orderByDesc(UserFollow::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = relations.stream()
                .map(fans ? UserFollow::getFollowerId : UserFollow::getFolloweeId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        // 公开菜谱数（一次分组统计，避免 N+1）
        Map<Long, Long> recipeCountMap = new HashMap<>();
        recipeMapper.selectList(new LambdaQueryWrapper<Recipe>()
                        .in(Recipe::getAuthorId, userIds)
                        .eq(Recipe::getVisibility, Constants.VISIBILITY_PUBLIC)
                        .eq(Recipe::getStatus, Constants.RECIPE_PUBLISHED)
                        .select(Recipe::getAuthorId))
                .forEach(r -> recipeCountMap.merge(r.getAuthorId(), 1L, Long::sum));
        // 查看者对这些用户的关注状态（用于列表行内 关注/已关注 按钮）
        Set<Long> followedIds = new HashSet<>();
        if (viewer != null) {
            followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, viewer)
                            .in(UserFollow::getFolloweeId, userIds))
                    .forEach(f -> followedIds.add(f.getFolloweeId()));
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (UserFollow f : relations) {
            Long otherId = fans ? f.getFollowerId() : f.getFolloweeId();
            User u = userMap.get(otherId);
            if (u == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("userId", u.getId());
            item.put("nickname", u.getNickname());
            item.put("avatar", u.getAvatar());
            item.put("avatarChar", com.recipe.dto.UserDTO.avatarChar(u));
            item.put("profileVisibility", u.getProfileVisibility());
            item.put("recipeCount", recipeCountMap.getOrDefault(u.getId(), 0L));
            item.put("followed", followedIds.contains(u.getId()));
            item.put("isSelf", viewer != null && viewer.equals(u.getId()));
            result.add(item);
        }
        return result;
    }
}
