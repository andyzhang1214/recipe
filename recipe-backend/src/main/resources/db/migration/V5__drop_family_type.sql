-- 删除无用的 family.type 字段（「家庭/团队」已统一为「圈子」概念，type 不再使用）
ALTER TABLE family DROP COLUMN type;
