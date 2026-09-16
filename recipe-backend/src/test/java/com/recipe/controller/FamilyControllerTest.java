package com.recipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.Family;
import com.recipe.entity.FamilyInvite;
import com.recipe.entity.FamilyMember;
import com.recipe.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 队伍权限（requireMember / requireOwnerOrAdmin）+ 加入流程测试：
 * 成员校验、群主/管理员校验、并发重复加入（DuplicateKeyException 兜底）、幂等加入。
 */
class FamilyControllerTest {

    private FamilyMapper familyMapper;
    private FamilyMemberMapper familyMemberMapper;
    private FamilyInviteMapper familyInviteMapper;
    private FamilyController controller;

    @BeforeEach
    void setUp() {
        familyMapper = mock(FamilyMapper.class);
        familyMemberMapper = mock(FamilyMemberMapper.class);
        familyInviteMapper = mock(FamilyInviteMapper.class);
        controller = new FamilyController(familyMapper, familyMemberMapper,
                familyInviteMapper, mock(UserMapper.class), mock(RecipeMapper.class),
                mock(RecipeFamilyShareMapper.class), mock(FridgeItemMapper.class),
                mock(MealRequestMapper.class), new ObjectMapper());
        when(familyInviteMapper.selectList(any())).thenReturn(List.of());
    }

    private HttpServletRequest req(Long uid) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        if (uid != null) {
            when(request.getAttribute(AuthInterceptor.ATTR_UID)).thenReturn(uid);
        }
        return request;
    }

    private FamilyMember member(Long familyId, Long userId, String role) {
        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        m.setUserId(userId);
        m.setRole(role);
        m.setStatus(Constants.MEMBER_APPROVED);
        return m;
    }

    // ---------- requireMember：邀请列表 ----------

    @Test
    @DisplayName("邀请列表：正式成员可看")
    void invites_memberVisible() {
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.ROLE_MEMBER));
        var result = controller.invites(5L, req(1L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("邀请列表：非成员 403")
    void invites_strangerForbidden() {
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> controller.invites(5L, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅圈层成员可见");
    }

    // ---------- requireOwnerOrAdmin：作废邀请 ----------

    private FamilyInvite invite(Long id, Long familyId) {
        FamilyInvite inv = new FamilyInvite();
        inv.setId(id);
        inv.setFamilyId(familyId);
        inv.setStatus("ACTIVE");
        return inv;
    }

    @Test
    @DisplayName("作废邀请：群主可操作")
    void revokeInvite_ownerOk() {
        when(familyInviteMapper.selectById(3L)).thenReturn(invite(3L, 5L));
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.ROLE_OWNER));
        var result = controller.revokeInvite(3L, req(1L));
        assertThat(result.getCode()).isZero();
    }

    @Test
    @DisplayName("作废邀请：普通成员 403")
    void revokeInvite_plainMemberForbidden() {
        when(familyInviteMapper.selectById(3L)).thenReturn(invite(3L, 5L));
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.ROLE_MEMBER));
        assertThatThrownBy(() -> controller.revokeInvite(3L, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅群主/管理员可操作");
    }

    // ---------- doJoin：加入流程 ----------

    private Family family(Long id, int memberCount, int maxMembers) {
        Family f = new Family();
        f.setId(id);
        f.setName("测试队伍");
        f.setOwnerId(99L);
        f.setMemberCount(memberCount);
        f.setMaxMembers(maxMembers);
        return f;
    }

    @Test
    @DisplayName("加入：已是正式成员幂等返回 alreadyMember=true")
    void join_alreadyMember_idempotent() {
        when(familyMapper.selectOne(any())).thenReturn(family(5L, 2, 99));
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.ROLE_MEMBER));
        var result = controller.join(java.util.Map.of("joinCode", "FAM-ABC123"), req(1L));
        assertThat(result.getData())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("alreadyMember", true);
    }

    @Test
    @DisplayName("加入：新成员正常入队（原子计数通过）")
    void join_newMember_success() {
        when(familyMapper.selectOne(any())).thenReturn(family(5L, 2, 99));
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        when(familyMapper.update(any(), any())).thenReturn(1);
        var result = controller.join(java.util.Map.of("joinCode", "FAM-ABC123"), req(1L));
        assertThat(result.getData())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("alreadyMember", false);
    }

    @Test
    @DisplayName("加入：并发重复插入撞唯一索引，兜底返回 alreadyMember=true 而非 500")
    void join_duplicateKey_fallbackToAlreadyMember() {
        when(familyMapper.selectOne(any())).thenReturn(family(5L, 2, 99));
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        when(familyMemberMapper.insert(any(FamilyMember.class)))
                .thenThrow(new DuplicateKeyException("uk_family_user"));
        var result = controller.join(java.util.Map.of("joinCode", "FAM-ABC123"), req(1L));
        assertThat(result.getData())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("alreadyMember", true);
    }

    @Test
    @DisplayName("加入：队伍已满拒绝")
    void join_familyFull_rejected() {
        when(familyMapper.selectOne(any())).thenReturn(family(5L, 99, 99));
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        when(familyMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> controller.join(java.util.Map.of("joinCode", "FAM-ABC123"), req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("队伍已满");
    }
}
