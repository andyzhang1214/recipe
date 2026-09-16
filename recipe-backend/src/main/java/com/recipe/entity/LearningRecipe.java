package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待学习菜谱：心愿单点「我也不会」后移入的个人学习清单
 */
@Data
@TableName("learning_recipe")
public class LearningRecipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String dishName;
    private Long sourceFamilyId;
    private String sourceFamilyName;
    private LocalDateTime createdAt;
}
