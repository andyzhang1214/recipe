package com.recipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.common.BizException;
import com.recipe.common.Constants;
import com.recipe.config.AuthInterceptor;
import com.recipe.entity.FamilyMember;
import com.recipe.entity.FridgeItem;
import com.recipe.entity.FridgeMember;
import com.recipe.mapper.FamilyMemberMapper;
import com.recipe.mapper.FridgeItemMapper;
import com.recipe.mapper.FridgeLogMapper;
import com.recipe.mapper.FridgeMemberMapper;
import com.recipe.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 冰箱三级权限（requireFamilyMember / requireSharedAccess）分支测试：
 * 队伍冰箱（正式成员才可）、共享冰箱（主人/被邀请成员）、个人冰箱（仅本人）。
 * 覆盖 list / create / update / delete 的越权拒绝与合法放行。
 */
class FridgeControllerTest {

    private FridgeItemMapper fridgeItemMapper;
    private FamilyMemberMapper familyMemberMapper;
    private FridgeMemberMapper fridgeMemberMapper;
    private FridgeController controller;

    @BeforeEach
    void setUp() {
        fridgeItemMapper = mock(FridgeItemMapper.class);
        familyMemberMapper = mock(FamilyMemberMapper.class);
        fridgeMemberMapper = mock(FridgeMemberMapper.class);
        FridgeLogMapper fridgeLogMapper = mock(FridgeLogMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        // 对齐 Spring Boot 自动装配的 ObjectMapper 行为（默认不因未知字段失败）：
        // create 的 body 含 ownerId/familyId 等范围控制字段，非 FridgeItem 实体属性
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        controller = new FridgeController(fridgeItemMapper, familyMemberMapper,
                fridgeMemberMapper, fridgeLogMapper, userMapper, objectMapper);
        when(fridgeItemMapper.selectList(any())).thenReturn(List.of());
    }

    private HttpServletRequest req(Long uid) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        if (uid != null) {
            when(request.getAttribute(AuthInterceptor.ATTR_UID)).thenReturn(uid);
        }
        return request;
    }

    private FamilyMember member(Long familyId, Long userId, String status) {
        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        m.setUserId(userId);
        m.setStatus(status);
        return m;
    }

    // ---------- 队伍冰箱 ----------

    @Test
    @DisplayName("队伍冰箱 list：正式成员可看")
    void familyFridge_memberVisible() {
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.MEMBER_APPROVED));
        var result = controller.list(null, null, 5L, null, req(1L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("队伍冰箱 list：非成员 403")
    void familyFridge_strangerForbidden() {
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> controller.list(null, null, 5L, null, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅队伍成员");
    }

    @Test
    @DisplayName("队伍冰箱 list：PENDING 待审成员 403")
    void familyFridge_pendingMemberForbidden() {
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.MEMBER_PENDING));
        assertThatThrownBy(() -> controller.list(null, null, 5L, null, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅队伍成员");
    }

    // ---------- 共享冰箱 ----------

    @Test
    @DisplayName("共享冰箱 list：主人本人可看")
    void sharedFridge_ownerVisible() {
        // ownerId=1 与 uid=1 相同，无需查询 fridge_member
        var result = controller.list(null, null, null, 1L, req(1L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("共享冰箱 list：被邀请成员可看")
    void sharedFridge_invitedMemberVisible() {
        when(fridgeMemberMapper.selectCount(any())).thenReturn(1L);
        var result = controller.list(null, null, null, 2L, req(1L));
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("共享冰箱 list：陌生人 403")
    void sharedFridge_strangerForbidden() {
        when(fridgeMemberMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> controller.list(null, null, null, 2L, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问该冰箱");
    }

    // ---------- 录入 ----------

    @Test
    @DisplayName("录入共享冰箱：被邀请成员可写")
    void create_sharedFridge_invitedMemberCanWrite() {
        when(fridgeMemberMapper.selectCount(any())).thenReturn(1L);
        // mock insert 不回填自增 id，故只断言业务码（写权限已通过即为本测试目的）
        var result = controller.create(Map.of("name", "牛奶", "ownerId", 2), req(1L));
        assertThat(result.getCode()).isZero();
    }

    @Test
    @DisplayName("录入队伍冰箱：非成员 403")
    void create_familyFridge_strangerForbidden() {
        when(familyMemberMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> controller.create(Map.of("name", "牛奶", "familyId", 5), req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅队伍成员");
    }

    // ---------- 修改 / 删除（个人物品越权是最高风险分支） ----------

    private FridgeItem personalItem(Long id, Long ownerId) {
        FridgeItem item = new FridgeItem();
        item.setId(id);
        item.setUserId(ownerId);
        item.setName("牛奶");
        return item;
    }

    @Test
    @DisplayName("改他人个人冰箱物品：无共享关系 403")
    void update_othersPersonalItem_forbidden() {
        when(fridgeItemMapper.selectById(9L)).thenReturn(personalItem(9L, 2L));
        when(fridgeMemberMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> controller.update(9L, Map.of("name", "酸奶"), req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问该冰箱");
    }

    @Test
    @DisplayName("改他人个人冰箱物品：被邀请成员可改")
    void update_othersPersonalItem_invitedMemberOk() {
        when(fridgeItemMapper.selectById(9L)).thenReturn(personalItem(9L, 2L));
        when(fridgeMemberMapper.selectCount(any())).thenReturn(1L);
        var result = controller.update(9L, Map.of("name", "酸奶"), req(1L));
        assertThat(result.getCode()).isZero();
    }

    @Test
    @DisplayName("删他人个人冰箱物品：无共享关系 403")
    void delete_othersPersonalItem_forbidden() {
        when(fridgeItemMapper.selectById(9L)).thenReturn(personalItem(9L, 2L));
        when(fridgeMemberMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> controller.delete(9L, req(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问该冰箱");
    }

    @Test
    @DisplayName("删队伍冰箱物品：正式成员可删")
    void delete_familyItem_memberOk() {
        FridgeItem item = personalItem(9L, 2L);
        item.setFamilyId(5L);
        when(fridgeItemMapper.selectById(9L)).thenReturn(item);
        when(familyMemberMapper.selectOne(any()))
                .thenReturn(member(5L, 1L, Constants.MEMBER_APPROVED));
        var result = controller.delete(9L, req(1L));
        assertThat(result.getCode()).isZero();
    }

    // ---------- FridgeMember 实体仅用于 count 查询，此处确保构造可用 ----------

    @Test
    @DisplayName("FridgeMember 实体字段可正常赋值（count 查询载体）")
    void fridgeMember_entitySanity() {
        FridgeMember m = new FridgeMember();
        m.setOwnerId(2L);
        m.setUserId(1L);
        assertThat(m.getOwnerId()).isEqualTo(2L);
        assertThat(m.getUserId()).isEqualTo(1L);
    }
}
