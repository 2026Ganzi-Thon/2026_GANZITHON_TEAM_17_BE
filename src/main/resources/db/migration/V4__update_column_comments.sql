-- 확정된 감사 도장과 고민 신고자 범위를 컬럼 설명에 반영한다.
ALTER TABLE thanks MODIFY COLUMN stamp VARCHAR(20) NOT NULL
    COMMENT 'GRATEFUL | HELPED | CHEERED';

ALTER TABLE worry_reports MODIFY COLUMN reporter_id CHAR(36) NOT NULL
    COMMENT '청년 및 어르신 신고자';
