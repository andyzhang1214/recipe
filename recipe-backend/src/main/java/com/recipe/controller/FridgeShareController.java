package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.FridgeInvite;
import com.recipe.entity.FridgeMember;
import com.recipe.entity.User;
import com.recipe.mapper.FridgeInviteMapper;
import com.recipe.mapper.FridgeMemberMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 个人冰箱共享：生成邀请码（类似队伍邀请）、凭码加入、共享成员管理 */
@RestController
@RequestMapping("/api/fridge-share")
@RequiredArgsConstructor
public class FridgeShareController {

    private final FridgeInviteMapper fridgeInviteMapper;
    private final FridgeMemberMapper fridgeMemberMapper;
    private final UserMapper userMapper;

    /** 生成冰箱邀请码（仅冰箱主人） */
    @PostMapping("/invites")
    public ApiResult<FridgeInvite> createInvite(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        int validDays = 7;
        if (body.get("validDays") != null) {
            try {
                validDays = Integer.parseInt(body.get("validDays").toString());
            } catch (NumberFormatException ignored) {
            }
        }
        if (validDays < 1 || validDays > 30) {
            throw new BizException("有效期需在 1-30 天之间");
        }
        FridgeInvite invite = new FridgeInvite();
        invite.setOwnerId(uid);
        invite.setToken("FR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        invite.setValidDays(validDays);
        invite.setExpiresAt(LocalDateTime.now().plusDays(validDays));
        invite.setStatus("ACTIVE");
        fridgeInviteMapper.insert(invite);
        return ApiResult.ok(invite);
    }

    /** 我的冰箱邀请列表 */
    @GetMapping("/invites")
    public ApiResult<List<FridgeInvite>> myInvites(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<FridgeInvite> list = fridgeInviteMapper.selectList(new LambdaQueryWrapper<FridgeInvite>()
                .eq(FridgeInvite::getOwnerId, uid)
                .orderByDesc(FridgeInvite::getId)
                .last("LIMIT 30"));
        // 过期视为已失效
        for (FridgeInvite inv : list) {
            if ("ACTIVE".equals(inv.getStatus()) && inv.getExpiresAt().isBefore(LocalDateTime.now())) {
                inv.setStatus("EXPIRED");
            }
        }
        return ApiResult.ok(list);
    }

    /** 作废邀请 */
    @DeleteMapping("/invites/{id}")
    public ApiResult<Void> revokeInvite(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FridgeInvite invite = fridgeInviteMapper.selectById(id);
        if (invite == null || !invite.getOwnerId().equals(uid)) {
            throw new BizException(404, "邀请不存在");
        }
        invite.setStatus("REVOKED");
        fridgeInviteMapper.updateById(invite);
        return ApiResult.ok();
    }

    /** 凭邀请码加入共享冰箱（幂等：已在共享中直接返回） */
    @PostMapping("/join")
    public ApiResult<Map<String, Object>> join(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        String token = body.get("token") == null ? null : body.get("token").toString().trim().toUpperCase();
        if (token == null || token.isBlank()) {
            throw new BizException("请输入邀请码");
        }
        FridgeInvite invite = fridgeInviteMapper.selectOne(new LambdaQueryWrapper<FridgeInvite>()
                .eq(FridgeInvite::getToken, token));
        if (invite == null) {
            throw new BizException("邀请码不存在");
        }
        if (!"ACTIVE".equals(invite.getStatus())) {
            throw new BizException("邀请码已失效");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("邀请码已过期");
        }
        if (invite.getOwnerId().equals(uid)) {
            throw new BizException("不能加入自己的冰箱");
        }
        Long cnt = fridgeMemberMapper.selectCount(new LambdaQueryWrapper<FridgeMember>()
                .eq(FridgeMember::getOwnerId, invite.getOwnerId())
                .eq(FridgeMember::getUserId, uid));
        if (cnt == null || cnt == 0) {
            FridgeMember member = new FridgeMember();
            member.setOwnerId(invite.getOwnerId());
            member.setUserId(uid);
            fridgeMemberMapper.insert(member);
        }
        User owner = userMapper.selectById(invite.getOwnerId());
        return ApiResult.ok(Map.of(
                "ownerId", invite.getOwnerId(),
                "ownerName", owner == null ? "" : String.valueOf(owner.getNickname()),
                "alreadyMember", cnt != null && cnt > 0));
    }

    /** 我的冰箱共享成员列表（带昵称/头像） */
    @GetMapping("/members")
    public ApiResult<List<Map<String, Object>>> members(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<FridgeMember> list = fridgeMemberMapper.selectList(new LambdaQueryWrapper<FridgeMember>()
                .eq(FridgeMember::getOwnerId, uid)
                .orderByDesc(FridgeMember::getId));
        List<Long> userIds = list.stream().map(FridgeMember::getUserId).toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (FridgeMember m : list) {
            User u = userMap.get(m.getUserId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", m.getId());
            item.put("userId", m.getUserId());
            item.put("nickname", u == null ? null : u.getNickname());
            item.put("avatar", u == null ? null : u.getAvatar());
            item.put("createdAt", m.getCreatedAt());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    /** 移除共享成员（仅主人） */
    @DeleteMapping("/members/{id}")
    public ApiResult<Void> removeMember(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FridgeMember member = fridgeMemberMapper.selectById(id);
        if (member == null || !member.getOwnerId().equals(uid)) {
            throw new BizException(404, "成员不存在");
        }
        fridgeMemberMapper.deleteById(id);
        return ApiResult.ok();
    }

    /** 共享给我的冰箱列表（主人昵称/头像） */
    @GetMapping("/shared-with-me")
    public ApiResult<List<Map<String, Object>>> sharedWithMe(HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        List<FridgeMember> list = fridgeMemberMapper.selectList(new LambdaQueryWrapper<FridgeMember>()
                .eq(FridgeMember::getUserId, uid)
                .orderByDesc(FridgeMember::getId));
        List<Long> ownerIds = list.stream().map(FridgeMember::getOwnerId).toList();
        Map<Long, User> ownerMap = ownerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ownerIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (FridgeMember m : list) {
            User owner = ownerMap.get(m.getOwnerId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("ownerId", m.getOwnerId());
            item.put("ownerName", owner == null ? null : owner.getNickname());
            item.put("ownerAvatar", owner == null ? null : owner.getAvatar());
            result.add(item);
        }
        return ApiResult.ok(result);
    }
}
