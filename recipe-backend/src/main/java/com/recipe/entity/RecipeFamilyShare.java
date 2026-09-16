package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 菜谱-队伍分享关系（指定队伍，多对多） */
@Data
@TableName("recipe_family_share")
public class RecipeFamilyShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Long familyId;
    private LocalDateTime createdAt;
}
