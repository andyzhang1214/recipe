package com.recipe.common;

/**
 * 全局业务常量：角色/状态/可见性等，收敛散落的魔法字符串。
 */
public final class Constants {

    private Constants() {
    }

    // 角色
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    // 圈层成员状态
    public static final String MEMBER_PENDING = "PENDING";
    public static final String MEMBER_APPROVED = "APPROVED";
    public static final String MEMBER_REJECTED = "REJECTED";

    // 菜谱可见性
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_FAMILY = "FAMILY";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    // 菜谱状态
    public static final String RECIPE_DRAFT = "DRAFT";
    public static final String RECIPE_PUBLISHED = "PUBLISHED";

    // 群员点餐状态
    public static final String MEAL_PENDING = "PENDING";
    public static final String MEAL_DONE = "DONE";
}
