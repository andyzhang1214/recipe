package com.recipe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对外信息 DTO：仅暴露必要字段，剔除 openid/unionid/phone 等敏感凭证。
 */
@Data
public class UserDTO {
    private Long id;
    private String nickname;
    private String avatar;
    private String profileVisibility;
    private LocalDateTime createdAt;

    public static UserDTO of(com.recipe.entity.User u) {
        UserDTO d = new UserDTO();
        d.setId(u.getId());
        d.setNickname(u.getNickname());
        d.setAvatar(u.getAvatar());
        d.setProfileVisibility(u.getProfileVisibility());
        d.setCreatedAt(u.getCreatedAt());
        return d;
    }

    /** 昵称首字符（列表头像占位字）；昵称为空返回 null，由前端兜底 */
    public static String avatarChar(com.recipe.entity.User u) {
        if (u == null || u.getNickname() == null) return null;
        String nick = u.getNickname().trim();
        if (nick.isEmpty()) return null;
        return new String(Character.toChars(nick.codePointAt(0)));
    }
}
