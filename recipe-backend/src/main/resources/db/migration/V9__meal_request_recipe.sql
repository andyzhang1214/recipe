-- 心愿完成时关联发布的菜谱：心愿单展示「菜谱已发布」可点击查看
ALTER TABLE meal_request ADD COLUMN recipe_id BIGINT NULL COMMENT '完成心愿时发布的菜谱 id' AFTER note;
