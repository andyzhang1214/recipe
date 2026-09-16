package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family")
public class Family {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String avatar;
    private Long ownerId;
    private String joinCode;
    private Integer maxMembers;
    private Integer memberCount;
    /** 1=需审批 0=直接加入 */
    private Boolean approvalRequired;
    private LocalDateTime createdAt;
}
