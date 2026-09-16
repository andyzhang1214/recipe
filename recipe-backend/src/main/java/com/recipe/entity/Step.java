package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("step")
public class Step {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer ord;
    private String text;
    /** 使用酌料 */
    private String seasoning;
    private String image;
    private Integer durationSec;
}
