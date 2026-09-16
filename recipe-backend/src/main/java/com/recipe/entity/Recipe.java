package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recipe")
public class Recipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private Long familyId;
    private String title;
    private String cover;
    /** 展示图 JSON 数组字符串（最多4张，cover 为第一张） */
    private String covers;
    /** PRIVATE/FAMILY/TEAM/PUBLIC */
    private String visibility;
    /** 1=分享到作者全部队伍（visibility=FAMILY 时生效） */
    private Boolean shareAllFamilies;
    private String difficulty;
    private Integer servings;
    /** DRAFT/PUBLISHED */
    private String status;
    private BigDecimal avgStars;
    private Integer ratingCount;
    /** 总热量(千卡)，NULL=未计算（老数据） */
    private Integer totalCalories;
    /** 每份热量(千卡) */
    private Integer caloriesPerServing;
    /** 1=存在未匹配/未填克数的食材，热量为估算值（前端展示 ≈） */
    private Boolean caloriesApprox;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
