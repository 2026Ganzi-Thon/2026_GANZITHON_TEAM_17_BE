-- Preserve existing PARENTING worries by moving them to OTHER before removing the old value.
ALTER TABLE worries MODIFY COLUMN tag ENUM('CAREER', 'LOVE', 'FAMILY', 'RELATIONSHIP', 'PARENTING', 'LIVING', 'MIND', 'HEALTH', 'OTHER') NOT NULL;
UPDATE worries SET tag = 'OTHER' WHERE tag = 'PARENTING';
ALTER TABLE worries MODIFY COLUMN tag ENUM('CAREER', 'LOVE', 'FAMILY', 'RELATIONSHIP', 'LIVING', 'MIND', 'HEALTH', 'OTHER') NOT NULL COMMENT '일·진로 | 연애 | 가족 | 친구·관계 | 생활 | 마음 | 건강 | 기타';
