너는 Spring Boot 백엔드 코드를 리뷰하는 시니어 개발자다. 아래 PR을 독립적으로 검토해줘. 이 요청에 적힌 설명이 맞는지부터 의심하고, 코드와 테스트를 직접 대조해서 판단해줘.

# 1. 프로젝트 전제
- 서비스: 청년이 고민을 쓰면 어르신(인생 선배)이 답장하고, 청년은 받은 답장을 신고할 수 있다. 백엔드는 Java 21, Spring Boot 4.1, JPA, MySQL(운영) / H2(테스트). 코드 스타일 지적은 필요 없다.
- 정책 POL-RPT-04~06: 어르신 누적 신고(신고한 청년 기준 중복 제거) 1회 경고, 3회 7일 답장 제한, 5회 영구 제한. 7일 제한은 기간 만료 후 자동 해제하고, 해제 후에도 누적 신고 수는 유지한다. 단계는 누적 수가 정확히 1, 3, 5가 되는 순간에만 바뀌므로 해제 뒤 4번째 신고로 다시 제한하지 않는다.
- 이미 있는 것(앞선 PR에서 머지됨): 답장 신고 `ReplyReportService.report`가 3번째 신고에서 `User.applySanction(RESTRICTED, now + 7일)`을 적용한다. `User.isReplyRestrictedAt(now)`는 RESTRICTED이고 만료 시각 전일 때만 막으므로, 만료 시각이 지난 어르신은 상태가 안 풀렸어도 이미 답장할 수 있다. 어르신 행 잠금 `SeniorLock.lock(id)`(find + refresh PESSIMISTIC_WRITE), 신고·답장 작성·삭제는 모두 어르신 행을 마지막에 잠근다. 신고·작성·삭제 서비스는 MySQL 기본 격리 수준(REPEATABLE READ)의 오래된 조회를 피하려고 `@Transactional(isolation = READ_COMMITTED)`를 쓴다.
- 제한 해제 알림은 명세에 없다. (알림은 제재 "적용" 때만 보낸다. POL-NTF-02)

# 2. 이 PR이 하는 일: 7일 제한 자동 해제
- `RestrictionLiftScheduler`(`@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)`)가 `RestrictionLiftService.liftExpired(LocalDateTime.now())`를 호출한다. `wool.scheduling.enabled`(기본 true)로 `SchedulingConfig`(`@EnableScheduling`)와 스케줄러 Bean을 함께 켜고 끈다. 테스트 설정(`application-test.yml`)은 false다. 이유: 테스트는 H2를 공유해서 스케줄러가 다른 테스트의 데이터를 건드릴 수 있다.
- `RestrictionLiftService`(트랜잭션 없음): `UserRepository.findIdsRestrictedUntilBefore(RESTRICTED, now, PageRequest.of(0, 100))`로 만료된 후보 id를 잠금 없이 읽고(만료 시각이 이른 순), 한 명씩 `RestrictionLifter.liftIfExpired`를 호출한다. 한 명이 예외를 던져도 로그를 남기고 나머지를 계속 처리한다. 100명이 넘으면 다음 실행에서 이어서 처리한다. 풀린 수를 돌려준다.
- `RestrictionLifter.liftIfExpired(seniorId, now)`: `@Transactional(isolation = READ_COMMITTED)`. `SeniorLock.lock`으로 어르신 행을 잠근 뒤 최신 상태를 읽고, RESTRICTED이고 만료 시각이 null이 아니며 `now` 이후가 아닐 때만 `User.liftRestriction()`(NONE, 만료 시각 null)을 호출해 true를 돌려준다. 후보를 고른 뒤 신고가 쌓여 영구 제한(BANNED)이 된 어르신은 풀지 않는다. 별도 Bean이라 어르신마다 트랜잭션이 따로 열린다.
- `UserRepository.findIdsRestrictedUntilBefore` 추가, `User.liftRestriction`은 이미 있었다. DB 스키마 변경 없음. 새 API 없음.
- 테스트: `RestrictionLiftServiceTest`(6개): 만료/정각/남은 기간, 경고·영구·없음 불변, 두 번 실행해도 한 번만 해제되고 누적 신고 수 유지, 100명 배치 제한과 이어서 처리, 만료 시각 재확인, 해제가 잠금에서 기다리는 사이 영구 제한이 되면 풀지 않음(별도 트랜잭션이 어르신 행을 쥐고 BANNED로 바꾸게 한 뒤 해제가 기다리는 것을 확인하고 커밋). `RestrictionLiftSchedulerTest`(2개): 테스트 설정에서 스케줄러가 없음 / 켜면 1분 간격으로 등록. 변형 검사: 잠금 뒤 단계 재확인 제거, 만료 재확인 제거, 배치 크기 제거를 각각 넣어 보니 대응하는 테스트가 실패했다.

# 3. 특히 봐줬으면 하는 곳
1. 신고(`ReplyReportService.report`)와 해제가 같은 어르신에게 동시에 들어올 때(후보 선정 → 잠금 → 재확인) 3번째 신고로 막 제한된 어르신이 풀리거나, 5번째 신고로 영구 제한된 어르신이 풀리는 경우가 없는지. 잠금 순서(어르신 행만 잠그므로 교착 가능성)도 봐줘.
2. 후보 쿼리를 잠금 없이 읽고 한 명씩 트랜잭션을 여는 구조(서버가 여러 대여도 한 어르신은 행 잠금과 재확인으로 한 번만 풀림)가 타당한지. `fixedDelay`와 100명 배치, 예외 삼키기(로그만 남김) 방식의 위험.
3. `@ConditionalOnProperty`로 스케줄링과 Bean을 함께 켜고 끄는 설정이 안전한지(기본 켜짐, 속성 이름 오타 시 어떻게 되는지), 시각은 프로젝트 전반처럼 `LocalDateTime.now()`(JVM 기본 시간대)를 쓰는데 `restrictedUntil`을 저장한 쪽과 같은 기준인지.
4. 테스트가 보호를 실제로 검증하는지.

# 4. 알려진 한계 (지적하지 않아도 된다)
- 제한 해제 알림은 보내지 않는다(명세에 없음).
- 운영 DB(MySQL) 기반 통합 테스트는 없다. H2 기준이다.
- `restrictedUntil`이 null인 RESTRICTED 어르신은 만료 시각이 없어 해제되지 않는다(`isReplyRestrictedAt`도 계속 막는다). 신고 서비스가 항상 만료 시각을 넣으므로 정상 흐름에서는 생기지 않는다.

# 5. 출력 형식
- 항목마다: 심각도(높음/중간/낮음), 파일과 줄, 구체적 시나리오, 고치는 방향
- 문제가 없으면 없다고만 쓰고, 억지로 지적을 만들지 마. 스타일 지적은 하지 마.
- 답변은 파일로 만들지 말고 채팅창에 바로 출력해줘. 내가 그대로 복사해 붙여넣을 수 있게 답변 전체를 마크다운으로 쓰고, 중간에 블록이 끊기거나 벗겨지지 않도록 답변 전체를 백틱 4개(````)로 감싼 코드 블록 하나에 담아줘. 그 안에서는 백틱 3개(```) 코드 블록을 쓰지 말고, 코드 예시는 들여쓰기나 인라인 코드로 써줘.

# 6. 변경 내용 (git diff, 새 파일은 전체가 + 로 표시됨)
```diff
diff --git a/src/main/java/com/ganzithon/wool/user/repository/UserRepository.java b/src/main/java/com/ganzithon/wool/user/repository/UserRepository.java
index 905d403..7236d1f 100644
--- a/src/main/java/com/ganzithon/wool/user/repository/UserRepository.java
+++ b/src/main/java/com/ganzithon/wool/user/repository/UserRepository.java
@@ -1,7 +1,14 @@
 package com.ganzithon.wool.user.repository;
 
 import com.ganzithon.wool.user.entity.User;
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import org.springframework.data.domain.Pageable;
 import org.springframework.data.jpa.repository.JpaRepository;
+import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
+
+import java.time.LocalDateTime;
+import java.util.List;
 
 public interface UserRepository extends JpaRepository<User, String> {
 
@@ -9,4 +16,8 @@ public interface UserRepository extends JpaRepository<User, String> {
 
     // 닉네임을 바꿀 때 자기 자신은 제외하고 중복을 확인한다. (내 닉네임 그대로, 또는 대소문자만 바꾸는 경우를 막지 않기 위해)
     boolean existsByNicknameAndIdNot(String nickname, String id);
+
+    // 7일 제한 기간이 지난 어르신의 id. 잠그지 않고 읽는 후보 목록이라서, 풀기 전에 잠금을 잡고 다시 확인한다.
+    @Query("select u.id from User u where u.sanctionLevel = :level and u.restrictedUntil <= :now order by u.restrictedUntil")
+    List<String> findIdsRestrictedUntilBefore(@Param("level") SanctionLevel level, @Param("now") LocalDateTime now, Pageable pageable);
 }
diff --git a/src/test/resources/application-test.yml b/src/test/resources/application-test.yml
index 936cdda..44a1975 100644
--- a/src/test/resources/application-test.yml
+++ b/src/test/resources/application-test.yml
@@ -9,3 +9,7 @@ spring:
   jpa:
     hibernate:
       ddl-auto: create-drop
+wool:
+  scheduling:
+    # 스케줄러는 테스트에서 직접 호출한다. 켜 두면 공유 H2의 다른 테스트 데이터를 건드릴 수 있다.
+    enabled: false
diff --git a/src/main/java/com/ganzithon/wool/global/config/SchedulingConfig.java b/src/main/java/com/ganzithon/wool/global/config/SchedulingConfig.java
new file mode 100644
index 0000000..f0ba1b7
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/global/config/SchedulingConfig.java
@@ -0,0 +1,12 @@
+package com.ganzithon.wool.global.config;
+
+import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
+import org.springframework.context.annotation.Configuration;
+import org.springframework.scheduling.annotation.EnableScheduling;
+
+// wool.scheduling.enabled=false면 스케줄링 자체를 켜지 않는다. 테스트는 DB를 공유하므로 끈다.
+@Configuration
+@EnableScheduling
+@ConditionalOnProperty(name = "wool.scheduling.enabled", havingValue = "true", matchIfMissing = true)
+public class SchedulingConfig {
+}
diff --git a/src/main/java/com/ganzithon/wool/report/scheduler/RestrictionLiftScheduler.java b/src/main/java/com/ganzithon/wool/report/scheduler/RestrictionLiftScheduler.java
new file mode 100644
index 0000000..dade88a
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/scheduler/RestrictionLiftScheduler.java
@@ -0,0 +1,29 @@
+package com.ganzithon.wool.report.scheduler;
+
+import com.ganzithon.wool.report.service.RestrictionLiftService;
+import lombok.RequiredArgsConstructor;
+import lombok.extern.slf4j.Slf4j;
+import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
+import org.springframework.scheduling.annotation.Scheduled;
+import org.springframework.stereotype.Component;
+
+import java.time.LocalDateTime;
+
+// 1분마다 기간이 지난 7일 답장 제한을 푼다. wool.scheduling.enabled=false면 만들지 않는다. (테스트에서 끈다)
+@Slf4j
+@Component
+@RequiredArgsConstructor
+@ConditionalOnProperty(name = "wool.scheduling.enabled", havingValue = "true", matchIfMissing = true)
+public class RestrictionLiftScheduler {
+
+    private final RestrictionLiftService restrictionLiftService;
+
+    // fixedDelay: 이전 실행이 끝난 뒤 1분. 서버가 여러 대로 늘어도 한 어르신은 행 잠금과 재확인 때문에 한 번만 풀린다.
+    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
+    public void run() {
+        int lifted = restrictionLiftService.liftExpired(LocalDateTime.now());
+        if (lifted > 0) {
+            log.info("7일 답장 제한 해제 {}명", lifted);
+        }
+    }
+}
diff --git a/src/main/java/com/ganzithon/wool/report/service/RestrictionLiftService.java b/src/main/java/com/ganzithon/wool/report/service/RestrictionLiftService.java
new file mode 100644
index 0000000..701efdd
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/service/RestrictionLiftService.java
@@ -0,0 +1,44 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import com.ganzithon.wool.user.repository.UserRepository;
+import lombok.RequiredArgsConstructor;
+import lombok.extern.slf4j.Slf4j;
+import org.springframework.data.domain.PageRequest;
+import org.springframework.stereotype.Service;
+
+import java.time.LocalDateTime;
+import java.util.List;
+
+// 7일 답장 제한의 자동 해제. (POL-RPT-06) 기간이 지난 어르신의 제재 단계를 NONE으로 되돌린다.
+// 읽는 쪽(User.isReplyRestrictedAt)이 만료 시각을 보고 이미 막지 않으므로, 이 작업은 저장된 상태를 맞춰 두는 정리다.
+// 이 서비스에는 트랜잭션이 없다. 한 명씩 RestrictionLifter(별도 Bean)가 자기 트랜잭션으로 푼다.
+@Slf4j
+@Service
+@RequiredArgsConstructor
+public class RestrictionLiftService {
+
+    // 한 번에 처리하는 최대 인원. 넘치면 다음 실행에서 이어서 처리한다.
+    static final int BATCH_SIZE = 100;
+
+    private final UserRepository userRepository;
+    private final RestrictionLifter restrictionLifter;
+
+    // 이번 실행에서 푼 어르신 수를 돌려준다.
+    public int liftExpired(LocalDateTime now) {
+        List<String> candidates = userRepository.findIdsRestrictedUntilBefore(
+                SanctionLevel.RESTRICTED, now, PageRequest.of(0, BATCH_SIZE));
+        int lifted = 0;
+        for (String seniorId : candidates) {
+            try {
+                if (restrictionLifter.liftIfExpired(seniorId, now)) {
+                    lifted++;
+                }
+            } catch (RuntimeException e) {
+                // 한 명이 실패해도 나머지는 계속 처리한다. 풀리지 않은 사람은 다음 실행에서 다시 후보가 된다.
+                log.warn("7일 제한 해제 실패 seniorId={}", seniorId, e);
+            }
+        }
+        return lifted;
+    }
+}
diff --git a/src/main/java/com/ganzithon/wool/report/service/RestrictionLifter.java b/src/main/java/com/ganzithon/wool/report/service/RestrictionLifter.java
new file mode 100644
index 0000000..b26706d
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/service/RestrictionLifter.java
@@ -0,0 +1,35 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.reply.service.SeniorLock;
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import com.ganzithon.wool.user.entity.User;
+import lombok.RequiredArgsConstructor;
+import org.springframework.stereotype.Service;
+import org.springframework.transaction.annotation.Isolation;
+import org.springframework.transaction.annotation.Transactional;
+
+import java.time.LocalDateTime;
+
+// 어르신 한 명의 7일 제한을 푼다. 어르신마다 트랜잭션을 따로 열어서 한 명이 실패해도 나머지는 풀린다.
+// 신고(ReplyReportService)와 같은 어르신 행 잠금을 쓴다. 잠금을 잡은 뒤 상태를 다시 읽어서 확인하므로,
+// 후보를 고른 뒤 신고가 쌓여 영구 제한이 된 어르신은 풀지 않는다.
+@Service
+@RequiredArgsConstructor
+public class RestrictionLifter {
+
+    private final SeniorLock seniorLock;
+
+    // 잠금을 잡은 뒤 최신 상태를 읽어야 하므로 READ COMMITTED로 실행한다. (ReplyReportService와 같은 이유)
+    @Transactional(isolation = Isolation.READ_COMMITTED)
+    public boolean liftIfExpired(String seniorId, LocalDateTime now) {
+        User senior = seniorLock.lock(seniorId);
+        if (senior.getSanctionLevel() != SanctionLevel.RESTRICTED
+                || senior.getRestrictedUntil() == null
+                || senior.getRestrictedUntil().isAfter(now)) {
+            return false;
+        }
+        // 누적 신고 수는 reply_reports에서 계산하므로 상태만 푼다. (POL-RPT-06)
+        senior.liftRestriction();
+        return true;
+    }
+}
diff --git a/src/test/java/com/ganzithon/wool/report/scheduler/RestrictionLiftSchedulerTest.java b/src/test/java/com/ganzithon/wool/report/scheduler/RestrictionLiftSchedulerTest.java
new file mode 100644
index 0000000..9ab2f77
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/scheduler/RestrictionLiftSchedulerTest.java
@@ -0,0 +1,42 @@
+package com.ganzithon.wool.report.scheduler;
+
+import com.ganzithon.wool.IntegrationTestSupport;
+import org.junit.jupiter.api.Nested;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.context.ApplicationContext;
+import org.springframework.scheduling.annotation.Scheduled;
+import org.springframework.test.context.TestPropertySource;
+
+import java.lang.reflect.Method;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+// 스케줄러는 설정으로 켜고 끈다. 테스트에서는 꺼져 있고, 켜면 1분 간격 작업이 등록된다.
+class RestrictionLiftSchedulerTest extends IntegrationTestSupport {
+
+    @Autowired private ApplicationContext context;
+
+    @Test
+    void 테스트_설정에서는_스케줄러가_만들어지지_않는다() {
+        assertThat(context.getBeanNamesForType(RestrictionLiftScheduler.class)).isEmpty();
+    }
+
+    @Nested
+    @TestPropertySource(properties = {
+            "wool.scheduling.enabled=true",
+            "spring.datasource.url=jdbc:h2:mem:test_scheduler;MODE=MySQL"
+    })
+    class Enabled {
+
+        @Autowired private ApplicationContext enabledContext;
+
+        @Test
+        void 켜면_스케줄러가_만들어지고_1분마다_실행되도록_등록된다() throws Exception {
+            assertThat(enabledContext.getBeanNamesForType(RestrictionLiftScheduler.class)).hasSize(1);
+            Method run = RestrictionLiftScheduler.class.getMethod("run");
+            Scheduled scheduled = run.getAnnotation(Scheduled.class);
+            assertThat(scheduled.fixedDelay()).isEqualTo(60_000L);
+        }
+    }
+}
diff --git a/src/test/java/com/ganzithon/wool/report/service/RestrictionLiftServiceTest.java b/src/test/java/com/ganzithon/wool/report/service/RestrictionLiftServiceTest.java
new file mode 100644
index 0000000..d9f4460
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/service/RestrictionLiftServiceTest.java
@@ -0,0 +1,210 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.DatabaseCleaner;
+import com.ganzithon.wool.IntegrationTestSupport;
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import com.ganzithon.wool.report.repository.ReplyReportRepository;
+import com.ganzithon.wool.reply.entity.Reply;
+import com.ganzithon.wool.reply.repository.ReplyRepository;
+import com.ganzithon.wool.reply.service.SeniorLock;
+import com.ganzithon.wool.user.entity.AgeGroup;
+import com.ganzithon.wool.user.entity.Role;
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import com.ganzithon.wool.user.entity.User;
+import com.ganzithon.wool.user.repository.UserRepository;
+import com.ganzithon.wool.worry.entity.Worry;
+import com.ganzithon.wool.worry.entity.WorryTag;
+import com.ganzithon.wool.worry.entity.WorryVisibility;
+import com.ganzithon.wool.worry.repository.WorryRepository;
+import org.junit.jupiter.api.AfterEach;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.jdbc.core.JdbcTemplate;
+import org.springframework.transaction.PlatformTransactionManager;
+import org.springframework.transaction.support.TransactionTemplate;
+
+import java.time.LocalDateTime;
+import java.util.UUID;
+import java.util.concurrent.CountDownLatch;
+import java.util.concurrent.ExecutorService;
+import java.util.concurrent.Executors;
+import java.util.concurrent.Future;
+import java.util.concurrent.TimeUnit;
+import java.util.concurrent.atomic.AtomicInteger;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+// 7일 답장 제한 자동 해제. (POL-RPT-06) 공유 DB를 쓰는 동시성 테스트가 있어서 트랜잭션 롤백 대신 DatabaseCleaner로 비운다.
+class RestrictionLiftServiceTest extends IntegrationTestSupport {
+
+    @Autowired private UserRepository users;
+    @Autowired private WorryRepository worries;
+    @Autowired private ReplyRepository replies;
+    @Autowired private ReplyReportRepository reports;
+    @Autowired private RestrictionLiftService restrictionLiftService;
+    @Autowired private RestrictionLifter restrictionLifter;
+    @Autowired private ReplyReportService replyReportService;
+    @Autowired private SeniorLock seniorLock;
+    @Autowired private PlatformTransactionManager transactionManager;
+    @Autowired private JdbcTemplate jdbc;
+    @Autowired private DatabaseCleaner databaseCleaner;
+
+    @AfterEach
+    void cleanUp() {
+        databaseCleaner.clean();
+    }
+
+    private static String name(String prefix) {
+        return prefix + UUID.randomUUID().toString().substring(0, 6);
+    }
+
+    private User newUser(Role role, String prefix) {
+        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
+    }
+
+    private User seniorWith(SanctionLevel level, LocalDateTime restrictedUntil) {
+        User senior = newUser(Role.SENIOR, "해제선배");
+        senior.applySanction(level, restrictedUntil);
+        return users.saveAndFlush(senior);
+    }
+
+    private User reload(User user) {
+        return users.findById(user.getId()).orElseThrow();
+    }
+
+    private LocalDateTime now() {
+        // DB 시각 정밀도(마이크로초)와 어긋나 경계값 비교가 흔들리지 않게 초 단위로 맞춘다.
+        return LocalDateTime.now().withNano(0);
+    }
+
+    @Test
+    void 기간이_지난_7일_제한은_풀리고_기간이_남은_제한은_그대로다() {
+        LocalDateTime now = now();
+        User expired = seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));
+        User exactly = seniorWith(SanctionLevel.RESTRICTED, now);
+        User remaining = seniorWith(SanctionLevel.RESTRICTED, now.plusMinutes(1));
+
+        int lifted = restrictionLiftService.liftExpired(now);
+
+        assertThat(lifted).isEqualTo(2);
+        assertThat(reload(expired).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+        assertThat(reload(expired).getRestrictedUntil()).isNull();
+        assertThat(reload(exactly).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+        assertThat(reload(remaining).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+        assertThat(reload(remaining).getRestrictedUntil()).isNotNull();
+    }
+
+    // 후보 목록과 별개로, 풀기 직전에 잠금을 잡고 만료 시각을 다시 확인한다.
+    @Test
+    void 기간이_남은_어르신을_직접_풀려고_해도_풀리지_않는다() {
+        LocalDateTime now = now();
+        User remaining = seniorWith(SanctionLevel.RESTRICTED, now.plusMinutes(1));
+
+        assertThat(restrictionLifter.liftIfExpired(remaining.getId(), now)).isFalse();
+        assertThat(reload(remaining).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+        assertThat(restrictionLifter.liftIfExpired(remaining.getId(), now.plusMinutes(1))).isTrue();
+    }
+
+    @Test
+    void 경고_영구_제한_제재없음은_건드리지_않는다() {
+        LocalDateTime now = now();
+        User warned = seniorWith(SanctionLevel.WARNED, null);
+        User banned = seniorWith(SanctionLevel.BANNED, null);
+        User none = seniorWith(SanctionLevel.NONE, null);
+        // 영구 제한인데 예전 만료 시각이 남아 있어도 풀지 않는다.
+        User bannedWithOldDate = seniorWith(SanctionLevel.BANNED, now.minusDays(1));
+
+        assertThat(restrictionLiftService.liftExpired(now)).isZero();
+
+        assertThat(reload(warned).getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+        assertThat(reload(banned).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
+        assertThat(reload(none).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+        assertThat(reload(bannedWithOldDate).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
+    }
+
+    @Test
+    void 두_번_실행해도_한_번만_풀리고_해제_뒤에도_누적_신고_수는_그대로다() {
+        User senior = newUser(Role.SENIOR, "해제선배");
+        for (int i = 0; i < 3; i++) {
+            User youth = newUser(Role.YOUTH, "해제청년");
+            Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+            Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
+            replyReportService.report(youth, reply.getId(), ReplyReportReason.ABUSE);
+        }
+        assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+        LocalDateTime afterExpiry = now().plusDays(8);
+
+        assertThat(restrictionLiftService.liftExpired(afterExpiry)).isEqualTo(1);
+        assertThat(restrictionLiftService.liftExpired(afterExpiry)).isZero();
+
+        assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(3);
+        assertThat(reload(senior).isReplyRestrictedAt(afterExpiry)).isFalse();
+    }
+
+    @Test
+    void 한_번에_백_명까지만_처리하고_나머지는_다음_실행에서_푼다() {
+        LocalDateTime now = now();
+        for (int i = 0; i < RestrictionLiftService.BATCH_SIZE + 5; i++) {
+            seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));
+        }
+
+        assertThat(restrictionLiftService.liftExpired(now)).isEqualTo(RestrictionLiftService.BATCH_SIZE);
+        assertThat(restrictionLiftService.liftExpired(now)).isEqualTo(5);
+        assertThat(restrictionLiftService.liftExpired(now)).isZero();
+    }
+
+    // 후보로 뽑힌 뒤 풀기 전에 다른 트랜잭션이 영구 제한으로 올리면 풀지 않는다. (잠금 뒤 재확인)
+    // 별도 트랜잭션이 어르신 행을 쥔 채 해제를 시작하고, 해제가 잠금에서 기다리는 것을 확인한 뒤 영구 제한을 커밋한다.
+    @Test
+    void 해제가_잠금을_기다리는_사이_영구_제한이_되면_풀지_않는다() throws Exception {
+        LocalDateTime now = now();
+        User senior = seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));
+
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        CountDownLatch locked = new CountDownLatch(1);
+        CountDownLatch release = new CountDownLatch(1);
+        AtomicInteger holderSessionId = new AtomicInteger(-1);
+        try {
+            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
+                User locked1 = seniorLock.lock(senior.getId());
+                holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
+                locked.countDown();
+                try {
+                    release.await(30, TimeUnit.SECONDS);
+                } catch (InterruptedException e) {
+                    Thread.currentThread().interrupt();
+                }
+                // 영구 제한이어도 예전 만료 시각이 남아 있는 경우: 단계 확인이 유일한 방어다.
+                locked1.applySanction(SanctionLevel.BANNED, now.minusMinutes(1));
+                return null;
+            }));
+            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
+
+            Future<Integer> lifting = pool.submit(() -> restrictionLiftService.liftExpired(now));
+            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000)).isTrue();
+            assertThat(lifting.isDone()).isFalse();
+
+            release.countDown();
+            holder.get(10, TimeUnit.SECONDS);
+            assertThat(lifting.get(30, TimeUnit.SECONDS)).isZero();
+            assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
+        } finally {
+            release.countDown();
+            pool.shutdownNow();
+        }
+    }
+
+    private boolean waitUntilBlockedBy(int holderSessionId, long timeoutMillis) throws InterruptedException {
+        long deadline = System.currentTimeMillis() + timeoutMillis;
+        while (System.currentTimeMillis() < deadline) {
+            Integer waiting = jdbc.queryForObject(
+                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, holderSessionId);
+            if (waiting != null && waiting > 0) {
+                return true;
+            }
+            Thread.sleep(20);
+        }
+        return false;
+    }
+}
```
