너는 Spring Boot 백엔드 코드를 리뷰하는 시니어 개발자다. 아래 PR을 독립적으로 검토해줘. 이 요청에 적힌 설명이 맞는지부터 의심하고, 코드와 테스트를 직접 대조해서 판단해줘.

# 1. 프로젝트 전제
- 서비스: 청년이 고민을 쓰면 어르신(인생 선배)이 답장하고, 청년은 받은 답장을 신고할 수 있다. 백엔드는 Java 21, Spring Boot 4.1(Jackson 3), JPA, MySQL.
- 응답은 항상 `{ status, code, message, data }` 래퍼이고, 예외는 `BusinessException(ErrorCode)`를 `GlobalExceptionHandler`가 같은 형식으로 바꾼다. 인증은 `X-User-Id` 헤더(`@LoginUser(Role.YOUTH)`)다. 코드 스타일 지적은 필요 없다.
- 정책(POL-RPT-01·03~06): 고민 작성자(청년)는 받은 답장을 신고할 수 있다. 신고한 답장은 관리자 검토 없이 즉시, 영구적으로 숨긴다. 같은 대상은 1인 1회만 신고할 수 있다. 어르신 누적 신고 수에 따라 자동 제재: 1회 경고, 3회 7일 답장 제한, 5회 영구 제한. 누적 수는 신고한 청년 기준으로 중복을 뺀다(같은 청년이 같은 어르신을 여러 번 신고해도 1회). 7일 제한이 풀려도 누적 수는 유지한다. 삭제하거나 숨긴 답장의 신고 이력과 제재는 유지한다.
- "유효 답장" = `hidden = false AND deleted_at IS NULL`. 어르신의 누적 유효 답장 수가 털 단계와 감사장 기준이다.
- 이미 있는 것: `ReplyReport` 엔티티(`uk_reply_reports (reply_id, reporter_id)`), `User.applySanction/isReplyRestrictedAt/liftRestriction`, 알림 서비스(`NotificationService.send`), 어르신 행 잠금 `SeniorLock`, 답장 행 잠금 조회 `ReplyRepository.findByIdAndDeletedAtIsNull`(`@Lock PESSIMISTIC_WRITE`). 답장 작성은 고민 행 → 어르신 행, 답장 삭제는 답장 행 → 어르신 행 순서로 잠근다(어르신 행이 항상 마지막).
- 아직 없는 것: 7일 제한을 자동으로 푸는 스케줄러(다음 PR). 읽는 쪽은 `isReplyRestrictedAt(now)`가 만료 시각이 지났으면 상태가 안 풀렸어도 막지 않는다.

# 2. 이 PR이 하는 일: 답장 신고 (REPORT-001)
`POST /api/replies/{replyId}/reports` body `{ "reason": "ABUSE|SEXUAL|AD|IRRELEVANT|ETC" }` (청년 전용, 어르신 403 `USER_403_001`, 헤더 없으면 401). 성공 시 201 `REPORT_201_1`, `data = { replyId, hidden: true }`.

실패: 400(사유 누락·잘못된 값·숫자), 403 `REPLY_403_001`(내 고민에 달린 답장이 아님), 404 `REPLY_404_001`(없거나 삭제된 답장), 409 `REPORT_409_001`(이미 신고함).

처리 순서(`ReplyReportService.report`, 한 트랜잭션):
1. `findByIdAndDeletedAtIsNull`로 답장 행을 잠그고 가져온다(404). 숨긴 답장도 가져온다.
2. 답장이 달린 고민의 작성자가 신고자가 아니면 403.
3. 같은 청년의 이전 신고가 있으면 409.
4. `SeniorLock`으로 답장을 쓴 어르신 행을 잠그고(답장 → 어르신), 시각을 구한다.
5. 누적 신고 수(`countDistinctReportersBySeniorId`: 신고한 청년 중복 제거, 삭제·숨긴 답장의 신고 포함)를 센다 → 신고 저장 → `reply.hide()` → 다시 센다.
6. 수가 늘었으면(`after > before`) `SanctionPolicy.levelReachedAt(after)`: 정확히 1이면 WARNED, 3이면 RESTRICTED(7일, `restrictedUntil = now + 7일`), 5이면 BANNED, 그 밖의 수(2, 4, 6…)는 단계 변경 없음. 이미 같거나 더 높은 단계면 바꾸지 않고, 바꿨으면 `SANCTION` 알림(이동 대상 `SANCTION`, 대상 ID 없음)을 보낸다.

다른 변경:
- `ReplyErrorCode.FORBIDDEN_VIEW`(`REPLY_403_001`) 추가.
- `application.yml`: `spring.jackson.datatype.enum.fail-on-numbers-for-enums: true`. 이유: `{"reason": 1}`처럼 열거형 자리에 숫자를 보내면 Jackson이 순번으로 해석해 `SEXUAL`로 통과(201)했다. 명세는 잘못된 사유를 400으로 정한다. 이 설정은 모든 API의 열거형 요청 필드에 적용된다(숫자 거부). `JacksonConfigTest`에 이름은 받고 숫자는 거부하는 테스트를 추가했다.
- 신규: `report/controller/ReportController`, `report/service/{ReplyReportService, SanctionPolicy}`, `report/request/ReportReplyRequest`, `report/response/{ReportReplyResponse, ReportResponseCode}`, `report/exception/ReportErrorCode`, 테스트 `ReplyReportControllerTest`(18개), `ReplyReportConcurrencyTest`(3개), `SanctionPolicyTest`(7개).
- 격리 수준: 운영 MySQL 기본은 REPEATABLE READ라서, 잠금을 기다린 뒤에도 트랜잭션 시작 시점의 오래된 누적 수를 읽으면 동시 신고·작성·삭제에서 제재 단계나 감사장이 빠질 수 있다(1명→동시에 2명 더 신고하면 3번째 기준을 건너뜀). 그래서 누적 수를 바꾸는 세 서비스 `ReplyReportService.report`, `ReplyCreateService.create`, `ReplyDeleteService.delete`에 `@Transactional(isolation = READ_COMMITTED)`를 선언했다(잠금 뒤 읽기가 최신 커밋을 본다). 테스트 `RepeatableReadConcurrencyTest`(4개, 별도 H2 DB에 연결 기본을 REPEATABLE READ로 둠): 신고 1→3(7일 제한), 3→5(영구 제한), 답장 19→20 동시 작성(감사장 1장), 삭제와 작성 동시 겹침. 선언을 하나씩 지우면 대응 테스트가 실패하는 것을 확인했다. 신고 테스트는 별도 트랜잭션이 어르신 행을 쥔 채 두 신고를 시작하고, 둘 다 잠금에서 기다리는 것을 확인한 뒤 풀어 주므로 순서가 매번 같다. 세 서비스는 컨트롤러에서만 호출되고 상위 트랜잭션이 없어 새 트랜잭션이 열린다(다른 트랜잭션 안에서 호출하면 격리 수준 선언은 무시된다).
- 수정: `ReplyReportRepository`(누적 신고 수 쿼리 추가), `ReplyCreateService`, `ReplyDeleteService`, `ReplyErrorCode`, `application.yml`, `JacksonConfigTest`.
- DB 스키마 변경 없음.

# 3. 특히 봐줬으면 하는 곳
1. 제재 단계 규칙: "정확히 1, 3, 5가 되는 순간에만 바뀐다"는 해석이 POL-RPT-04~06(1회 경고 / 3회 7일 제한 / 5회 영구 제한, 해제 후에도 누적 수 유지)과 맞는지. 7일 제한이 풀린 뒤(상태 NONE) 4번째 신고나 같은 청년의 추가 신고가 다시 제한하지 않는 것이 의도에 맞는지. 이미 영구 제한(BANNED)인 어르신이 낮은 단계 기준에 도달해도 내려가지 않는지.
2. 누적 신고 수 쿼리(`count(distinct rr.reporter.id) ... where rr.reply.senior.id = :seniorId`)가 중복을 올바르게 빼고, 삭제·숨긴 답장의 신고를 세는지.
3. 잠금과 동시성: 답장 → 어르신 순서가 답장 삭제(답장 → 어르신), 답장 작성(고민 → 어르신)과 합쳐 교착이 생기지 않는지. 같은 어르신을 서로 다른 청년이 동시에 신고하면 누적 수와 제재 알림이 한 번씩만 맞아떨어지는지, 같은 청년이 같은 답장을 동시에 신고하면 한 번만 접수되는지(`uk_reply_reports` 제약과 답장 행 잠금).
4. 검사 순서(404 → 403 → 409)와 이미 숨겨진 답장 처리, 신고자가 고민 작성자가 아닐 때 존재 여부가 드러나는 점이 허용 가능한지.
5. `SeniorLock` 이후의 `senior`(영속성 컨텍스트에서 refresh한 엔티티)에 `applySanction`을 바로 쓰는 것이 안전한지, 트랜잭션 안에서 알림 저장이 같이 커밋·롤백되는지.
6. 전역 Jackson 설정(`fail-on-numbers-for-enums`) 변경이 다른 API의 정상 요청을 막을 여지가 있는지(열거형 요청 필드는 문자열 이름으로만 보낸다).
7. 테스트가 보호를 실제로 검증하는지: 저자가 답장 숨김 제거, 7일 제한 기준 변경, 중복 신고자 제거 안 함, 삭제한 답장의 신고 제외, 내 고민 확인 제거, 중복 신고 확인 제거, 어르신 행 잠금 제거, 누적 수 증가 확인 제거, 단계 내림 방지 제거를 각각 넣어 보니 해당 테스트가 실패했다.

# 4. 알려진 한계 (지적하지 않아도 된다)
- 7일 제한을 자동으로 푸는 스케줄러는 다음 PR이다. 그동안에도 읽는 쪽이 만료 시각을 보고 막지 않는다.
- 제재 알림 문구는 명세에 없어서 저자가 정했다.
- 신고된 청년(작성자)에 대한 제재는 없다. 어르신에게만 적용한다.
- 테스트 DB는 H2라서 MySQL의 잠금 동작까지 보장하지는 않는다. H2의 REPEATABLE READ는 MySQL보다 스냅샷을 일찍 만들어 더 엄격하다.
- 고민 수정(`WorryUpdateTransaction`)은 이 PR 범위 밖이며 격리 수준을 바꾸지 않았다.

# 5. 출력 형식
- 항목마다: 심각도(높음/중간/낮음), 파일과 줄, 구체적 시나리오, 고치는 방향
- 문제가 없으면 없다고만 쓰고, 억지로 지적을 만들지 마. 스타일 지적은 하지 마.
- 답변은 파일로 만들지 말고 채팅창에 바로 출력해줘. 내가 그대로 복사해 붙여넣을 수 있게 답변 전체를 마크다운으로 쓰고, 중간에 블록이 끊기거나 벗겨지지 않도록 답변 전체를 백틱 4개(````)로 감싼 코드 블록 하나에 담아줘. 그 안에서는 백틱 3개(```) 코드 블록을 쓰지 말고, 코드 예시는 들여쓰기나 인라인 코드로 써줘.

# 6. 변경 내용 (git diff, 새 파일은 전체가 + 로 표시됨)
```diff
diff --git a/src/main/java/com/ganzithon/wool/reply/exception/ReplyErrorCode.java b/src/main/java/com/ganzithon/wool/reply/exception/ReplyErrorCode.java
index 70756f7..8013786 100644
--- a/src/main/java/com/ganzithon/wool/reply/exception/ReplyErrorCode.java
+++ b/src/main/java/com/ganzithon/wool/reply/exception/ReplyErrorCode.java
@@ -9,6 +9,7 @@ import org.springframework.http.HttpStatus;
 @RequiredArgsConstructor
 public enum ReplyErrorCode implements ErrorCode {
 
+    FORBIDDEN_VIEW(HttpStatus.FORBIDDEN, "REPLY_403_001", "볼 수 없는 답장입니다."),
     RESTRICTED(HttpStatus.FORBIDDEN, "REPLY_403_002", "지금은 답장할 수 없어요."),
     FORBIDDEN_DELETE(HttpStatus.FORBIDDEN, "REPLY_403_003", "삭제할 권한이 없습니다."),
     NOT_FOUND(HttpStatus.NOT_FOUND, "REPLY_404_001", "답장을 찾을 수 없습니다."),
diff --git a/src/main/java/com/ganzithon/wool/reply/service/ReplyCreateService.java b/src/main/java/com/ganzithon/wool/reply/service/ReplyCreateService.java
index 7ec497f..d1a0896 100644
--- a/src/main/java/com/ganzithon/wool/reply/service/ReplyCreateService.java
+++ b/src/main/java/com/ganzithon/wool/reply/service/ReplyCreateService.java
@@ -23,6 +23,7 @@ import com.ganzithon.wool.worry.exception.WorryErrorCode;
 import com.ganzithon.wool.worry.repository.WorryRepository;
 import lombok.RequiredArgsConstructor;
 import org.springframework.stereotype.Service;
+import org.springframework.transaction.annotation.Isolation;
 import org.springframework.transaction.annotation.Transactional;
 
 import java.time.LocalDateTime;
@@ -42,7 +43,10 @@ public class ReplyCreateService {
 
     // requester는 @LoginUser가 요청 시작 때 읽은 사용자다. 제재 상태가 그 사이 바뀌었을 수 있어서 id만 쓰고,
     // 잠금을 잡은 뒤 다시 읽은 최신 상태(senior)로 판단한다.
-    @Transactional
+    // 어르신의 누적 답장 수와 감사장 기준을 잠금을 잡은 뒤에 읽으므로 READ COMMITTED로 실행한다.
+    // MySQL 기본(REPEATABLE READ)에서는 트랜잭션의 첫 일반 조회가 그 시점의 스냅샷을 만들어서, 잠금을 기다린 뒤에도
+    // 다른 트랜잭션이 그 사이 커밋한 답장·신고를 못 보는 오래된 수를 읽을 수 있다. READ COMMITTED는 조회마다 최신 커밋을 본다.
+    @Transactional(isolation = Isolation.READ_COMMITTED)
     public CreateReplyResponse create(User requester, Long worryId, CreateReplyRequest request) {
         // 손편지는 업로드 API가 실제로 저장한 사진만 받는다. 형식만 맞는 가짜 주소는 잠금을 잡기 전에 돌려보낸다.
         if (request.type() == ReplyType.LETTER && !fileStorage.exists(request.imageUrl())) {
diff --git a/src/main/java/com/ganzithon/wool/reply/service/ReplyDeleteService.java b/src/main/java/com/ganzithon/wool/reply/service/ReplyDeleteService.java
index 4b37d7c..ef90aae 100644
--- a/src/main/java/com/ganzithon/wool/reply/service/ReplyDeleteService.java
+++ b/src/main/java/com/ganzithon/wool/reply/service/ReplyDeleteService.java
@@ -8,6 +8,7 @@ import com.ganzithon.wool.reply.response.DeleteReplyResponse;
 import com.ganzithon.wool.user.entity.User;
 import lombok.RequiredArgsConstructor;
 import org.springframework.stereotype.Service;
+import org.springframework.transaction.annotation.Isolation;
 import org.springframework.transaction.annotation.Transactional;
 
 // 답장 삭제(REPLY-007). 소프트 삭제이고 복구하지 않는다.
@@ -21,7 +22,10 @@ public class ReplyDeleteService {
     private final ReplyRepository replyRepository;
     private final SeniorLock seniorLock;
 
-    @Transactional
+    // 어르신의 누적 답장 수와 감사장 기준을 잠금을 잡은 뒤에 읽으므로 READ COMMITTED로 실행한다.
+    // MySQL 기본(REPEATABLE READ)에서는 트랜잭션의 첫 일반 조회가 그 시점의 스냅샷을 만들어서, 잠금을 기다린 뒤에도
+    // 다른 트랜잭션이 그 사이 커밋한 답장·신고를 못 보는 오래된 수를 읽을 수 있다. READ COMMITTED는 조회마다 최신 커밋을 본다.
+    @Transactional(isolation = Isolation.READ_COMMITTED)
     public DeleteReplyResponse delete(User senior, Long replyId) {
         // 답장 행을 잠근 뒤 판단한다. 같은 답장에 대한 수정, 고마움, 신고가 동시에 들어와도 한쪽씩 처리된다.
         Reply reply = replyRepository.findByIdAndDeletedAtIsNull(replyId)
diff --git a/src/main/java/com/ganzithon/wool/report/repository/ReplyReportRepository.java b/src/main/java/com/ganzithon/wool/report/repository/ReplyReportRepository.java
index 21164d0..a24f715 100644
--- a/src/main/java/com/ganzithon/wool/report/repository/ReplyReportRepository.java
+++ b/src/main/java/com/ganzithon/wool/report/repository/ReplyReportRepository.java
@@ -2,8 +2,14 @@ package com.ganzithon.wool.report.repository;
 
 import com.ganzithon.wool.report.entity.ReplyReport;
 import org.springframework.data.jpa.repository.JpaRepository;
+import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
 
 public interface ReplyReportRepository extends JpaRepository<ReplyReport, Long> {
 
     boolean existsByReplyIdAndReporterId(Long replyId, String reporterId);
+
+    // 어르신의 누적 신고 수. 신고한 청년을 기준으로 중복을 뺀다. 삭제하거나 숨긴 답장의 신고도 센다. (POL-RPT-05, POL-RPL-09)
+    @Query("select count(distinct rr.reporter.id) from ReplyReport rr where rr.reply.senior.id = :seniorId")
+    long countDistinctReportersBySeniorId(@Param("seniorId") String seniorId);
 }
diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
index f0da21f..1b24147 100644
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -5,6 +5,11 @@ spring:
     import: optional:file:.env[.properties]
   profiles:
     active: local
+  jackson:
+    datatype:
+      enum:
+        # 열거형 자리에 숫자(순번)를 보내면 해당 순번의 값으로 통과하지 않고 400으로 거부한다. (예: {"reason": 1})
+        fail-on-numbers-for-enums: true
   servlet:
     multipart:
       # 손편지 사진은 10MB까지. (서비스에서도 같은 값을 한 번 더 확인한다)
diff --git a/src/test/java/com/ganzithon/wool/global/config/JacksonConfigTest.java b/src/test/java/com/ganzithon/wool/global/config/JacksonConfigTest.java
index dcd3208..48f1eaa 100644
--- a/src/test/java/com/ganzithon/wool/global/config/JacksonConfigTest.java
+++ b/src/test/java/com/ganzithon/wool/global/config/JacksonConfigTest.java
@@ -8,12 +8,18 @@ import tools.jackson.databind.json.JsonMapper;
 import java.time.LocalDateTime;
 
 import static org.assertj.core.api.Assertions.assertThat;
+import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
 class JacksonConfigTest extends IntegrationTestSupport {
 
     record Sample(LocalDateTime createdAt) {
     }
 
+    enum Color { RED, BLUE }
+
+    record Paint(Color color) {
+    }
+
     @Autowired
     private JsonMapper jsonMapper;
 
@@ -38,4 +44,12 @@ class JacksonConfigTest extends IntegrationTestSupport {
 
         assertThat(json).isEqualTo("{\"createdAt\":\"2026-12-31T23:59:59+09:00\"}");
     }
+
+    @Test
+    void 열거형에_이름은_받고_숫자는_받지_않는다() {
+        assertThat(jsonMapper.readValue("{\"color\":\"BLUE\"}", Paint.class).color()).isEqualTo(Color.BLUE);
+
+        assertThatThrownBy(() -> jsonMapper.readValue("{\"color\":1}", Paint.class))
+                .isInstanceOf(RuntimeException.class);
+    }
 }
diff --git a/src/main/java/com/ganzithon/wool/report/controller/ReportController.java b/src/main/java/com/ganzithon/wool/report/controller/ReportController.java
new file mode 100644
index 0000000..ba1be7e
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/controller/ReportController.java
@@ -0,0 +1,35 @@
+package com.ganzithon.wool.report.controller;
+
+import com.ganzithon.wool.global.auth.LoginUser;
+import com.ganzithon.wool.global.response.ApiResponse;
+import com.ganzithon.wool.report.request.ReportReplyRequest;
+import com.ganzithon.wool.report.response.ReportReplyResponse;
+import com.ganzithon.wool.report.response.ReportResponseCode;
+import com.ganzithon.wool.report.service.ReplyReportService;
+import com.ganzithon.wool.user.entity.Role;
+import com.ganzithon.wool.user.entity.User;
+import jakarta.validation.Valid;
+import lombok.RequiredArgsConstructor;
+import org.springframework.http.ResponseEntity;
+import org.springframework.web.bind.annotation.PathVariable;
+import org.springframework.web.bind.annotation.PostMapping;
+import org.springframework.web.bind.annotation.RequestBody;
+import org.springframework.web.bind.annotation.RequestMapping;
+import org.springframework.web.bind.annotation.RestController;
+
+@RestController
+@RequestMapping("/api")
+@RequiredArgsConstructor
+public class ReportController {
+
+    private final ReplyReportService replyReportService;
+
+    // REPORT-001 답장 신고
+    @PostMapping("/replies/{replyId}/reports")
+    public ResponseEntity<ApiResponse<ReportReplyResponse>> reportReply(
+            @LoginUser(Role.YOUTH) User youth, @PathVariable Long replyId,
+            @Valid @RequestBody ReportReplyRequest request) {
+        return ApiResponse.of(ReportResponseCode.REPLY_REPORTED,
+                replyReportService.report(youth, replyId, request.reason())).toResponseEntity();
+    }
+}
diff --git a/src/main/java/com/ganzithon/wool/report/exception/ReportErrorCode.java b/src/main/java/com/ganzithon/wool/report/exception/ReportErrorCode.java
new file mode 100644
index 0000000..c325ba3
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/exception/ReportErrorCode.java
@@ -0,0 +1,17 @@
+package com.ganzithon.wool.report.exception;
+
+import com.ganzithon.wool.global.exception.ErrorCode;
+import lombok.Getter;
+import lombok.RequiredArgsConstructor;
+import org.springframework.http.HttpStatus;
+
+@Getter
+@RequiredArgsConstructor
+public enum ReportErrorCode implements ErrorCode {
+
+    ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT_409_001", "이미 신고한 답장입니다.");
+
+    private final HttpStatus httpStatus;
+    private final String code;
+    private final String message;
+}
diff --git a/src/main/java/com/ganzithon/wool/report/request/ReportReplyRequest.java b/src/main/java/com/ganzithon/wool/report/request/ReportReplyRequest.java
new file mode 100644
index 0000000..fd82a89
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/request/ReportReplyRequest.java
@@ -0,0 +1,8 @@
+package com.ganzithon.wool.report.request;
+
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import jakarta.validation.constraints.NotNull;
+
+// 사유는 ABUSE(욕설) / SEXUAL(성희롱) / AD(광고·홍보) / IRRELEVANT(관련 없는 내용) / ETC(기타) 중 하나다.
+public record ReportReplyRequest(@NotNull ReplyReportReason reason) {
+}
diff --git a/src/main/java/com/ganzithon/wool/report/response/ReportReplyResponse.java b/src/main/java/com/ganzithon/wool/report/response/ReportReplyResponse.java
new file mode 100644
index 0000000..2056499
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/response/ReportReplyResponse.java
@@ -0,0 +1,5 @@
+package com.ganzithon.wool.report.response;
+
+// hidden은 항상 true다. 신고한 답장은 즉시 숨긴다. 프론트는 내 고민 상세로 이동한다.
+public record ReportReplyResponse(Long replyId, boolean hidden) {
+}
diff --git a/src/main/java/com/ganzithon/wool/report/response/ReportResponseCode.java b/src/main/java/com/ganzithon/wool/report/response/ReportResponseCode.java
new file mode 100644
index 0000000..d4529c7
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/response/ReportResponseCode.java
@@ -0,0 +1,17 @@
+package com.ganzithon.wool.report.response;
+
+import com.ganzithon.wool.global.response.ResponseCode;
+import lombok.Getter;
+import lombok.RequiredArgsConstructor;
+import org.springframework.http.HttpStatus;
+
+@Getter
+@RequiredArgsConstructor
+public enum ReportResponseCode implements ResponseCode {
+
+    REPLY_REPORTED(HttpStatus.CREATED, "REPORT_201_1", "신고가 접수되었고 답장을 숨겼습니다.");
+
+    private final HttpStatus httpStatus;
+    private final String code;
+    private final String message;
+}
diff --git a/src/main/java/com/ganzithon/wool/report/service/ReplyReportService.java b/src/main/java/com/ganzithon/wool/report/service/ReplyReportService.java
new file mode 100644
index 0000000..7c71e50
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/service/ReplyReportService.java
@@ -0,0 +1,99 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.global.exception.BusinessException;
+import com.ganzithon.wool.notification.entity.NotificationType;
+import com.ganzithon.wool.notification.entity.TargetKind;
+import com.ganzithon.wool.notification.service.NotificationService;
+import com.ganzithon.wool.report.entity.ReplyReport;
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import com.ganzithon.wool.report.exception.ReportErrorCode;
+import com.ganzithon.wool.report.repository.ReplyReportRepository;
+import com.ganzithon.wool.report.response.ReportReplyResponse;
+import com.ganzithon.wool.reply.entity.Reply;
+import com.ganzithon.wool.reply.exception.ReplyErrorCode;
+import com.ganzithon.wool.reply.repository.ReplyRepository;
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
+// 답장 신고(REPORT-001). 신고를 저장하고, 답장을 즉시 숨기고, 어르신의 누적 신고 수에 따라 제재 단계를 올린다.
+// 숨김은 영구이고 관리자 검토는 없다. 신고 저장, 숨김, 제재, 제재 알림이 한 트랜잭션이다.
+//
+// 잠금 순서: 답장 행 → 어르신 행. 답장 삭제와 같고, 답장 작성(고민 행 → 어르신 행)과도 어긋나지 않는다.
+// 숨김이 누적 답장 수를 줄이고 신고 수가 제재 단계를 바꾸므로, 둘 다 같은 어르신 기준으로 한 번에 하나씩 처리한다.
+@Service
+@RequiredArgsConstructor
+public class ReplyReportService {
+
+    private final ReplyRepository replyRepository;
+    private final ReplyReportRepository replyReportRepository;
+    private final SeniorLock seniorLock;
+    private final NotificationService notificationService;
+
+    // 어르신의 누적 답장 수와 감사장 기준을 잠금을 잡은 뒤에 읽으므로 READ COMMITTED로 실행한다.
+    // MySQL 기본(REPEATABLE READ)에서는 트랜잭션의 첫 일반 조회가 그 시점의 스냅샷을 만들어서, 잠금을 기다린 뒤에도
+    // 다른 트랜잭션이 그 사이 커밋한 답장·신고를 못 보는 오래된 수를 읽을 수 있다. READ COMMITTED는 조회마다 최신 커밋을 본다.
+    @Transactional(isolation = Isolation.READ_COMMITTED)
+    public ReportReplyResponse report(User reporter, Long replyId, ReplyReportReason reason) {
+        // 삭제된 답장은 없는 것으로 본다. 숨긴 답장은 가져온다. (이미 신고했다면 아래에서 409가 된다)
+        Reply reply = replyRepository.findByIdAndDeletedAtIsNull(replyId)
+                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
+        // 자기 고민에 달린 답장만 신고할 수 있다.
+        if (!reply.getWorry().getAuthor().getId().equals(reporter.getId())) {
+            throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
+        }
+        if (replyReportRepository.existsByReplyIdAndReporterId(replyId, reporter.getId())) {
+            throw new BusinessException(ReportErrorCode.ALREADY_REPORTED);
+        }
+
+        User senior = seniorLock.lock(reply.getSenior().getId());
+        LocalDateTime now = LocalDateTime.now();
+        long before = replyReportRepository.countDistinctReportersBySeniorId(senior.getId());
+
+        replyReportRepository.saveAndFlush(ReplyReport.of(reply, reporter, reason));
+        reply.hide();
+
+        long after = replyReportRepository.countDistinctReportersBySeniorId(senior.getId());
+        if (after > before) {
+            applySanction(senior, after, now);
+        }
+        return new ReportReplyResponse(reply.getId(), true);
+    }
+
+    // 누적 신고 수가 1, 3, 5가 된 순간에만 단계를 올리고 알림을 보낸다. 이미 더 높은 단계면 내리지 않는다.
+    private void applySanction(User senior, long distinctReporters, LocalDateTime now) {
+        SanctionLevel level = SanctionPolicy.levelReachedAt(distinctReporters);
+        if (level == null || level.ordinal() <= senior.getSanctionLevel().ordinal()) {
+            return;
+        }
+        LocalDateTime restrictedUntil = level == SanctionLevel.RESTRICTED
+                ? now.plusDays(SanctionPolicy.RESTRICTION_DAYS) : null;
+        senior.applySanction(level, restrictedUntil);
+        notificationService.send(senior.getId(), NotificationType.SANCTION, title(level), content(level),
+                TargetKind.SANCTION, null);
+    }
+
+    private static String title(SanctionLevel level) {
+        return switch (level) {
+            case WARNED -> "답장에 대한 경고가 있어요";
+            case RESTRICTED -> "7일 동안 답장을 쓸 수 없어요";
+            case BANNED -> "더 이상 답장을 쓸 수 없어요";
+            case NONE -> throw new IllegalArgumentException("제재가 없는 단계는 알림을 보내지 않습니다.");
+        };
+    }
+
+    private static String content(SanctionLevel level) {
+        return switch (level) {
+            case WARNED -> "청년이 답장을 신고했어요. 서로를 존중하는 표현으로 답장해 주세요.";
+            case RESTRICTED -> "신고가 쌓여 7일 동안 답장을 쓸 수 없어요. 기간이 지나면 다시 쓸 수 있어요.";
+            case BANNED -> "신고가 계속 쌓여 답장 기능을 더 이상 쓸 수 없어요.";
+            case NONE -> throw new IllegalArgumentException("제재가 없는 단계는 알림을 보내지 않습니다.");
+        };
+    }
+}
diff --git a/src/main/java/com/ganzithon/wool/report/service/SanctionPolicy.java b/src/main/java/com/ganzithon/wool/report/service/SanctionPolicy.java
new file mode 100644
index 0000000..ec1c208
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/report/service/SanctionPolicy.java
@@ -0,0 +1,32 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.user.entity.SanctionLevel;
+
+// 어르신 자동 단계 제재. (POL-RPT-04~06)
+// 누적 신고 수는 신고한 청년 기준으로 중복을 뺀 수다. 같은 청년이 같은 어르신을 여러 번 신고해도 1회로 센다.
+// 누적 수가 정확히 1, 3, 5가 되는 순간에만 단계가 바뀐다. (1회 경고 / 3회 7일 답장 제한 / 5회 영구 제한)
+// 7일 제한이 풀린 뒤에도 누적 수는 유지하고, 4회째에는 다시 제한하지 않는다.
+public final class SanctionPolicy {
+
+    public static final int WARNING_AT = 1;
+    public static final int RESTRICTION_AT = 3;
+    public static final int BAN_AT = 5;
+    public static final int RESTRICTION_DAYS = 7;
+
+    private SanctionPolicy() {
+    }
+
+    // 누적 신고 수가 count가 되는 순간 적용할 단계. 단계가 바뀌지 않는 수면 null이다.
+    public static SanctionLevel levelReachedAt(long count) {
+        if (count == WARNING_AT) {
+            return SanctionLevel.WARNED;
+        }
+        if (count == RESTRICTION_AT) {
+            return SanctionLevel.RESTRICTED;
+        }
+        if (count == BAN_AT) {
+            return SanctionLevel.BANNED;
+        }
+        return null;
+    }
+}
diff --git a/src/test/java/com/ganzithon/wool/report/controller/ReplyReportControllerTest.java b/src/test/java/com/ganzithon/wool/report/controller/ReplyReportControllerTest.java
new file mode 100644
index 0000000..d19ba7e
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/controller/ReplyReportControllerTest.java
@@ -0,0 +1,356 @@
+package com.ganzithon.wool.report.controller;
+
+import com.ganzithon.wool.IntegrationTestSupport;
+import com.ganzithon.wool.notification.entity.Notification;
+import com.ganzithon.wool.notification.entity.NotificationType;
+import com.ganzithon.wool.notification.entity.TargetKind;
+import com.ganzithon.wool.notification.repository.NotificationRepository;
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import com.ganzithon.wool.report.repository.ReplyReportRepository;
+import com.ganzithon.wool.reply.entity.Reply;
+import com.ganzithon.wool.reply.repository.ReplyRepository;
+import com.ganzithon.wool.user.entity.AgeGroup;
+import com.ganzithon.wool.user.entity.Role;
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import com.ganzithon.wool.user.entity.User;
+import com.ganzithon.wool.user.repository.UserRepository;
+import com.ganzithon.wool.worry.entity.Worry;
+import com.ganzithon.wool.worry.entity.WorryTag;
+import com.ganzithon.wool.worry.entity.WorryVisibility;
+import com.ganzithon.wool.worry.repository.WorryRepository;
+import jakarta.persistence.EntityManager;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
+import org.springframework.http.MediaType;
+import org.springframework.test.web.servlet.MockMvc;
+import org.springframework.test.web.servlet.ResultActions;
+import org.springframework.transaction.annotation.Transactional;
+
+import java.time.Duration;
+import java.time.LocalDateTime;
+import java.util.List;
+import java.util.UUID;
+
+import static org.assertj.core.api.Assertions.assertThat;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+// REPORT-001 답장 신고
+@AutoConfigureMockMvc
+@Transactional
+class ReplyReportControllerTest extends IntegrationTestSupport {
+
+    @Autowired private MockMvc mockMvc;
+    @Autowired private UserRepository users;
+    @Autowired private WorryRepository worries;
+    @Autowired private ReplyRepository replies;
+    @Autowired private ReplyReportRepository reports;
+    @Autowired private NotificationRepository notifications;
+    @Autowired private EntityManager entityManager;
+
+    private User senior;
+    private User youth;
+    private Reply reply;
+
+    private static String name(String prefix) {
+        return prefix + UUID.randomUUID().toString().substring(0, 6);
+    }
+
+    private User newUser(Role role, String prefix) {
+        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
+    }
+
+    // 청년이 쓴 고민에 senior가 답장한 상태를 만든다.
+    private Reply replyFrom(User author, User by) {
+        Worry worry = worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+        return replies.saveAndFlush(Reply.text(worry, by, "답장"));
+    }
+
+    private ResultActions report(User who, Long replyId, String body) throws Exception {
+        var request = post("/api/replies/{replyId}/reports", replyId).contentType(MediaType.APPLICATION_JSON).content(body);
+        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
+    }
+
+    private ResultActions report(User who, Reply target, ReplyReportReason reason) throws Exception {
+        return report(who, target.getId(), "{\"reason\":\"" + reason + "\"}");
+    }
+
+    // 서로 다른 청년이 senior의 서로 다른 답장을 신고한다.
+    private void reportByNewYouth() throws Exception {
+        User other = newUser(Role.YOUTH, "신고청년");
+        report(other, replyFrom(other, senior), ReplyReportReason.ABUSE).andExpect(status().isCreated());
+    }
+
+    private User reloadSenior() {
+        entityManager.flush();
+        entityManager.clear();
+        return users.findById(senior.getId()).orElseThrow();
+    }
+
+    private List<Notification> sanctionNotifications() {
+        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
+                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
+    }
+
+    @BeforeEach
+    void setUp() {
+        senior = newUser(Role.SENIOR, "선배");
+        youth = newUser(Role.YOUTH, "청년");
+        reply = replyFrom(youth, senior);
+    }
+
+    // ---------- 신고와 숨김 ----------
+
+    @Test
+    void 신고하면_201과_래퍼로_알려주고_답장을_숨기고_신고를_저장한다() throws Exception {
+        report(youth, reply, ReplyReportReason.SEXUAL)
+                .andExpect(status().isCreated())
+                .andExpect(jsonPath("$.status").value(201))
+                .andExpect(jsonPath("$.code").value("REPORT_201_1"))
+                .andExpect(jsonPath("$.message").value("신고가 접수되었고 답장을 숨겼습니다."))
+                .andExpect(jsonPath("$.data.replyId").value(reply.getId()))
+                .andExpect(jsonPath("$.data.hidden").value(true));
+
+        entityManager.flush();
+        entityManager.clear();
+        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isTrue();
+        assertThat(reports.existsByReplyIdAndReporterId(reply.getId(), youth.getId())).isTrue();
+    }
+
+    @Test
+    void 사유는_다섯_가지_모두_받는다() throws Exception {
+        for (ReplyReportReason reason : ReplyReportReason.values()) {
+            User other = newUser(Role.YOUTH, "사유청년");
+            report(other, replyFrom(other, newUser(Role.SENIOR, "사유선배")), reason).andExpect(status().isCreated());
+        }
+    }
+
+    @Test
+    void 숨긴_답장은_누적_답장_수와_어르신_목록의_답장_수에서_빠진다() throws Exception {
+        replyFrom(newUser(Role.YOUTH, "다른청년"), senior); // 다른 고민의 답장 하나
+        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(2);
+
+        report(youth, reply, ReplyReportReason.AD).andExpect(status().isCreated());
+
+        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(1);
+        mockMvc.perform(get("/api/senior/wool").header("X-User-Id", senior.getId()))
+                .andExpect(jsonPath("$.data.totalReplies").value(1));
+    }
+
+    // ---------- 실패 ----------
+
+    @Test
+    void 같은_답장을_다시_신고하면_409다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+
+        report(youth, reply, ReplyReportReason.AD)
+                .andExpect(status().isConflict())
+                .andExpect(jsonPath("$.code").value("REPORT_409_001"))
+                .andExpect(jsonPath("$.message").value("이미 신고한 답장입니다."));
+        assertThat(reports.count()).isEqualTo(1);
+    }
+
+    @Test
+    void 내_고민의_답장이_아니면_403이고_신고도_숨김도_없다() throws Exception {
+        User stranger = newUser(Role.YOUTH, "남의청년");
+
+        report(stranger, reply, ReplyReportReason.ABUSE)
+                .andExpect(status().isForbidden())
+                .andExpect(jsonPath("$.code").value("REPLY_403_001"))
+                .andExpect(jsonPath("$.message").value("볼 수 없는 답장입니다."));
+
+        entityManager.flush();
+        entityManager.clear();
+        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isFalse();
+        assertThat(reports.count()).isZero();
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+    }
+
+    @Test
+    void 없거나_삭제된_답장은_404다() throws Exception {
+        report(youth, 999_999L, "{\"reason\":\"ABUSE\"}")
+                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));
+
+        mockMvc.perform(delete("/api/replies/{replyId}", reply.getId()).header("X-User-Id", senior.getId()))
+                .andExpect(status().isOk());
+        report(youth, reply, ReplyReportReason.ABUSE)
+                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));
+    }
+
+    @Test
+    void 사유가_없거나_잘못되면_400이다() throws Exception {
+        for (String body : new String[]{"{}", "{\"reason\":null}", "{\"reason\":\"HATE\"}", "{\"reason\":1}", "{\"reason\":\"abuse\"}", ""}) {
+            report(youth, reply.getId(), body).andExpect(status().isBadRequest());
+        }
+        entityManager.flush();
+        entityManager.clear();
+        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isFalse();
+        assertThat(reports.count()).isZero();
+    }
+
+    @Test
+    void 헤더가_없으면_401_어르신이면_403이다() throws Exception {
+        report(null, reply, ReplyReportReason.ABUSE)
+                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
+        report(senior, reply, ReplyReportReason.ABUSE)
+                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
+    }
+
+    // ---------- 제재 ----------
+
+    @Test
+    void 첫_신고에서_경고가_적용되고_알림이_간다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+
+        User after = reloadSenior();
+        assertThat(after.getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+        assertThat(after.getRestrictedUntil()).isNull();
+        List<Notification> sent = sanctionNotifications();
+        assertThat(sent).hasSize(1);
+        assertThat(sent.get(0).getTargetKind()).isEqualTo(TargetKind.SANCTION);
+        assertThat(sent.get(0).getTargetId()).isNull();
+        assertThat(sent.get(0).getTitle()).isEqualTo("답장에 대한 경고가 있어요");
+    }
+
+    @Test
+    void 신고한_청년이_서로_다르게_셋이면_7일_답장_제한이고_다섯이면_영구_제한이다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated()); // 1명: 경고
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+
+        reportByNewYouth(); // 2명: 그대로
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+        assertThat(sanctionNotifications()).hasSize(1);
+
+        LocalDateTime before = LocalDateTime.now();
+        reportByNewYouth(); // 3명: 7일 제한
+        User restricted = reloadSenior();
+        assertThat(restricted.getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+        assertThat(Duration.between(before.plusDays(7), restricted.getRestrictedUntil()).abs()).isLessThan(Duration.ofMinutes(1));
+        assertThat(sanctionNotifications()).hasSize(2);
+        assertThat(sanctionNotifications().get(0).getTitle()).isEqualTo("7일 동안 답장을 쓸 수 없어요");
+
+        reportByNewYouth(); // 4명: 그대로
+        User stillRestricted = reloadSenior();
+        assertThat(stillRestricted.getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+        assertThat(stillRestricted.getRestrictedUntil()).isEqualTo(restricted.getRestrictedUntil());
+        assertThat(sanctionNotifications()).hasSize(2);
+
+        reportByNewYouth(); // 5명: 영구 제한
+        User banned = reloadSenior();
+        assertThat(banned.getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
+        assertThat(banned.getRestrictedUntil()).isNull();
+        assertThat(sanctionNotifications()).hasSize(3);
+        assertThat(sanctionNotifications().get(0).getTitle()).isEqualTo("더 이상 답장을 쓸 수 없어요");
+    }
+
+    // 7일 제한이 풀린 뒤에도 누적 수는 그대로다. 같은 청년의 추가 신고나 4번째 신고로 다시 제한하지 않는다.
+    @Test
+    void 제한이_풀린_뒤에는_같은_청년의_추가_신고나_네_번째_신고로_다시_제한하지_않는다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        reportByNewYouth();
+        reportByNewYouth(); // 3명: 7일 제한
+        User restricted = reloadSenior();
+        restricted.liftRestriction();
+        users.saveAndFlush(restricted);
+        assertThat(sanctionNotifications()).hasSize(2);
+
+        report(youth, replyFrom(youth, senior), ReplyReportReason.AD).andExpect(status().isCreated()); // 같은 청년, 누적 3 그대로
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+
+        reportByNewYouth(); // 4명
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
+        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(4);
+        assertThat(sanctionNotifications()).hasSize(2);
+    }
+
+    // 이미 더 높은 단계(영구 제한)인 어르신은 낮은 단계 기준에 도달해도 단계가 내려가거나 알림이 가지 않는다.
+    @Test
+    void 이미_영구_제한인_어르신은_낮은_단계_기준에_도달해도_내려가지_않는다() throws Exception {
+        senior.applySanction(SanctionLevel.BANNED, null);
+        users.saveAndFlush(senior);
+
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        reportByNewYouth();
+        reportByNewYouth(); // 누적 3명: 7일 제한 기준에 도달
+
+        User after = reloadSenior();
+        assertThat(after.getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
+        assertThat(after.getRestrictedUntil()).isNull();
+        assertThat(sanctionNotifications()).isEmpty();
+    }
+
+    @Test
+    void 같은_청년이_같은_어르신의_다른_답장을_또_신고해도_누적_신고_수는_늘지_않는다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        Reply second = replyFrom(youth, senior);
+        Reply third = replyFrom(youth, senior);
+
+        report(youth, second, ReplyReportReason.AD).andExpect(status().isCreated());
+        report(youth, third, ReplyReportReason.ETC).andExpect(status().isCreated());
+
+        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(1);
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+        assertThat(sanctionNotifications()).hasSize(1);
+        // 답장은 세 통 모두 숨겨진다.
+        assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
+    }
+
+    @Test
+    void 어르신이_삭제한_답장의_신고도_누적_수에_계속_센다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated()); // 1명
+        mockMvc.perform(delete("/api/replies/{replyId}", reply.getId()).header("X-User-Id", senior.getId()))
+                .andExpect(status().isOk());
+        reportByNewYouth(); // 2명
+        reportByNewYouth(); // 3명
+
+        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(3);
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+    }
+
+    @Test
+    void 다른_어르신의_신고는_섞이지_않는다() throws Exception {
+        User other = newUser(Role.SENIOR, "다른선배");
+        for (int i = 0; i < 3; i++) {
+            User reporter = newUser(Role.YOUTH, "남청년");
+            report(reporter, replyFrom(reporter, other), ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        }
+
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+
+        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
+        assertThat(users.findById(other.getId()).orElseThrow().getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
+    }
+
+    @Test
+    void 제한된_어르신은_답장을_쓸_수_없다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        reportByNewYouth();
+        reportByNewYouth(); // 3명: 7일 제한
+        Worry target = worries.saveAndFlush(Worry.of(youth, "새 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+
+        mockMvc.perform(post("/api/worries/{worryId}/replies", target.getId())
+                        .header("X-User-Id", senior.getId())
+                        .contentType(MediaType.APPLICATION_JSON)
+                        .content("{\"type\":\"TEXT\",\"content\":\"답장\"}"))
+                .andExpect(status().isForbidden())
+                .andExpect(jsonPath("$.code").value("REPLY_403_002"))
+                .andExpect(jsonPath("$.data.restrictedUntil").exists());
+    }
+
+    @Test
+    void 경고_단계는_답장을_막지_않는다() throws Exception {
+        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
+        Worry target = worries.saveAndFlush(Worry.of(youth, "새 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+
+        mockMvc.perform(post("/api/worries/{worryId}/replies", target.getId())
+                        .header("X-User-Id", senior.getId())
+                        .contentType(MediaType.APPLICATION_JSON)
+                        .content("{\"type\":\"TEXT\",\"content\":\"답장\"}"))
+                .andExpect(status().isCreated());
+    }
+}
diff --git a/src/test/java/com/ganzithon/wool/report/service/RepeatableReadConcurrencyTest.java b/src/test/java/com/ganzithon/wool/report/service/RepeatableReadConcurrencyTest.java
new file mode 100644
index 0000000..8c9d0ef
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/service/RepeatableReadConcurrencyTest.java
@@ -0,0 +1,281 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.DatabaseCleaner;
+import com.ganzithon.wool.IntegrationTestSupport;
+import com.ganzithon.wool.certificate.repository.CertificateRepository;
+import com.ganzithon.wool.notification.entity.Notification;
+import com.ganzithon.wool.notification.entity.NotificationType;
+import com.ganzithon.wool.notification.repository.NotificationRepository;
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import com.ganzithon.wool.report.repository.ReplyReportRepository;
+import com.ganzithon.wool.reply.entity.Reply;
+import com.ganzithon.wool.reply.entity.ReplyType;
+import com.ganzithon.wool.reply.repository.ReplyRepository;
+import com.ganzithon.wool.reply.request.CreateReplyRequest;
+import com.ganzithon.wool.reply.response.CreateReplyResponse;
+import com.ganzithon.wool.reply.response.DeleteReplyResponse;
+import com.ganzithon.wool.reply.service.ReplyCreateService;
+import com.ganzithon.wool.reply.service.ReplyDeleteService;
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
+import org.springframework.test.context.TestPropertySource;
+import org.springframework.transaction.PlatformTransactionManager;
+import org.springframework.transaction.support.TransactionTemplate;
+
+import java.util.ArrayList;
+import java.util.List;
+import java.util.UUID;
+import java.util.concurrent.Callable;
+import java.util.concurrent.CountDownLatch;
+import java.util.concurrent.ExecutorService;
+import java.util.concurrent.Executors;
+import java.util.concurrent.Future;
+import java.util.concurrent.TimeUnit;
+import java.util.concurrent.atomic.AtomicInteger;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+// 운영 MySQL의 기본 격리 수준(REPEATABLE READ)에서도 누적 수를 바꾸는 서비스(답장 작성·삭제·신고)가 어긋나지 않는지 확인한다.
+// 이 테스트는 연결의 기본 격리 수준을 REPEATABLE READ로 두고(별도 H2 DB), 서비스가 선언한 READ COMMITTED가 그것을 덮어써서
+// "잠금을 기다린 뒤에도 트랜잭션 시작 시점의 오래된 수를 읽는" 문제를 막는지 본다.
+// 신고 테스트는 두 신고가 잠금 전 조회를 마치고 어르신 행 잠금에서 함께 기다리게 만든 뒤 풀어 주므로 순서가 매번 같다.
+// 서비스의 @Transactional(isolation = READ_COMMITTED)를 빼면 아래 테스트가 실패한다.
+// H2의 REPEATABLE READ는 MySQL보다 스냅샷을 더 일찍 만들어서, 이 테스트는 선언이 지워지는 것을 막는 안전망이다.
+@TestPropertySource(properties = {
+        "spring.datasource.url=jdbc:h2:mem:test_repeatable_read;MODE=MySQL",
+        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
+})
+class RepeatableReadConcurrencyTest extends IntegrationTestSupport {
+
+    @Autowired private UserRepository users;
+    @Autowired private WorryRepository worries;
+    @Autowired private ReplyRepository replies;
+    @Autowired private ReplyReportRepository reports;
+    @Autowired private CertificateRepository certificates;
+    @Autowired private NotificationRepository notifications;
+    @Autowired private ReplyReportService replyReportService;
+    @Autowired private ReplyCreateService replyCreateService;
+    @Autowired private ReplyDeleteService replyDeleteService;
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
+    private Worry newWorry(User author) {
+        return worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+    }
+
+    private record Target(User youth, Reply reply) {
+    }
+
+    private Target newTarget(User senior) {
+        User youth = newUser(Role.YOUTH, "격리청년");
+        return new Target(youth, replies.saveAndFlush(Reply.text(newWorry(youth), senior, "답장")));
+    }
+
+    private List<Notification> sanctionNotifications(User senior) {
+        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
+                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
+    }
+
+    private boolean waitUntilBlockedBy(int holderSessionId, int expected, long timeoutMillis) throws InterruptedException {
+        long deadline = System.currentTimeMillis() + timeoutMillis;
+        while (System.currentTimeMillis() < deadline) {
+            Integer waiting = jdbc.queryForObject(
+                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, holderSessionId);
+            if (waiting != null && waiting >= expected) {
+                return true;
+            }
+            Thread.sleep(20);
+        }
+        return false;
+    }
+
+    // 어르신에 대한 신고자가 existing명인 상태에서 서로 다른 두 청년이 동시에 신고한다. 마지막 신고가 제재 기준을 넘는다.
+    private void reportTwoAtOnce(User senior, int existing, SanctionLevel expectedLevel, int expectedNotifications) throws Exception {
+        for (int i = 0; i < existing; i++) {
+            Target target = newTarget(senior);
+            replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE);
+        }
+        Target first = newTarget(senior);
+        Target second = newTarget(senior);
+
+        // 결정적으로 만든다: 별도 트랜잭션이 어르신 행을 쥔 채로 두 신고를 시작해, 두 신고가 모두 잠금 앞에서
+        // (잠금 전 일반 조회로 스냅샷을 만든 뒤) 기다리는 것을 확인하고 나서 풀어 준다.
+        ExecutorService pool = Executors.newFixedThreadPool(3);
+        CountDownLatch locked = new CountDownLatch(1);
+        CountDownLatch release = new CountDownLatch(1);
+        AtomicInteger holderSessionId = new AtomicInteger(-1);
+        try {
+            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
+                seniorLock.lock(senior.getId());
+                holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
+                locked.countDown();
+                try {
+                    release.await(60, TimeUnit.SECONDS);
+                } catch (InterruptedException e) {
+                    Thread.currentThread().interrupt();
+                }
+                return null;
+            }));
+            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
+
+            List<Future<String>> results = new ArrayList<>();
+            for (Target target : List.of(first, second)) {
+                Callable<String> task = () -> {
+                    replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.AD);
+                    return "ok";
+                };
+                results.add(pool.submit(task));
+            }
+            assertThat(waitUntilBlockedBy(holderSessionId.get(), 2, 15_000))
+                    .as("두 신고가 모두 잠금을 쥔 세션(%d)에 막혀 있어야 한다", holderSessionId.get()).isTrue();
+
+            release.countDown();
+            holder.get(10, TimeUnit.SECONDS);
+            for (Future<String> result : results) {
+                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("ok");
+            }
+        } finally {
+            release.countDown();
+            pool.shutdownNow();
+        }
+
+        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(existing + 2);
+        assertThat(users.findById(senior.getId()).orElseThrow().getSanctionLevel()).isEqualTo(expectedLevel);
+        assertThat(sanctionNotifications(senior)).hasSize(expectedNotifications);
+    }
+
+    // 신고자 1명(경고) → 동시에 2명 더: 3명째가 7일 제한이다. 오래된 수를 읽으면 제한이 빠진다.
+    @Test
+    void 동시에_신고해_세_번째_기준을_넘어도_7일_제한이_빠지지_않는다() throws Exception {
+        for (int round = 0; round < 8; round++) {
+            User senior = newUser(Role.SENIOR, "격리선배");
+            reportTwoAtOnce(senior, 1, SanctionLevel.RESTRICTED, 2);
+        }
+    }
+
+    // 신고자 3명(7일 제한) → 동시에 2명 더: 5명째가 영구 제한이다.
+    @Test
+    void 동시에_신고해_다섯_번째_기준을_넘어도_영구_제한이_빠지지_않는다() throws Exception {
+        for (int round = 0; round < 6; round++) {
+            User senior = newUser(Role.SENIOR, "격리선배");
+            reportTwoAtOnce(senior, 3, SanctionLevel.BANNED, 3);
+        }
+    }
+
+    // 19통인 어르신이 서로 다른 고민에 동시에 답장하면 20통째 감사장이 한 장만 발급된다.
+    @Test
+    void 동시에_답장해도_스무_번째_감사장은_한_장만_발급된다() throws Exception {
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        try {
+            for (int round = 0; round < 6; round++) {
+                User senior = newUser(Role.SENIOR, "격리선배");
+                for (int i = 0; i < 19; i++) {
+                    newTarget(senior);
+                }
+                Long worry1 = newWorry(newUser(Role.YOUTH, "격리청년")).getId();
+                Long worry2 = newWorry(newUser(Role.YOUTH, "격리청년")).getId();
+
+                CountDownLatch ready = new CountDownLatch(2);
+                CountDownLatch go = new CountDownLatch(1);
+                List<Future<CreateReplyResponse>> results = new ArrayList<>();
+                for (Long worryId : List.of(worry1, worry2)) {
+                    Callable<CreateReplyResponse> task = () -> {
+                        ready.countDown();
+                        go.await();
+                        return replyCreateService.create(senior, worryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null));
+                    };
+                    results.add(pool.submit(task));
+                }
+                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
+                go.countDown();
+                List<Integer> totals = new ArrayList<>();
+                for (Future<CreateReplyResponse> result : results) {
+                    totals.add((int) result.get(30, TimeUnit.SECONDS).totalReplies());
+                }
+
+                assertThat(totals).as("%d번째 라운드", round).containsExactlyInAnyOrder(20, 21);
+                assertThat(certificates.findBySeniorIdOrderByIssuedAtDescIdDesc(senior.getId()))
+                        .as("%d번째 라운드", round).hasSize(1);
+            }
+        } finally {
+            pool.shutdownNow();
+        }
+    }
+
+    // 19통인 어르신이 한 통을 지우는 것과 새 답장을 쓰는 것이 겹쳐도 한 순서로 맞아떨어진다.
+    @Test
+    void 동시에_삭제와_작성이_겹쳐도_누적_수와_감사장이_어느_한_순서로_맞아떨어진다() throws Exception {
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        try {
+            for (int round = 0; round < 6; round++) {
+                User senior = newUser(Role.SENIOR, "격리선배");
+                Long deleteId = null;
+                for (int i = 0; i < 19; i++) {
+                    Target target = newTarget(senior);
+                    if (deleteId == null) deleteId = target.reply().getId();
+                }
+                Long targetReplyId = deleteId;
+                Long newWorryId = newWorry(newUser(Role.YOUTH, "격리청년")).getId();
+
+                CountDownLatch ready = new CountDownLatch(2);
+                CountDownLatch go = new CountDownLatch(1);
+                Callable<CreateReplyResponse> createTask = () -> {
+                    ready.countDown();
+                    go.await();
+                    return replyCreateService.create(senior, newWorryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null));
+                };
+                Callable<DeleteReplyResponse> deleteTask = () -> {
+                    ready.countDown();
+                    go.await();
+                    return replyDeleteService.delete(senior, targetReplyId);
+                };
+                Future<CreateReplyResponse> created = pool.submit(createTask);
+                Future<DeleteReplyResponse> deleted = pool.submit(deleteTask);
+                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
+                go.countDown();
+                CreateReplyResponse createResult = created.get(30, TimeUnit.SECONDS);
+                DeleteReplyResponse deleteResult = deleted.get(30, TimeUnit.SECONDS);
+
+                boolean createFirst = createResult.totalReplies() == 20;
+                if (createFirst) {
+                    assertThat(createResult.certificateIssued()).as("%d번째 라운드", round).isTrue();
+                    assertThat(deleteResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(19);
+                } else {
+                    assertThat(createResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(19);
+                    assertThat(createResult.certificateIssued()).as("%d번째 라운드", round).isFalse();
+                    assertThat(deleteResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(18);
+                }
+                assertThat(replies.countVisibleBySeniorId(senior.getId())).as("%d번째 라운드", round).isEqualTo(19);
+            }
+        } finally {
+            pool.shutdownNow();
+        }
+    }
+}
diff --git a/src/test/java/com/ganzithon/wool/report/service/ReplyReportConcurrencyTest.java b/src/test/java/com/ganzithon/wool/report/service/ReplyReportConcurrencyTest.java
new file mode 100644
index 0000000..1b72624
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/service/ReplyReportConcurrencyTest.java
@@ -0,0 +1,215 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.DatabaseCleaner;
+import com.ganzithon.wool.IntegrationTestSupport;
+import com.ganzithon.wool.global.exception.BusinessException;
+import com.ganzithon.wool.notification.entity.Notification;
+import com.ganzithon.wool.notification.entity.NotificationType;
+import com.ganzithon.wool.notification.repository.NotificationRepository;
+import com.ganzithon.wool.report.entity.ReplyReportReason;
+import com.ganzithon.wool.report.exception.ReportErrorCode;
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
+import java.util.ArrayList;
+import java.util.List;
+import java.util.UUID;
+import java.util.concurrent.Callable;
+import java.util.concurrent.CountDownLatch;
+import java.util.concurrent.ExecutorService;
+import java.util.concurrent.Executors;
+import java.util.concurrent.Future;
+import java.util.concurrent.TimeUnit;
+import java.util.concurrent.atomic.AtomicInteger;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+// 같은 어르신을 두 청년이 동시에 신고해도 누적 신고 수와 제재 알림이 한 번씩만 맞아떨어지는지 확인한다.
+// 답장 신고는 답장 행 → 어르신 행 순서로 잠근다. (답장 삭제와 같은 순서, 답장 작성은 고민 행 → 어르신 행)
+// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
+class ReplyReportConcurrencyTest extends IntegrationTestSupport {
+
+    @Autowired private UserRepository users;
+    @Autowired private WorryRepository worries;
+    @Autowired private ReplyRepository replies;
+    @Autowired private ReplyReportRepository reports;
+    @Autowired private NotificationRepository notifications;
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
+    private record Target(User youth, Reply reply) {
+    }
+
+    // 청년 한 명이 쓴 고민에 senior가 답장한 상태.
+    private Target newTarget(User senior) {
+        User youth = newUser(Role.YOUTH, "동신청년");
+        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
+        return new Target(youth, replies.saveAndFlush(Reply.text(worry, senior, "답장")));
+    }
+
+    private List<Notification> sanctionNotifications(User senior) {
+        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
+                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
+    }
+
+    // 서로 다른 두 청년이 같은 어르신의 서로 다른 답장을 동시에 신고한다.
+    // 잠금이 없으면 둘 다 "내가 첫 신고"라고 보고 경고 알림이 두 번 갈 수 있다.
+    @Test
+    void 두_청년이_같은_어르신을_동시에_신고해도_경고_알림은_한_번만_간다() throws Exception {
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        try {
+            for (int round = 0; round < 15; round++) {
+                User senior = newUser(Role.SENIOR, "동신선배");
+                Target first = newTarget(senior);
+                Target second = newTarget(senior);
+
+                CountDownLatch ready = new CountDownLatch(2);
+                CountDownLatch go = new CountDownLatch(1);
+                List<Future<String>> results = new ArrayList<>();
+                for (Target target : List.of(first, second)) {
+                    Callable<String> task = () -> {
+                        ready.countDown();
+                        go.await();
+                        replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE);
+                        return "ok";
+                    };
+                    results.add(pool.submit(task));
+                }
+                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
+                go.countDown();
+                for (Future<String> result : results) {
+                    assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("ok");
+                }
+
+                assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).as("%d번째 라운드", round).isEqualTo(2);
+                assertThat(users.findById(senior.getId()).orElseThrow().getSanctionLevel())
+                        .as("%d번째 라운드", round).isEqualTo(SanctionLevel.WARNED);
+                assertThat(sanctionNotifications(senior)).as("%d번째 라운드", round).hasSize(1);
+            }
+        } finally {
+            pool.shutdownNow();
+        }
+    }
+
+    // 같은 청년이 같은 답장을 동시에 신고하면 한 번만 접수되고 나머지는 409다.
+    @Test
+    void 같은_청년이_같은_답장을_동시에_신고하면_한_번만_접수된다() throws Exception {
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        try {
+            for (int round = 0; round < 15; round++) {
+                User senior = newUser(Role.SENIOR, "동신선배");
+                Target target = newTarget(senior);
+
+                CountDownLatch ready = new CountDownLatch(2);
+                CountDownLatch go = new CountDownLatch(1);
+                Callable<String> task = () -> {
+                    ready.countDown();
+                    go.await();
+                    try {
+                        replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.AD);
+                        return "접수";
+                    } catch (BusinessException e) {
+                        return e.getErrorCode() == ReportErrorCode.ALREADY_REPORTED ? "409" : "다른 오류: " + e.getErrorCode();
+                    }
+                };
+                Future<String> a = pool.submit(task);
+                Future<String> b = pool.submit(task);
+                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
+                go.countDown();
+
+                assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
+                        .as("%d번째 라운드", round).containsExactlyInAnyOrder("접수", "409");
+                assertThat(reports.count()).as("%d번째 라운드", round).isEqualTo(round + 1);
+                assertThat(sanctionNotifications(senior)).as("%d번째 라운드", round).hasSize(1);
+            }
+        } finally {
+            pool.shutdownNow();
+        }
+    }
+
+    // 다른 트랜잭션이 어르신 행을 쥐고 있으면 신고는 풀릴 때까지 기다린다. 잠금이 없으면 바로 끝나 실패한다.
+    @Test
+    void 다른_트랜잭션이_어르신_행을_잠그고_있으면_신고는_풀릴_때까지_기다린다() throws Exception {
+        User senior = newUser(Role.SENIOR, "잠금선배");
+        Target target = newTarget(senior);
+
+        ExecutorService pool = Executors.newFixedThreadPool(2);
+        CountDownLatch locked = new CountDownLatch(1);
+        CountDownLatch release = new CountDownLatch(1);
+        AtomicInteger holderSessionId = new AtomicInteger(-1);
+        try {
+            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
+                seniorLock.lock(senior.getId());
+                holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
+                locked.countDown();
+                try {
+                    release.await(30, TimeUnit.SECONDS);
+                } catch (InterruptedException e) {
+                    Thread.currentThread().interrupt();
+                }
+                return null;
+            }));
+            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
+
+            Future<Object> blocked = pool.submit(
+                    () -> replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE));
+            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000))
+                    .as("잠금을 쥔 세션(%d)에 막힌 세션", holderSessionId.get()).isTrue();
+            assertThat(blocked.isDone()).isFalse();
+
+            release.countDown();
+            holder.get(10, TimeUnit.SECONDS);
+            blocked.get(30, TimeUnit.SECONDS);
+            assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
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
diff --git a/src/test/java/com/ganzithon/wool/report/service/SanctionPolicyTest.java b/src/test/java/com/ganzithon/wool/report/service/SanctionPolicyTest.java
new file mode 100644
index 0000000..33a8290
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/report/service/SanctionPolicyTest.java
@@ -0,0 +1,24 @@
+package com.ganzithon.wool.report.service;
+
+import com.ganzithon.wool.user.entity.SanctionLevel;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.params.ParameterizedTest;
+import org.junit.jupiter.params.provider.ValueSource;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+class SanctionPolicyTest {
+
+    @Test
+    void 누적_신고_수가_1_3_5가_되는_순간에만_단계가_바뀐다() {
+        assertThat(SanctionPolicy.levelReachedAt(1)).isEqualTo(SanctionLevel.WARNED);
+        assertThat(SanctionPolicy.levelReachedAt(3)).isEqualTo(SanctionLevel.RESTRICTED);
+        assertThat(SanctionPolicy.levelReachedAt(5)).isEqualTo(SanctionLevel.BANNED);
+    }
+
+    @ParameterizedTest
+    @ValueSource(longs = {0, 2, 4, 6, 7, 100})
+    void 그_밖의_수에서는_단계가_바뀌지_않는다(long count) {
+        assertThat(SanctionPolicy.levelReachedAt(count)).isNull();
+    }
+}
```
