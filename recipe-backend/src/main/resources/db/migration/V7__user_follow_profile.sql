-- 用户关注关系 + 主页可见性
CREATE TABLE `user_follow` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `follower_id` BIGINT NOT NULL COMMENT '关注者',
  `followee_id` BIGINT NOT NULL COMMENT '被关注者',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_follow` (`follower_id`, `followee_id`),
  KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注';

-- 主页可见性：PUBLIC 公开 / PRIVATE 私密 / FAMILY 仅同圈子可见
ALTER TABLE `user` ADD COLUMN `profile_visibility` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '主页可见性';
