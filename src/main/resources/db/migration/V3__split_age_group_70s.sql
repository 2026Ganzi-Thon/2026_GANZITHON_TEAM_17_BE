-- 나이대 "70대 이상"(70S_PLUS)을 "70대"(70S)와 "80대 이상"(80S_PLUS)으로 나눈다.
-- 화면: 청년 20·30·40대 / 어르신 50·60·70·80대 이상. 역할별 제한은 서버에서 두지 않는다. (본인 인증이 없다)
-- 기존 70S_PLUS 사용자는 80대 이상인지 알 수 없으므로 70S로 옮긴다. 새 값을 넣은 뒤 옛 값을 지운다.
ALTER TABLE users MODIFY COLUMN age_group ENUM('20S', '30S', '40S', '50S', '60S', '70S', '70S_PLUS', '80S_PLUS') NOT NULL COMMENT '나이대';
UPDATE users SET age_group = '70S' WHERE age_group = '70S_PLUS';
ALTER TABLE users MODIFY COLUMN age_group ENUM('20S', '30S', '40S', '50S', '60S', '70S', '80S_PLUS') NOT NULL COMMENT '나이대';
