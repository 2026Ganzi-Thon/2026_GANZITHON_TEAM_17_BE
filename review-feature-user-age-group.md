너는 Spring Boot 백엔드를 리뷰하는 시니어 개발자다. 아래 PR을 독립적으로 검토해줘. 이 요청에 적힌 설명이 맞는지부터 의심하고, 코드와 마이그레이션을 직접 대조해서 판단해줘. 코드 스타일 지적은 필요 없다.

# 1. 배경
- 서비스 "한 올": 로그인 없이 역할(YOUTH/SENIOR)과 나이대만 골라 가입한다(POL-MBM-01). 본인 인증이 없다. Java 21, Spring Boot 4.1, JPA, Flyway, MySQL 8(운영) / H2(테스트, Flyway 꺼짐).
- 디자인이 확정됐다: 청년은 20·30·40대, 어르신은 50·60·70·80대 이상 중에서 고른다. 기존 값은 `20S, 30S, 40S, 50S, 60S, 70S_PLUS`(70대 이상)였다.
- 이미 서버에 배포돼 있고 테스트 사용자가 있다. Flyway `V1`, `V2`는 이미 적용됐으므로 수정하면 안 된다(체크섬).

# 2. 이 PR이 하는 일
- `AgeGroup`: `70S_PLUS`를 없애고 `70S`, `80S_PLUS`를 추가. 최종 값은 `20S, 30S, 40S, 50S, 60S, 70S, 80S_PLUS`(7개). 외부 값은 `@JsonValue`, DB 값은 `AgeGroupConverter`가 매핑한다(수정 없음).
- `User`의 `columnDefinition` ENUM을 새 값 7개로 변경(테스트용 H2 스키마 생성에 쓰인다).
- `V3__split_age_group_70s.sql`: ① ENUM에 새 값과 옛 값을 모두 포함해 확장 → ② 기존 `70S_PLUS` 행을 `70S`로 변환(80대 이상인지 알 수 없으므로) → ③ 옛 값을 뺀 최종 ENUM으로 축소.
- 역할별 나이 제한은 서버에서 두지 않는다(결정). 본인 인증이 없어 요청에 적힌 값만 믿을 수 있어서 서버가 막아도 실효가 없다. 화면이 역할별로 선택지를 보여 준다. 서버는 값 7개만 검사한다(정책 POL-MBM-04 유지, 문구는 노션에서 갱신).
- 테스트: `70S_PLUS`를 쓰던 6곳을 새 값으로 바꾸고, 가입 테스트에 `70S`, `80S_PLUS` 허용과 옛 값 `70S_PLUS`·잘못된 값 `80S` 거부(400)를 추가.
- 검증한 것: 전체 `./gradlew build` 통과(836개). 일회용 MySQL 8 컨테이너에서 V1→V2 적용, `70S_PLUS`·`60S`·`20S` 사용자를 넣고 V3를 적용: `70S_PLUS`는 `70S`로 바뀌고 나머지는 그대로, 새 값 `80S_PLUS` 삽입 성공, 옛 값 `70S_PLUS` 삽입은 `Data truncated`로 거부, 최종 컬럼은 `enum('20S','30S','40S','50S','60S','70S','80S_PLUS') NOT NULL`.

# 3. 특히 봐줬으면 하는 곳
1. `V3`의 ENUM 확장 → 변환 → 축소 순서가 MySQL 8에서 안전한지(데이터 손실, 잠금, 엄격 모드), 이미 적용된 V1·V2 체크섬에 영향이 없는지, 컬럼 `COMMENT`와 `NOT NULL`이 유지되는지.
2. `70S_PLUS` 사용자를 `70S`로 옮기는 결정이 합리적인지(80대 이상 여부를 알 수 없음).
3. 역할별 나이 제한을 두지 않는 결정(본인 인증 없음)이 타당한지, 다른 곳(응답, 목록)에서 `70S_PLUS`를 전제한 코드가 남지 않았는지.
4. 옛 값 `70S_PLUS`를 보내는 프론트가 있을 때 400이 되는 것이 허용 가능한지(프론트에는 값 변경을 알린다).
5. 테스트가 실제로 보호를 검증하는지.

# 4. 알려진 한계 (지적하지 않아도 된다)
- 운영 서버의 기존 `70S_PLUS` 사용자는 마이그레이션 후 `70S`로 표시된다. 해커톤 테스트 데이터라서 허용한다.
- 마이그레이션은 H2 테스트에서 실행되지 않는다(Flyway가 꺼져 있다). MySQL 8 컨테이너에서 따로 검증했다.

# 5. 출력 형식
- 항목마다: 심각도(높음/중간/낮음), 파일과 줄, 구체적 시나리오, 고치는 방향
- 문제가 없으면 없다고만 쓰고, 억지로 지적을 만들지 마. 스타일 지적은 하지 마.
- 답변은 파일로 만들지 말고 채팅창에 바로 출력해줘. 내가 그대로 복사해 붙여넣을 수 있게 답변 전체를 마크다운으로 쓰고, 중간에 블록이 끊기거나 벗겨지지 않도록 답변 전체를 백틱 4개(````)로 감싼 코드 블록 하나에 담아줘. 그 안에서는 백틱 3개(```) 코드 블록을 쓰지 말고, 코드 예시는 들여쓰기나 인라인 코드로 써줘.

# 6. 변경 내용 (git diff, 새 파일은 전체가 + 로 표시됨)
```diff
diff --git a/src/main/java/com/ganzithon/wool/user/entity/AgeGroup.java b/src/main/java/com/ganzithon/wool/user/entity/AgeGroup.java
index 440636a..05a0463 100644
--- a/src/main/java/com/ganzithon/wool/user/entity/AgeGroup.java
+++ b/src/main/java/com/ganzithon/wool/user/entity/AgeGroup.java
@@ -17,7 +17,8 @@ public enum AgeGroup {
     AGE_40S("40S"),
     AGE_50S("50S"),
     AGE_60S("60S"),
-    AGE_70S_PLUS("70S_PLUS");
+    AGE_70S("70S"),
+    AGE_80S_PLUS("80S_PLUS");
 
     private final String value;
 
diff --git a/src/main/java/com/ganzithon/wool/user/entity/User.java b/src/main/java/com/ganzithon/wool/user/entity/User.java
index 35a19e2..5c4baad 100644
--- a/src/main/java/com/ganzithon/wool/user/entity/User.java
+++ b/src/main/java/com/ganzithon/wool/user/entity/User.java
@@ -31,7 +31,7 @@ public class User extends BaseCreatedEntity {
 
     @Convert(converter = AgeGroupConverter.class)
     @Column(name = "age_group", nullable = false,
-            columnDefinition = "ENUM('20S', '30S', '40S', '50S', '60S', '70S_PLUS')")
+            columnDefinition = "ENUM('20S', '30S', '40S', '50S', '60S', '70S', '80S_PLUS')")
     private AgeGroup ageGroup;
 
     @Column(name = "nickname", length = 10, nullable = false, unique = true)
diff --git a/src/test/java/com/ganzithon/wool/reply/controller/SeniorWorryControllerTest.java b/src/test/java/com/ganzithon/wool/reply/controller/SeniorWorryControllerTest.java
index a4c5c47..243053a 100644
--- a/src/test/java/com/ganzithon/wool/reply/controller/SeniorWorryControllerTest.java
+++ b/src/test/java/com/ganzithon/wool/reply/controller/SeniorWorryControllerTest.java
@@ -96,7 +96,7 @@ class SeniorWorryControllerTest extends IntegrationTestSupport {
     void setUp() {
         youth = newUser(Role.YOUTH, AgeGroup.AGE_20S, "청년");
         senior = newUser(Role.SENIOR, AgeGroup.AGE_60S, "선배");
-        other = newUser(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "다른선배");
+        other = newUser(Role.SENIOR, AgeGroup.AGE_80S_PLUS, "다른선배");
     }
 
     // ================= REPLY-002 목록 =================
@@ -295,7 +295,7 @@ class SeniorWorryControllerTest extends IntegrationTestSupport {
                 .andExpect(jsonPath("$.data.otherReplies[0].senior.ageGroup").value("50S"))
                 .andExpect(jsonPath("$.data.otherReplies[0].createdAt").exists())
                 .andExpect(jsonPath("$.data.otherReplies[1].replyId").value(first.getId()))
-                .andExpect(jsonPath("$.data.otherReplies[1].senior.ageGroup").value("70S_PLUS"));
+                .andExpect(jsonPath("$.data.otherReplies[1].senior.ageGroup").value("80S_PLUS"));
     }
 
     @Test
diff --git a/src/test/java/com/ganzithon/wool/user/controller/MyInfoControllerTest.java b/src/test/java/com/ganzithon/wool/user/controller/MyInfoControllerTest.java
index ea5b70a..f016e27 100644
--- a/src/test/java/com/ganzithon/wool/user/controller/MyInfoControllerTest.java
+++ b/src/test/java/com/ganzithon/wool/user/controller/MyInfoControllerTest.java
@@ -44,13 +44,13 @@ class MyInfoControllerTest extends IntegrationTestSupport {
 
     @Test
     void 어르신의_제한_상태와_해제_시각을_반환한다() throws Exception {
-        User user = User.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "조회선배");
+        User user = User.join(Role.SENIOR, AgeGroup.AGE_80S_PLUS, "조회선배");
         user.applySanction(SanctionLevel.RESTRICTED, LocalDateTime.of(2026, 11, 20, 12, 30));
         userRepository.saveAndFlush(user);
         mockMvc.perform(get("/api/users/me").header("X-User-Id", user.getId()))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.role").value("SENIOR"))
-                .andExpect(jsonPath("$.data.ageGroup").value("70S_PLUS"))
+                .andExpect(jsonPath("$.data.ageGroup").value("80S_PLUS"))
                 .andExpect(jsonPath("$.data.sanction.level").value("RESTRICTED"))
                 .andExpect(jsonPath("$.data.sanction.restrictedUntil").value(endsWith("+09:00")));
     }
diff --git a/src/test/java/com/ganzithon/wool/user/controller/UserControllerTest.java b/src/test/java/com/ganzithon/wool/user/controller/UserControllerTest.java
index 2cae79c..424c3a1 100644
--- a/src/test/java/com/ganzithon/wool/user/controller/UserControllerTest.java
+++ b/src/test/java/com/ganzithon/wool/user/controller/UserControllerTest.java
@@ -42,14 +42,14 @@ class UserControllerTest extends IntegrationTestSupport {
     @Test
     void 가입하면_201과_발급된_사용자_정보를_래퍼로_내려준다() throws Exception {
         mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
-                        .content("{\"role\":\"SENIOR\",\"ageGroup\":\"70S_PLUS\"}"))
+                        .content("{\"role\":\"SENIOR\",\"ageGroup\":\"80S_PLUS\"}"))
                 .andExpect(status().isCreated())
                 .andExpect(jsonPath("$.status").value(201))
                 .andExpect(jsonPath("$.code").value("USER_201_1"))
                 .andExpect(jsonPath("$.message").value("가입에 성공했습니다."))
                 .andExpect(jsonPath("$.data.userId", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                 .andExpect(jsonPath("$.data.role").value("SENIOR"))
-                .andExpect(jsonPath("$.data.ageGroup").value("70S_PLUS"))
+                .andExpect(jsonPath("$.data.ageGroup").value("80S_PLUS"))
                 .andExpect(jsonPath("$.data.nickname", matchesPattern("[가-힣]+ 양 [1-9]\\d{2}")))
                 // 명세의 네 필드 외에 다른 값이 내려가지 않는다.
                 .andExpect(jsonPath("$.data.length()").value(4));
@@ -68,7 +68,7 @@ class UserControllerTest extends IntegrationTestSupport {
     }
 
     @ParameterizedTest
-    @ValueSource(strings = {"20S", "30S", "40S", "50S", "60S", "70S_PLUS"})
+    @ValueSource(strings = {"20S", "30S", "40S", "50S", "60S", "70S", "80S_PLUS"})
     void 청년과_어르신_모두_모든_나이대로_가입할_수_있다(String ageGroup) throws Exception {
         for (String role : new String[]{"YOUTH", "SENIOR"}) {
             mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
@@ -105,6 +105,9 @@ class UserControllerTest extends IntegrationTestSupport {
             "{\"role\":null,\"ageGroup\":\"20S\"}",
             "{\"role\":\"ADMIN\",\"ageGroup\":\"20S\"}",
             "{\"role\":\"YOUTH\",\"ageGroup\":\"10S\"}",
+            // 이전 값(70대 이상)은 더 이상 받지 않는다. 70대는 70S, 80대 이상은 80S_PLUS로 나뉜다.
+            "{\"role\":\"SENIOR\",\"ageGroup\":\"70S_PLUS\"}",
+            "{\"role\":\"SENIOR\",\"ageGroup\":\"80S\"}",
             "{\"role\":\"YOUTH\",\"ageGroup\":\"20\"}",
             "{\"role\":\"YOUTH\",\"ageGroup\":\"AGE_20S\"}",
             "{\"role\":\"YOUTH\",\"ageGroup\":20}",
diff --git a/src/test/java/com/ganzithon/wool/user/repository/UserRepositoryTest.java b/src/test/java/com/ganzithon/wool/user/repository/UserRepositoryTest.java
index c13f867..0dccd61 100644
--- a/src/test/java/com/ganzithon/wool/user/repository/UserRepositoryTest.java
+++ b/src/test/java/com/ganzithon/wool/user/repository/UserRepositoryTest.java
@@ -17,11 +17,11 @@ class UserRepositoryTest extends IntegrationTestSupport {
 
     @Test
     void 저장하고_다시_읽으면_나이대와_기본_제재단계가_유지된다() {
-        User saved = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "포근한 양 312"));
+        User saved = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_80S_PLUS, "포근한 양 312"));
 
         User found = userRepository.findById(saved.getId()).orElseThrow();
 
-        assertThat(found.getAgeGroup()).isEqualTo(AgeGroup.AGE_70S_PLUS);
+        assertThat(found.getAgeGroup()).isEqualTo(AgeGroup.AGE_80S_PLUS);
         assertThat(found.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
         assertThat(found.getCreatedAt()).isNotNull();
         assertThat(userRepository.existsByNickname("포근한 양 312")).isTrue();
diff --git a/src/test/java/com/ganzithon/wool/user/service/UserServiceTest.java b/src/test/java/com/ganzithon/wool/user/service/UserServiceTest.java
index dcec174..fca7637 100644
--- a/src/test/java/com/ganzithon/wool/user/service/UserServiceTest.java
+++ b/src/test/java/com/ganzithon/wool/user/service/UserServiceTest.java
@@ -43,10 +43,10 @@ class UserServiceTest {
         when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
         when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
 
-        User user = userService.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, null);
+        User user = userService.join(Role.SENIOR, AgeGroup.AGE_80S_PLUS, null);
 
         assertThat(user.getRole()).isEqualTo(Role.SENIOR);
-        assertThat(user.getAgeGroup()).isEqualTo(AgeGroup.AGE_70S_PLUS);
+        assertThat(user.getAgeGroup()).isEqualTo(AgeGroup.AGE_80S_PLUS);
         assertThat(user.getNickname()).isEqualTo("포근한 양 312");
         assertThat(user.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
         assertThat(user.getId()).isNotBlank();
diff --git a/src/main/resources/db/migration/V3__split_age_group_70s.sql b/src/main/resources/db/migration/V3__split_age_group_70s.sql
new file mode 100644
index 0000000..ce53667
--- /dev/null
+++ b/src/main/resources/db/migration/V3__split_age_group_70s.sql
@@ -0,0 +1,6 @@
+-- 나이대 "70대 이상"(70S_PLUS)을 "70대"(70S)와 "80대 이상"(80S_PLUS)으로 나눈다.
+-- 화면: 청년 20·30·40대 / 어르신 50·60·70·80대 이상. 역할별 제한은 서버에서 두지 않는다. (본인 인증이 없다)
+-- 기존 70S_PLUS 사용자는 80대 이상인지 알 수 없으므로 70S로 옮긴다. 새 값을 넣은 뒤 옛 값을 지운다.
+ALTER TABLE users MODIFY COLUMN age_group ENUM('20S', '30S', '40S', '50S', '60S', '70S', '70S_PLUS', '80S_PLUS') NOT NULL COMMENT '나이대';
+UPDATE users SET age_group = '70S' WHERE age_group = '70S_PLUS';
+ALTER TABLE users MODIFY COLUMN age_group ENUM('20S', '30S', '40S', '50S', '60S', '70S', '80S_PLUS') NOT NULL COMMENT '나이대';
```
