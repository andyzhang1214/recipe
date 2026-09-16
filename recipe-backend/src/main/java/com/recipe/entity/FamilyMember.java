package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_member")
public class FamilyMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    /** OWNER/ADMIN/MEMBER */
    private String role;
    /** PENDING/APPROVED/REJECTED */
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
}
