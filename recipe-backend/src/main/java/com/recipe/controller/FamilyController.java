package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Family;
import com.recipe.entity.FamilyInvite;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.FridgeItem;
import com.recipe.entity.MealRequest;
import com.recipe.entity.Recipe;
import com.recipe.entity.RecipeFamilyShare;
import com.recipe.entity.User;
import com.recipe.mapper.FamilyInviteMapper;
import com.recipe.mapper.FamilyMapper;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.FridgeItemMapper;
import com.recipe.mapper.MealRequestMapper;
import com.recipe.mapper.RecipeFamilyShareMapper;
import com.recipe.mapper.RecipeMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 队伍（家庭/团队）：创建/搜索/加入（邀请码与微信分享均直通）/成员管理/邀请（带有效期）
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FamilyInviteMapper familyInviteMapper;
    private final UserMapper userMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeFamilyShareMapper recipeFamilyShareMapper;
    private final FridgeItemMapper fridgeItemMapper;
    private final MealRequestMapper mealRequestMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 创建圈层（默认需审批；生成 join_code；创建者即 OWNER） */
    @PostMapping
    @Transactional
    public ApiResult<Long> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Family family = new Family();
        family.setName((String) body.get("name"));
        family.setAvatar(body.get("avatar") == null ? null : body.get("avatar").toString());
        family.setOwnerId(uid);
        family.setJoinCode("FAM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        family.setMaxMembers(body.get("maxMembers") == null ? 99 : Integer.parseInt(body.get("maxMembers").toString()));
        family.setMemberCount(1);
        family.setApprovalRequired(body.get("approvalRequired") == null || Boolean.parseBoolean(body.get("approvalRequired").toString()));
        if (family.getName() == null || family.getName().isBlank()) {
            throw new BizException("圈层名称不能为空");
        }
        familyMapper.insert(family);

        FamilyMember owner = new FamilyMember();
        owner.setFamilyId(family.getId());
        owner.setUserId(uid);
        owner.setRole(Constants.ROLE_OWNER);
        owner.setStatus(Constants.MEMBER_APPROVED);
        owner.setJoinedAt(LocalDateTime.now());
        familyMemberMapper.insert(owner);
        return ApiResult.ok(family.getId());
    }

    /** 我的圈子 */
    @GetMapping("/mine")
    public ApiResult<List<Map<String, Object>>> mine(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<FamilyMember> members = familyMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, uid).eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED));
        if (members.isEmpty()) {
            return ApiResult.ok(new java.util.ArrayList<>());
        }
        List<Long> familyIds = members.stream().map(FamilyMember::getFamilyId).distinct().toList();
        Map<Long, Family> familyMap = familyMapper.selectBatchIds(familyIds).stream()
                .collect(Collectors.toMap(Family::getId, f -> f));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (FamilyMember m : members) {
            Family f = familyMap.get(m.getFamilyId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("family", f);
            item.put("role", m.getRole());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** 搜 join_code 查圈层（公开） */
    @GetMapping("/search")
    public ApiResult<Family> search(@RequestParam String joinCode) {
        Family family = familyMapper.selectOne(new LambdaQueryWrapper<Family>().eq(Family::getJoinCode, joinCode));
        if (family == null) {
            throw new BizException(404, "未找到该圈层，请核对圈层ID");
        }
        return ApiResult.ok(family);
    }

    /** 凭 join_code 加入（邀请码即邀请：直接成为正式成员） */
    @PostMapping("/join")
    @Transactional
    public ApiResult<Map<String, Object>> join(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Family family = familyMapper.selectOne(new LambdaQueryWrapper<Family>()
                .eq(Family::getJoinCode, String.valueOf(body.get("joinCode"))));
        if (family == null) {
            throw new BizException(404, "未找到该队伍，请核对邀请码");
        }
        return doJoin(family, uid);
    }

    /** 凭邀请 token 加入（生成邀请分享卡片/二维码落地，校验有效期） */
    @PostMapping("/join-by-invite")
    @Transactional
    public ApiResult<Map<String, Object>> joinByInvite(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        String token = String.valueOf(body.get("invite"));
        FamilyInvite invite = familyInviteMapper.selectOne(new LambdaQueryWrapper<FamilyInvite>().eq(FamilyInvite::getToken, token));
        if (invite == null) {
            throw new BizException(404, "邀请不存在");
        }
        if (!"ACTIVE".equals(invite.getStatus())) {
            throw new BizException("邀请已作废");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("邀请已过期，请联系群主重新生成");
        }
        Family family = familyMapper.selectById(invite.getFamilyId());
        if (family == null) {
            throw new BizException(404, "队伍不存在");
        }
        return doJoin(family, uid);
    }

    /** 微信转发链接落地：凭队伍 id 直接加入（分享即邀请） */
    @PostMapping("/{id}/join")
    @Transactional
    public ApiResult<Map<String, Object>> joinById(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Family family = familyMapper.selectById(id);
        if (family == null) {
            throw new BizException(404, "队伍不存在或已解散");
        }
        return doJoin(family, uid);
    }

    /**
     * 统一加入逻辑：分享即邀请，加入即正式成员（仅受成员上限保护）。
     * 已是成员时幂等返回 alreadyMember=true，前端据此展示「已在队伍中」。
     */
    private ApiResult<Map<String, Object>> doJoin(Family family, Long uid) {
        FamilyMember exist = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, family.getId()).eq(FamilyMember::getUserId, uid));
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("familyId", family.getId());
        if (exist != null && Constants.MEMBER_APPROVED.equals(exist.getStatus())) {
            result.put("alreadyMember", true);
            return ApiResult.ok(result);
        }
        if (exist == null) {
            exist = new FamilyMember();
            exist.setFamilyId(family.getId());
            exist.setUserId(uid);
            exist.setRole(Constants.ROLE_MEMBER);
        }
        exist.setStatus(Constants.MEMBER_APPROVED);
        exist.setJoinedAt(LocalDateTime.now());
        try {
            if (exist.getId() == null) {
                familyMemberMapper.insert(exist);
            } else {
                familyMemberMapper.updateById(exist);
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重复加入：uk_family_user 唯一索引兜底，按已加入处理（事务内无其他写，直接返回安全）
            result.put("alreadyMember", true);
            return ApiResult.ok(result);
        }
        // 原子计数防并发超额
        int updated = familyMapper.update(null, new LambdaUpdateWrapper<Family>()
                .eq(Family::getId, family.getId())
                .lt(Family::getMemberCount, family.getMaxMembers())
                .setSql("member_count = member_count + 1"));
        if (updated == 0) {
            throw new BizException("队伍已满，无法加入");
        }
        result.put("alreadyMember", false);
        return ApiResult.ok(result);
    }

    /** 圈层详情（成员可见） */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Family family = familyMapper.selectById(id);
        if (family == null) {
            throw new BizException(404, "圈层不存在");
        }
        FamilyMember me = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, id).eq(FamilyMember::getUserId, AuthInterceptor.uid(request)));
        if (me == null || !Constants.MEMBER_APPROVED.equals(me.getStatus())) {
            throw new BizException(403, "仅圈层成员可见");
        }
        return ApiResult.ok(Map.of("family", family, "myRole", me.getRole()));
    }

    /** 队伍主页-圈员菜品：仅"分享到本队伍"的菜谱（公开/广场的菜品不出现在队伍中） */
    @GetMapping("/{id}/recipes")
    public ApiResult<List<Map<String, Object>>> recipes(@PathVariable Long id, HttpServletRequest request) {
        requireMember(id, AuthInterceptor.uid(request));
        List<Long> memberIds = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, id)
                        .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED))
                .stream().map(FamilyMember::getUserId).distinct().toList();
        // 指定分享到本队伍的菜谱 id（share 表）
        List<Long> sharedRecipeIds = recipeFamilyShareMapper.selectList(new LambdaQueryWrapper<RecipeFamilyShare>()
                        .eq(RecipeFamilyShare::getFamilyId, id))
                .stream().map(RecipeFamilyShare::getRecipeId).distinct().toList();
        // 注意：嵌套 or 内条件全空会渲染成 "OR OR" 语法错误，空集合分支必须在 Java 侧跳过
        List<Recipe> recipes = recipeMapper.selectList(new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getStatus, Constants.RECIPE_PUBLISHED)
                .eq(Recipe::getVisibility, Constants.VISIBILITY_FAMILY)
                .and(w -> {
                    // 指定分享本队伍（存量 family_id 或 share 表）
                    w.eq(Recipe::getFamilyId, id);
                    if (!sharedRecipeIds.isEmpty()) {
                        w.or(f -> f.in(Recipe::getId, sharedRecipeIds));
                    }
                    // 全部队伍：作者须是本队成员
                    if (!memberIds.isEmpty()) {
                        w.or(f -> f.eq(Recipe::getShareAllFamilies, true).in(Recipe::getAuthorId, memberIds));
                    }
                })
                .orderByDesc(Recipe::getId)
                .last("LIMIT 100"));
        List<Long> authorIds = recipes.stream().map(Recipe::getAuthorId).distinct().toList();
        Map<Long, User> authorMap = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Recipe r : recipes) {
            User author = authorMap.get(r.getAuthorId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", r.getId());
            item.put("title", r.getTitle());
            item.put("cover", r.getCover());
            item.put("covers", parseCovers(r.getCovers()));
            item.put("visibility", r.getVisibility());
            item.put("avgStars", r.getAvgStars());
            item.put("authorId", r.getAuthorId());
            item.put("authorNickname", author == null ? null : author.getNickname());
            item.put("authorAvatar", author == null ? null : author.getAvatar());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** covers JSON（字符串或数组）→ List<String>，最多 4 张 */
    private List<String> parseCovers(Object raw) {
        List<String> out = new java.util.ArrayList<>();
        try {
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
                }
            } else if (raw instanceof String s && !s.isBlank()) {
                out = objectMapper.readValue(s, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return out.stream().distinct().limit(4).toList();
    }

    /** 成员列表（含待审） */
    @GetMapping("/{id}/members")
    public ApiResult<List<Map<String, Object>>> members(@PathVariable Long id, HttpServletRequest request) {
        requireMember(id, AuthInterceptor.uid(request));
        List<FamilyMember> members = familyMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, id).orderByAsc(FamilyMember::getId));
        List<Long> userIds = members.stream().map(FamilyMember::getUserId).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (FamilyMember m : members) {
            var user = userMap.get(m.getUserId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("memberId", m.getId());
            item.put("userId", m.getUserId());
            item.put("role", m.getRole());
            item.put("status", m.getStatus());
            item.put("joinedAt", m.getJoinedAt());
            item.put("nickname", user == null ? null : user.getNickname());
            item.put("avatar", user == null ? null : user.getAvatar());
            item.put("avatarChar", com.recipe.dto.UserDTO.avatarChar(user));
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** 审批：通过/拒绝待审成员（存量待审数据处理；新加入已全部直通） */
    @PostMapping("/{id}/members/{memberId}/approve")
    @Transactional
    public ApiResult<Void> approve(@PathVariable Long id, @PathVariable Long memberId,
                                   @RequestParam boolean pass, HttpServletRequest request) {
        requireOwnerOrAdmin(id, AuthInterceptor.uid(request));
        FamilyMember target = familyMemberMapper.selectById(memberId);
        if (target == null || !target.getFamilyId().equals(id)) {
            throw new BizException(404, "成员不存在");
        }
        if (pass) {
            Family family = familyMapper.selectById(id);
            target.setStatus(Constants.MEMBER_APPROVED);
            target.setJoinedAt(LocalDateTime.now());
            int updated = familyMapper.update(null, new LambdaUpdateWrapper<Family>()
                    .eq(Family::getId, id)
                    .lt(Family::getMemberCount, family.getMaxMembers())
                    .setSql("member_count = member_count + 1"));
            if (updated == 0) {
                throw new BizException("队伍已满，无法通过");
            }
        } else {
            target.setStatus(Constants.MEMBER_REJECTED);
        }
        familyMemberMapper.updateById(target);
        return ApiResult.ok();
    }

    /** 删除/移出成员（OWNER 可删任意；ADMIN 可删非OWNER） */
    @DeleteMapping("/{id}/members/{memberId}")
    @Transactional
    public ApiResult<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FamilyMember me = requireMember(id, uid);
        FamilyMember target = familyMemberMapper.selectById(memberId);
        if (target == null || !target.getFamilyId().equals(id)) {
            throw new BizException(404, "成员不存在");
        }
        if (Constants.ROLE_OWNER.equals(target.getRole())) {
            throw new BizException(403, "不能移除群主");
        }
        if (!Constants.ROLE_OWNER.equals(me.getRole()) && !Constants.ROLE_ADMIN.equals(me.getRole())) {
            throw new BizException(403, "仅群主/管理员可移除成员");
        }
        if (Constants.ROLE_ADMIN.equals(me.getRole()) && Constants.ROLE_ADMIN.equals(target.getRole())) {
            throw new BizException(403, "管理员不能移除管理员");
        }
        familyMemberMapper.deleteById(memberId);
        if (Constants.MEMBER_APPROVED.equals(target.getStatus())) {
            familyMapper.update(null, new LambdaUpdateWrapper<Family>()
                    .eq(Family::getId, id)
                    .setSql("member_count = GREATEST(member_count - 1, 0)"));
        }
        return ApiResult.ok();
    }

    /** 改名/改上限/切换审批开关（OWNER/ADMIN） */
    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FamilyMember me = requireMember(id, uid);
        if (!Constants.ROLE_OWNER.equals(me.getRole()) && !Constants.ROLE_ADMIN.equals(me.getRole())) {
            throw new BizException(403, "仅群主/管理员可修改");
        }
        Family family = familyMapper.selectById(id);
        if (body.get("name") != null) family.setName((String) body.get("name"));
        if (body.get("avatar") != null) family.setAvatar(body.get("avatar").toString().isBlank() ? null : body.get("avatar").toString());
        if (body.get("maxMembers") != null) {
            int max = Integer.parseInt(body.get("maxMembers").toString());
            if (max < 1 || max > 99) throw new BizException("成员上限需在 1-99 之间");
            if (family.getMemberCount() > max) throw new BizException("当前成员数已超过新上限");
            family.setMaxMembers(max);
        }
        if (body.get("approvalRequired") != null) {
            family.setApprovalRequired(Boolean.parseBoolean(body.get("approvalRequired").toString()));
        }
        familyMapper.updateById(family);
        return ApiResult.ok();
    }

    /**
     * 解散队伍（仅群主）。
     * 成员、邀请、心愿单、队伍冰箱随队伍删除；圈内可见菜谱转为私密保留（作者仍可在我的菜谱查看）。
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResult<Void> dissolve(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Family family = familyMapper.selectById(id);
        if (family == null) {
            throw new BizException(404, "队伍不存在或已解散");
        }
        if (!uid.equals(family.getOwnerId())) {
            throw new BizException(403, "仅群主可解散队伍");
        }
        // 圈内可见菜谱转私密，保留作者内容
        List<Recipe> familyRecipes = recipeMapper.selectList(new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getFamilyId, id));
        for (Recipe r : familyRecipes) {
            r.setVisibility(Constants.VISIBILITY_PRIVATE);
            r.setFamilyId(null);
            recipeMapper.updateById(r);
        }
        familyMemberMapper.delete(new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, id));
        familyInviteMapper.delete(new LambdaQueryWrapper<FamilyInvite>().eq(FamilyInvite::getFamilyId, id));
        mealRequestMapper.delete(new LambdaQueryWrapper<MealRequest>().eq(MealRequest::getFamilyId, id));
        fridgeItemMapper.delete(new LambdaQueryWrapper<FridgeItem>().eq(FridgeItem::getFamilyId, id));
        familyMapper.deleteById(id);
        return ApiResult.ok();
    }

    /** 生成邀请（分享卡片/二维码，带有效期） */
    @PostMapping("/{id}/invites")
    public ApiResult<FamilyInvite> createInvite(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        FamilyMember me = requireMember(id, AuthInterceptor.uid(request));
        if (!Constants.ROLE_OWNER.equals(me.getRole()) && !Constants.ROLE_ADMIN.equals(me.getRole())) {
            throw new BizException(403, "仅群主/管理员可生成邀请");
        }
        int validDays = body.get("validDays") == null ? 7 : Integer.parseInt(body.get("validDays").toString());
        if (validDays != 1 && validDays != 7 && validDays != 30) {
            throw new BizException("有效期仅支持 1/7/30 天");
        }
        FamilyInvite invite = new FamilyInvite();
        invite.setFamilyId(id);
        invite.setType("QR_CODE".equals(body.get("type")) ? "QR_CODE" : "SHARE_CARD");
        invite.setToken("INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        invite.setValidDays(validDays);
        invite.setExpiresAt(LocalDateTime.now().plusDays(validDays));
        invite.setStatus("ACTIVE");
        invite.setCreatedBy(AuthInterceptor.uid(request));
        familyInviteMapper.insert(invite);
        return ApiResult.ok(invite);
    }

    /** 邀请列表 */
    @GetMapping("/{id}/invites")
    public ApiResult<List<FamilyInvite>> invites(@PathVariable Long id, HttpServletRequest request) {
        requireMember(id, AuthInterceptor.uid(request));
        return ApiResult.ok(familyInviteMapper.selectList(
                new LambdaQueryWrapper<FamilyInvite>().eq(FamilyInvite::getFamilyId, id).orderByDesc(FamilyInvite::getId)));
    }

    /** 作废邀请 */
    @DeleteMapping("/invites/{inviteId}")
    public ApiResult<Void> revokeInvite(@PathVariable Long inviteId, HttpServletRequest request) {
        FamilyInvite invite = familyInviteMapper.selectById(inviteId);
        if (invite == null) {
            throw new BizException(404, "邀请不存在");
        }
        requireOwnerOrAdmin(invite.getFamilyId(), AuthInterceptor.uid(request));
        invite.setStatus("REVOKED");
        familyInviteMapper.updateById(invite);
        return ApiResult.ok();
    }

    private FamilyMember requireMember(Long familyId, Long uid) {
        if (uid == null) throw new BizException(401, "请先登录");
        FamilyMember me = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId).eq(FamilyMember::getUserId, uid));
        if (me == null || !Constants.MEMBER_APPROVED.equals(me.getStatus())) {
            throw new BizException(403, "仅圈层成员可见");
        }
        return me;
    }

    private void requireOwnerOrAdmin(Long familyId, Long uid) {
        FamilyMember me = requireMember(familyId, uid);
        if (!Constants.ROLE_OWNER.equals(me.getRole()) && !Constants.ROLE_ADMIN.equals(me.getRole())) {
            throw new BizException(403, "仅群主/管理员可操作");
        }
    }
}
