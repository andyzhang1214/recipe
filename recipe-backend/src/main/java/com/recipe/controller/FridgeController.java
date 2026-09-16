package com.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.common.ApiResult;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.FridgeItem;
import com.recipe.entity.FridgeLog;
import com.recipe.entity.FridgeMember;
import com.recipe.entity.User;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.FridgeItemMapper;
import com.recipe.mapper.FridgeLogMapper;
import com.recipe.mapper.FridgeMemberMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 冰箱三种范围：
 * - 个人冰箱（默认）：仅本人；主人可通过邀请共享给他人
 * - 共享冰箱（ownerId=主人）：主人或被邀请成员可查看使用
 * - 队伍冰箱（familyId）：仅该队伍正式成员可查看使用
 * 所有增删改都写入 fridge_log 操作记录。
 */
@RestController
@RequestMapping("/api/fridge-items")
@RequiredArgsConstructor
public class FridgeController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FridgeController.class);

    private final FridgeItemMapper fridgeItemMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FridgeMemberMapper fridgeMemberMapper;
    private final FridgeLogMapper fridgeLogMapper;
    private final UserMapper userMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * sort=status 时按 已过期→快过期→新鲜 置顶，同组按到期日升序。
     * familyId → 队伍冰箱；ownerId → 查看某人的共享冰箱；缺省 → 我的个人冰箱。
     */
    @GetMapping
    public ApiResult<List<FridgeItem>> list(@RequestParam(required = false) String sort,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) Long familyId,
                                            @RequestParam(required = false) Long ownerId,
                                            HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        LambdaQueryWrapper<FridgeItem> qw = new LambdaQueryWrapper<FridgeItem>()
                .eq(category != null && !category.isBlank(), FridgeItem::getCategory, category);
        if (familyId != null) {
            requireFamilyMember(familyId, uid);
            qw.eq(FridgeItem::getFamilyId, familyId);
        } else if (ownerId != null) {
            requireSharedAccess(ownerId, uid);
            qw.eq(FridgeItem::getUserId, ownerId).isNull(FridgeItem::getFamilyId);
        } else {
            qw.eq(FridgeItem::getUserId, uid).isNull(FridgeItem::getFamilyId);
        }
        List<FridgeItem> list = fridgeItemMapper.selectList(qw);
        if ("status".equals(sort)) {
            list.sort(Comparator.comparingInt((FridgeItem it) -> statusLevel(it, LocalDate.now()))
                    .thenComparing(it -> it.getExpiryDate() == null ? LocalDate.MAX : it.getExpiryDate()));
        }
        return ApiResult.ok(list);
    }

    /** 操作记录：谁添加/修改/删除了食材（范围同 list） */
    @GetMapping("/logs")
    public ApiResult<List<Map<String, Object>>> logs(@RequestParam(required = false) Long familyId,
                                                     @RequestParam(required = false) Long ownerId,
                                                     HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        LambdaQueryWrapper<FridgeLog> qw = new LambdaQueryWrapper<FridgeLog>()
                .orderByDesc(FridgeLog::getId)
                .last("LIMIT 50");
        if (familyId != null) {
            requireFamilyMember(familyId, uid);
            qw.eq(FridgeLog::getFamilyId, familyId);
        } else if (ownerId != null) {
            requireSharedAccess(ownerId, uid);
            qw.eq(FridgeLog::getOwnerUserId, ownerId).isNull(FridgeLog::getFamilyId);
        } else {
            qw.eq(FridgeLog::getOwnerUserId, uid).isNull(FridgeLog::getFamilyId);
        }
        List<FridgeLog> logs = fridgeLogMapper.selectList(qw);
        List<Long> operatorIds = logs.stream().map(FridgeLog::getOperatorId).distinct().toList();
        Map<Long, User> userMap = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (FridgeLog log : logs) {
            User op = userMap.get(log.getOperatorId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", log.getId());
            item.put("action", log.getAction());
            item.put("itemName", log.getItemName());
            item.put("detail", log.getDetail());
            item.put("createdAt", log.getCreatedAt());
            item.put("operatorId", log.getOperatorId());
            item.put("operatorName", op == null ? null : op.getNickname());
            item.put("operatorAvatar", op == null ? null : op.getAvatar());
            result.add(item);
        }
        return ApiResult.ok(result);
    }

    private int statusLevel(FridgeItem item, LocalDate today) {
        LocalDate exp = item.getExpiryDate();
        if (exp == null) return 3; // 无到期日放最后
        long remain = java.time.temporal.ChronoUnit.DAYS.between(today, exp);
        if (remain < 0) return 0;      // 已过期
        if (remain <= item.getReminderDays()) return 1; // 快过期
        return 2;                      // 新鲜
    }

    /**
     * 录入：familyId → 队伍冰箱；ownerId → 录入某人的共享冰箱；缺省 → 我的个人冰箱。
     */
    @PostMapping
    public ApiResult<Long> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FridgeItem item = objectMapperConvert(body);
        item.setId(null);
        if (item.getName() == null || item.getName().isBlank()) {
            throw new BizException("食品名称不能为空");
        }
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        Long ownerId = body.get("ownerId") == null ? null : Long.valueOf(body.get("ownerId").toString());
        if (familyId != null) {
            requireFamilyMember(familyId, uid);
            item.setFamilyId(familyId);
            item.setUserId(uid);
        } else if (ownerId != null) {
            requireSharedAccess(ownerId, uid);
            item.setFamilyId(null);
            item.setUserId(ownerId);
        } else {
            item.setFamilyId(null);
            item.setUserId(uid);
        }
        if (item.getAddedAt() == null) item.setAddedAt(LocalDate.now());
        if (item.getReminderDays() == null) item.setReminderDays(3);
        fridgeItemMapper.insert(item);
        writeLog(familyId, familyId == null ? item.getUserId() : null, uid, "ADD", item.getName(),
                buildDetail(item));
        return ApiResult.ok(item.getId());
    }

    /** 队伍冰箱物品任何成员可改；共享冰箱主人/成员可改；个人物品仅本人可改 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FridgeItem exist = fridgeItemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "记录不存在");
        }
        if (exist.getFamilyId() != null) {
            requireFamilyMember(exist.getFamilyId(), uid);
        } else if (!exist.getUserId().equals(uid)) {
            requireSharedAccess(exist.getUserId(), uid);
        }
        FridgeItem item = objectMapperConvert(body);
        item.setId(id);
        item.setUserId(exist.getUserId());
        item.setFamilyId(exist.getFamilyId());
        fridgeItemMapper.updateById(item);
        writeLog(exist.getFamilyId(), exist.getFamilyId() == null ? exist.getUserId() : null, uid, "UPDATE",
                item.getName() != null ? item.getName() : exist.getName(), buildDetail(item));
        return ApiResult.ok();
    }

    /** 队伍冰箱物品任何成员可删；共享冰箱主人/成员可删；个人物品仅本人可删 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        FridgeItem exist = fridgeItemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "记录不存在");
        }
        if (exist.getFamilyId() != null) {
            requireFamilyMember(exist.getFamilyId(), uid);
        } else if (!exist.getUserId().equals(uid)) {
            requireSharedAccess(exist.getUserId(), uid);
        }
        fridgeItemMapper.deleteById(id);
        writeLog(exist.getFamilyId(), exist.getFamilyId() == null ? exist.getUserId() : null, uid, "DELETE",
                exist.getName(), null);
        return ApiResult.ok();
    }

    private void requireFamilyMember(Long familyId, Long uid) {
        if (uid == null) throw new BizException(401, "请先登录");
        FamilyMember me = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId).eq(FamilyMember::getUserId, uid));
        if (me == null || !Constants.MEMBER_APPROVED.equals(me.getStatus())) {
            throw new BizException(403, "仅队伍成员可管理队伍冰箱");
        }
    }

    /** 共享冰箱访问权限：主人本人或被邀请的共享成员 */
    private void requireSharedAccess(Long ownerId, Long uid) {
        if (uid == null) throw new BizException(401, "请先登录");
        if (ownerId.equals(uid)) return;
        Long cnt = fridgeMemberMapper.selectCount(new LambdaQueryWrapper<FridgeMember>()
                .eq(FridgeMember::getOwnerId, ownerId).eq(FridgeMember::getUserId, uid));
        if (cnt == null || cnt == 0) {
            throw new BizException(403, "无权访问该冰箱");
        }
    }

    private void writeLog(Long familyId, Long ownerUserId, Long operatorId, String action, String itemName, String detail) {
        try {
            FridgeLog log = new FridgeLog();
            log.setFamilyId(familyId);
            log.setOwnerUserId(ownerUserId);
            log.setOperatorId(operatorId);
            log.setAction(action);
            log.setItemName(itemName);
            log.setDetail(detail);
            fridgeLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不阻断主流程，但必须留痕（空 catch 会导致故障无法诊断）
            log.warn("冰箱操作日志写入失败 familyId={}, ownerUserId={}, operatorId={}, action={}, itemName={}: {}",
                    familyId, ownerUserId, operatorId, action, itemName, e.getMessage());
        }
    }

    /** 组装操作描述：数量 + 到期日 */
    private String buildDetail(FridgeItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getQuantity() != null) sb.append(item.getQuantity());
        if (item.getUnit() != null) sb.append(item.getUnit());
        if (item.getExpiryDate() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("到期 ").append(item.getExpiryDate());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private FridgeItem objectMapperConvert(Map<String, Object> body) {
        return objectMapper.convertValue(body, FridgeItem.class);
    }
}
