-- 优化（V4）：
-- 1. 为高频「按用户+状态查圈层成员」补联合索引（首页 overview / 我的圈子触发全表扫描）
-- 2. 引用完整性由应用层维护，不引入外键（避免历史孤儿数据导致迁移失败、锁竞争）
ALTER TABLE family_member ADD KEY idx_user_status (user_id, status);

-- 说明：meal_request 已有 idx_mr_family_status(family_id,status) 与 idx_mr_user(user_id)；
--       recipe 已有 idx_author、idx_visibility(visibility,status)。
