package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_invite")
public class FamilyInvite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** SHARE_CARD/QR_CODE */
    private String type;
    private String token;
    private Integer validDays;
    private LocalDateTime expiresAt;
    /** ACTIVE/REVOKED */
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
}
