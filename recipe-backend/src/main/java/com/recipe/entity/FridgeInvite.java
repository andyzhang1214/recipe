package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 个人冰箱邀请码（类似队伍邀请） */
@Data
@TableName("fridge_invite")
public class FridgeInvite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String token;
    private Integer validDays;
    private LocalDateTime expiresAt;
    /** ACTIVE/REVOKED */
    private String status;
    private LocalDateTime createdAt;
}
