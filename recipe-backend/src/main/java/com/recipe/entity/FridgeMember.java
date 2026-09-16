package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 个人冰箱共享成员（主人邀请他人共用冰箱） */
@Data
@TableName("fridge_member")
public class FridgeMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long userId;
    private LocalDateTime createdAt;
}
