package com.recipe.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.User;
import com.recipe.mapper.FamilyMemberMapper;

import java.util.List;

/** 主页可见性判定：PUBLIC 恒可见；PRIVATE 仅自己；FAMILY 需与查看者同属至少一个已加入的圈子 */
public final class ProfileAccess {

    private ProfileAccess() {
    }

    public static boolean accessible(User target, Long viewerUid, FamilyMemberMapper familyMemberMapper) {
        String vis = target.getProfileVisibility();
        if (vis == null || Constants.VISIBILITY_PUBLIC.equals(vis)) {
            return true;
        }
        if (viewerUid == null || viewerUid.equals(target.getId())) {
            return viewerUid != null && viewerUid.equals(target.getId());
        }
        if (Constants.VISIBILITY_PRIVATE.equals(vis)) {
            return false;
        }
        // FAMILY：同圈判定
        List<Long> myFamilyIds = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, viewerUid)
                        .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED))
                .stream().map(FamilyMember::getFamilyId).distinct().toList();
        if (myFamilyIds.isEmpty()) {
            return false;
        }
        return familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, target.getId())
                .eq(FamilyMember::getStatus, Constants.MEMBER_APPROVED)
                .in(FamilyMember::getFamilyId, myFamilyIds)) > 0;
    }
}
