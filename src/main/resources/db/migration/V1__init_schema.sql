-- wool (털 한 올) 최종 DDL — MySQL 8
-- 기준: 팀 ERD 최종본 (UNIQUE·FK·AUTO_INCREMENT, follow_ups.visibility 기본값, 제재 단계 RESTRICTED, users·follow_ups에 updated_at 없음)
-- 닉네임은 스냅샷 없이 users JOIN으로 현재 닉네임 표시
-- age_group '20S' 등은 Java enum 이름으로 못 쓰므로 AttributeConverter로 매핑 (enum 상수 예: AGE_20S ↔ '20S')

CREATE TABLE `users` (
  `id`               CHAR(36)     NOT NULL COMMENT '사용자 ID (UUID, X-User-Id)',
  `role`             ENUM('YOUTH', 'SENIOR') NOT NULL COMMENT '청년 | 어르신',
  `age_group`        ENUM('20S', '30S', '40S', '50S', '60S', '70S_PLUS') NOT NULL COMMENT '나이대',
  `nickname`         VARCHAR(10)  NOT NULL COMMENT '자동 할당, 2~10자 (형용사 3자 이하 + 양 + 숫자 3자리 이하)',
  `sanction_level`   ENUM('NONE', 'WARNED', 'RESTRICTED', 'BANNED') NOT NULL DEFAULT 'NONE' COMMENT '없음 | 경고 | 7일 제한 | 영구 제한',
  `restricted_until` DATETIME(6)  NULL     COMMENT '7일 제한 해제 시각',
  `created_at`       DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_nickname` (`nickname`)
) COMMENT '사용자';

CREATE TABLE `worries` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `author_id`   CHAR(36)      NOT NULL COMMENT '작성 청년',
  `title`       VARCHAR(40)   NOT NULL,
  `content`     VARCHAR(1000) NOT NULL,
  `tag`         ENUM('CAREER', 'LOVE', 'FAMILY', 'RELATIONSHIP', 'PARENTING', 'LIVING', 'MIND') NOT NULL COMMENT '일·진로 | 연애 | 가족 | 친구·관계 | 육아 | 자취·생활 | 마음',
  `visibility`  ENUM('SENIORS_ONLY', 'PUBLIC') NOT NULL COMMENT '인생 선배와 나만 보기 | 전체 공개',
  `hidden`      BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '신고로 숨김 (영구)',
  `deleted_at`  DATETIME(6)   NULL     COMMENT '소프트 삭제',
  `created_at`  DATETIME(6)   NOT NULL,
  `updated_at`  DATETIME(6)   NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_worries_created_at` (`created_at`),
  CONSTRAINT `fk_worries_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) COMMENT '고민';

CREATE TABLE `replies` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `worry_id`              BIGINT        NOT NULL,
  `senior_id`             CHAR(36)      NOT NULL COMMENT '답장 어르신',
  `type`                  ENUM('TEXT', 'VOICE', 'LETTER') NOT NULL COMMENT '글 | 말 | 손편지',
  `content`               TEXT          NULL     COMMENT 'TEXT/VOICE 본문 (원문 그대로)',
  `image_url`             VARCHAR(500)  NULL     COMMENT 'LETTER 이미지 URL',
  `first_viewed_at`       DATETIME(6)   NULL     COMMENT '청년 최초 열람 시각',
  `follow_up_reminded_at` DATETIME(6)   NULL     COMMENT '후일담 리마인더 발송 시각 (1회 보장)',
  `hidden`                BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '신고로 숨김 (영구)',
  `deleted_at`            DATETIME(6)   NULL     COMMENT '소프트 삭제',
  `created_at`            DATETIME(6)   NOT NULL,
  `updated_at`            DATETIME(6)   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_replies_worry_senior` (`worry_id`, `senior_id`),
  KEY `idx_replies_senior` (`senior_id`),
  CONSTRAINT `fk_replies_worry`  FOREIGN KEY (`worry_id`)  REFERENCES `worries` (`id`),
  CONSTRAINT `fk_replies_senior` FOREIGN KEY (`senior_id`) REFERENCES `users` (`id`)
) COMMENT '답장 (어르신당 고민 1회, 삭제분 포함)';

CREATE TABLE `thanks` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `reply_id`        BIGINT       NOT NULL,
  `stamp`           VARCHAR(20)  NOT NULL COMMENT 'CHEERED_UP | COMFORTED | WILL_TRY (미정, 확정 시 ENUM)',
  `message`         VARCHAR(255) NULL     COMMENT '한마디 (길이 미정)',
  `read_by_senior`  BOOLEAN      NOT NULL DEFAULT FALSE,
  `created_at`      DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_thanks_reply` (`reply_id`),
  CONSTRAINT `fk_thanks_reply` FOREIGN KEY (`reply_id`) REFERENCES `replies` (`id`)
) COMMENT '고마움 (답장당 1회)';

CREATE TABLE `follow_ups` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `reply_id`        BIGINT        NOT NULL,
  `content`         VARCHAR(1000) NOT NULL COMMENT '길이 미정',
  `visibility`      ENUM('SENIORS_ONLY', 'PUBLIC') NOT NULL DEFAULT 'SENIORS_ONLY' COMMENT '선배님께만 | 전체 공개',
  `read_by_senior`  BOOLEAN       NOT NULL DEFAULT FALSE,
  `created_at`      DATETIME(6)   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_ups_reply` (`reply_id`),
  CONSTRAINT `fk_follow_ups_reply` FOREIGN KEY (`reply_id`) REFERENCES `replies` (`id`)
) COMMENT '후일담 (답장당 1회)';

CREATE TABLE `worry_likes` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `worry_id`    BIGINT       NOT NULL,
  `user_id`     CHAR(36)     NOT NULL,
  `created_at`  DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worry_likes` (`worry_id`, `user_id`),
  CONSTRAINT `fk_worry_likes_worry` FOREIGN KEY (`worry_id`) REFERENCES `worries` (`id`),
  CONSTRAINT `fk_worry_likes_user`  FOREIGN KEY (`user_id`)  REFERENCES `users` (`id`)
) COMMENT '고민 좋아요';

CREATE TABLE `reply_likes` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `reply_id`    BIGINT       NOT NULL,
  `user_id`     CHAR(36)     NOT NULL,
  `created_at`  DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reply_likes` (`reply_id`, `user_id`),
  CONSTRAINT `fk_reply_likes_reply` FOREIGN KEY (`reply_id`) REFERENCES `replies` (`id`),
  CONSTRAINT `fk_reply_likes_user`  FOREIGN KEY (`user_id`)  REFERENCES `users` (`id`)
) COMMENT '답장 좋아요 (어르신에게는 공감 수)';

CREATE TABLE `reply_reports` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `reply_id`     BIGINT       NOT NULL,
  `reporter_id`  CHAR(36)     NOT NULL COMMENT '신고한 청년',
  `reason`       ENUM('ABUSE', 'SEXUAL', 'AD', 'IRRELEVANT', 'ETC') NOT NULL COMMENT '욕설 | 성희롱 | 광고·홍보 | 관련 없는 내용 | 기타',
  `created_at`   DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reply_reports` (`reply_id`, `reporter_id`),
  CONSTRAINT `fk_reply_reports_reply`    FOREIGN KEY (`reply_id`)    REFERENCES `replies` (`id`),
  CONSTRAINT `fk_reply_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) COMMENT '답장 신고 (제재 카운트 = 어르신 답장의 서로 다른 신고자 수)';

CREATE TABLE `worry_reports` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `worry_id`     BIGINT       NOT NULL,
  `reporter_id`  CHAR(36)     NOT NULL COMMENT '신고한 어르신',
  `reason`       ENUM('AD', 'ABUSE', 'ETC') NOT NULL COMMENT '광고·홍보 | 욕설 | 기타',
  `created_at`   DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worry_reports` (`worry_id`, `reporter_id`),
  CONSTRAINT `fk_worry_reports_worry`    FOREIGN KEY (`worry_id`)    REFERENCES `worries` (`id`),
  CONSTRAINT `fk_worry_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) COMMENT '고민 신고';

CREATE TABLE `certificates` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `senior_id`    CHAR(36)     NOT NULL,
  `reply_count`  INT          NOT NULL COMMENT '발급 기준 답장 수 (20, 40, 60...)',
  `issued_at`    DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificates_senior_count` (`senior_id`, `reply_count`),
  CONSTRAINT `fk_certificates_senior` FOREIGN KEY (`senior_id`) REFERENCES `users` (`id`)
) COMMENT '감사장 (누적 20통마다, 중복 발급 방지)';

CREATE TABLE `notifications` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`      CHAR(36)      NOT NULL COMMENT '수신자',
  `type`         ENUM('REPLY_ARRIVED', 'FOLLOW_UP_REMINDER', 'THANKS_ARRIVED', 'FOLLOW_UP_ARRIVED', 'SANCTION', 'CERTIFICATE_ISSUED') NOT NULL COMMENT '답장 도착 | 후일담 권유 | 고마움 도착 | 후일담 도착 | 제재 | 감사장 발급',
  `title`        VARCHAR(255)  NOT NULL COMMENT '알림 문구',
  `content`      VARCHAR(1000) NULL     COMMENT '알림 내용',
  `target_kind`  ENUM('REPLY', 'RECEIVED', 'MY_REPLIES', 'SANCTION', 'CERTIFICATE') NOT NULL COMMENT '이동 대상 종류',
  `target_id`    BIGINT        NULL     COMMENT '이동 대상 ID (REPLY/CERTIFICATE만 값 있음)',
  `is_read`      BOOLEAN       NOT NULL DEFAULT FALSE,
  `created_at`   DATETIME(6)   NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) COMMENT '알림';
