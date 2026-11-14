너는 Spring Boot 백엔드와 Docker/GitHub Actions 배포를 리뷰하는 시니어 개발자다. 아래 PR을 독립적으로 검토해줘. 이 요청에 적힌 설명이 맞는지부터 의심하고, 파일을 직접 대조해서 판단해줘.

# 1. 프로젝트 전제
- 서비스 "한 올": 청년이 고민을 쓰면 어르신이 답장한다. Java 21, Spring Boot 4.1, JPA, Flyway(V1·V2 스키마), MySQL. 코드 스타일 지적은 필요 없다.
- 팀 브랜치 규칙: 기능마다 `develop`에서 따서 `develop`으로 PR. `main`은 배포용이고 `develop`에서만 머지한다(merge commit). 기존 CI(`ci.yml`)는 PR마다 `./gradlew build`를 돌린다.
- 배포 대상: AWS EC2 한 대(t3.micro, RAM 1GB 목표, 필요하면 t3.small)에 Docker로 `caddy(HTTPS) → app(Spring) → mysql`을 띄운다. 비용을 최대한 아끼려고 DB도 같은 EC2의 컨테이너다(RDS 안 씀). 닥터저지(다른 팀 프로젝트)의 배포 방식을 최대한 따랐다: `main` 푸시 → Docker Hub 이미지 푸시 → EC2의 self-hosted 러너(`ec2-prod`)가 그 커밋의 compose와 Caddyfile을 복사해 앱을 교체한다(`deploy/deploy.sh`, 실패하면 이전 이미지로 롤백).
- HTTPS는 도메인 없이 Elastic IP로 만든 `<IP>.sslip.io` 주소를 Caddy가 Let's Encrypt로 자동 발급한다. 주소는 저장소에 적지 않고 서버의 `.env`(`DOMAIN`)로 받는다.
- 코드가 `LocalDateTime.now()`로 시각을 저장하고 DB 접속은 `serverTimezone=Asia/Seoul`이라 JVM 시간대를 `Asia/Seoul`로 맞춰야 한다.
- 손편지 사진은 로컬 폴더(`FILE_STORAGE_DIR`)에 저장한다(S3 안 씀). 영구 볼륨이 필요하다.
- 시크릿(DB 비밀번호, GROQ/UPSTAGE 키, Docker Hub 토큰)은 저장소에 넣지 않는다.

# 2. 이 PR이 하는 일
- `Dockerfile`: 멀티스테이지(JDK 21로 `bootJar` 빌드 → `eclipse-temurin:21-jre`), 시간대 옵션, 테스트는 CI가 하므로 `-x test`. `.dockerignore` 추가.
- `compose.prod.yml`: caddy / app / mysql. 호스트 포트는 caddy의 80/443만. mysql은 메모리 튜닝(`innodb-buffer-pool-size=128M`, `performance-schema=OFF`, `max-connections=30`, utf8mb4), healthcheck(비밀번호는 `MYSQL_PWD` 환경변수), 앱은 `JAVA_TOOL_OPTIONS=-Xmx384m`, 업로드 볼륨 `/data/uploads`, `depends_on`의 `service_healthy`. 필수 값은 `:?`로 빠지면 즉시 실패시킨다. `BE_IMAGE`는 서버 `.env`에도 둔다(수동 compose 명령과 백업이 파일 전체를 읽기 때문). 배포 때는 `deploy.sh`가 `BE_IMAGE`와 `IMAGE_TAG`(커밋 SHA)를 환경변수로 넘겨 덮어쓴다.
- `Caddyfile`: `{$DOMAIN}` → `reverse_proxy app:8080`.
- `compose.local.yml`: 프론트가 로컬에서 API만 띄우는 용도. 더미 값만 있다. `BE_IMAGE`를 주면 Docker Hub 이미지를 쓰고, 없으면 `build: .`로 직접 빌드한다. `127.0.0.1:8080`만 노출한다.
- `.github/workflows/cd.yml`: `main` 푸시에 실행. `build-and-push`(Docker Hub, `latest`와 커밋 SHA 태그, 액션은 SHA 고정)와 `deploy`(self-hosted 러너에서 `deploy/deploy.sh` 실행). 시크릿 값이 든 잡 출력은 GitHub가 다음 잡에 넘기지 않아서, deploy에서 `BE_IMAGE`를 시크릿으로 직접 만든다.
- `deploy/deploy.sh`(1차 리뷰 반영으로 추가): Caddyfile을 같은 이미지로 `caddy validate` → 배포 직전에 돌던 앱 이미지를 기록 → compose·Caddyfile 복사 → 앱 이미지만 pull(mysql 이미지는 받지 않는다) → `up -d --wait mysql`(재생성 강제 안 함) → app을 `--force-recreate --no-deps --wait-timeout 240`으로 교체 → caddy 교체. 새 app이 healthy가 못 되면 앱 로그를 남기고 기록해 둔 이전 이미지와 배포 전에 `*.prev`로 남긴 이전 `compose.prod.yml`·`Caddyfile`로 되돌린 뒤(mysql은 되돌리지 않음), 이번 배포는 실패(종료 코드 1)로 끝낸다. 3차 리뷰 반영: (a) 새 설정을 복사한 뒤 앱 교체 전에(`pull app`, mysql 확인) 실패해도 `trap EXIT`로 설정 파일을 되돌린다. (b) 이전 이미지가 새 이미지와 같아도 롤백한다(`--force-recreate`로 컨테이너가 이미 사라졌고 이전 설정으로 다시 만들면 복구될 수 있어서). (c) 이번 배포에서 백업한 파일만 복구하고, 현재 파일이 없으면 오래된 `.prev`를 지운다. `if deploy_app` 안에서는 `set -e`가 꺼져서 함수 안에서 각 명령을 `|| return 1`로 처리했다(가짜 docker로 테스트하다 발견한 버그).
- `src/main/resources/application-prod.yml`: `DB_HOST` · `DB_NAME` · `DB_USERNAME` · `DB_PASSWORD`로 데이터소스를 조립(이전에는 데이터소스 설정이 없었다), `groq.api-key`는 그대로.
- 헬스 엔드포인트 `GET /api/health`(로그인 헤더 불필요, 기존 응답 래퍼 `COMMON_200_1`)와 테스트 `HealthControllerTest`.
- `deploy/`: `README.md`(EC2·러너·시크릿 준비, 롤백 설명, 보안 메모), `prod.env.example`(서버 `.env` 템플릿, `BE_IMAGE` 포함: docker compose 수동 명령과 백업이 compose 전체를 읽으므로 필요), `setup-ec2.sh`(스왑 2GB + Docker + docker 그룹 추가는 설치 여부와 별개로 항상 + `~/wool`), `deploy.sh`, `backup-mysql.sh`(`umask 077`, `mktemp`로 만든 고유 임시 파일에 받은 뒤 성공해야 이름 변경, 비밀번호는 `MYSQL_PWD` 환경변수, 7일 보관).
- 1차 리뷰에서 일부러 반영하지 않은 것: (a) 비공개 Docker Hub 지원 — 저장소를 Public으로 쓰는 것을 전제로 하고 README에 명시했다(EC2에 토큰을 남기지 않고, 프론트도 로그인 없이 받는다). (b) 헬스체크의 DB 확인 — 프로세스 생존 확인용으로 유지(최초 부팅은 Flyway가 DB 접속을 확인한다). (c) 비루트 사용자 실행 — 이번에는 보류(named volume 소유권 설정이 필요).
- 검증한 것: 전체 `./gradlew build` 통과. `docker compose config`로 두 compose 파일의 문법과 필수 변수 누락 시 실패를 확인. `bootJar` 후 `prod` 프로파일로 실행해 `DB_*` 플레이스홀더가 풀리고 Flyway가 DB 접속을 시도하는 것까지 확인(이 PC의 로컬 MySQL이 자격 증명을 거부해 거기서 멈춤).
- 가짜 `docker` 명령으로 `deploy.sh`의 흐름을 테스트했다(`pull app` 실패·mysql 확인 실패 시 설정 복구, 같은 이미지 재배포 실패 시 이전 설정으로 재생성, 현재 파일이 없고 오래된 `.prev`만 있는 경우, 롤백 때 이전 설정 파일이 복구되고 성공하면 새 설정이 남는 것, 기존 파일이 없는 처음 배포 포함): 정상 배포, 새 버전 실패 후 이전 이미지로 롤백(종료 코드 1), 처음 배포 실패, 롤백도 실패, 같은 이미지 재배포 실패, mysql은 재생성 대상이 아니고 앱 이미지만 pull함. `backup-mysql.sh`는 성공 시 파일 생성, mysqldump 실패 시 불완전한 파일이 남지 않음을 가짜 docker로 확인했다(파일 권한은 Windows에서는 확인할 수 없다).
- 검증하지 못한 것: 이 PC에는 Docker 엔진이 실행 중이 아니라서 이미지 빌드, `compose up`, `caddy validate`, `cd.yml` 실제 실행은 해보지 못했다. 워크플로 YAML은 눈으로만 확인했다.

# 3. 특히 봐줬으면 하는 곳
1. `cd.yml`: 시크릿 처리(잡 출력, 환경변수 노출), 액션 SHA 고정, `concurrency`, self-hosted 러너에서 `main` 푸시마다 실행되는 구조의 보안 위험(포크 PR에서는 실행되지 않으므로 push 트리거만 있는지), `deploy.sh`의 롤백 흐름(이전 이미지 기록, 실패 시 복구, 종료 코드)과 `set -e`가 꺼지는 경우가 더 있는지.
2. `compose.prod.yml`: 필수 변수 `:?` 문법, `depends_on` 헬스체크 조건, mysql healthcheck(`CMD-SHELL`과 `$$` 이스케이프), `JAVA_TOOL_OPTIONS`와 Dockerfile의 `-Duser.timezone`이 함께 적용되는지, 볼륨 마운트, 메모리 튜닝이 t3.micro에서 현실적인지.
3. `Dockerfile`: 시간대 옵션, 비루트 실행이 아닌 점(허용 가능한지), `COPY build/libs/*.jar`가 plain jar와 겹치지 않는지(`bootJar`만 실행), 이미지에 비밀값이 들어갈 여지(`.dockerignore`).
4. `application-prod.yml`: 플레이스홀더에 기본값이 없어서 환경변수가 빠지면 부팅이 실패하는 것이 의도(조용히 잘못 뜨는 것보다 낫다), `ddl-auto: none`과 Flyway 조합.
5. `backup-mysql.sh`: `. ./.env` 소싱, `MYSQL_PWD` 환경변수, 임시 파일과 `trap`, 권한.
6. `/api/health`가 인증 없이 열려 있고 DB를 확인하지 않는 설계가 컨테이너 헬스체크로 충분한지(DB가 죽어도 healthy로 보이는 점).

# 4. 알려진 한계 (지적하지 않아도 된다)
- 서버, 러너, 시크릿은 PR 머지 후 사람이 준비한다(`deploy/README.md`). `develop`에 머지해도 `cd.yml`은 `main` 푸시에만 실행된다.
- DB가 같은 EC2의 컨테이너라서 서버나 디스크가 사라지면 데이터가 같이 사라진다. 백업 스크립트로 완화한다.
- CORS는 `allowedOriginPatterns("*")`이고 프론트 주소가 정해지면 좁힌다(기존 TODO).
- 모니터링, 무중단 배포(blue/green), 이미지 취약점 스캔은 범위 밖이다.

# 5. 출력 형식
- 항목마다: 심각도(높음/중간/낮음), 파일과 줄, 구체적 시나리오, 고치는 방향
- 문제가 없으면 없다고만 쓰고, 억지로 지적을 만들지 마. 스타일 지적은 하지 마.
- 답변은 파일로 만들지 말고 채팅창에 바로 출력해줘. 내가 그대로 복사해 붙여넣을 수 있게 답변 전체를 마크다운으로 쓰고, 중간에 블록이 끊기거나 벗겨지지 않도록 답변 전체를 백틱 4개(````)로 감싼 코드 블록 하나에 담아줘. 그 안에서는 백틱 3개(```) 코드 블록을 쓰지 말고, 코드 예시는 들여쓰기나 인라인 코드로 써줘.

# 6. 변경 내용 (git diff, 새 파일은 전체가 + 로 표시됨)
```diff
diff --git a/src/main/resources/application-prod.yml b/src/main/resources/application-prod.yml
index 10c713f..e035504 100644
--- a/src/main/resources/application-prod.yml
+++ b/src/main/resources/application-prod.yml
@@ -1,4 +1,11 @@
+# 운영(EC2 + Docker) 설정. 비밀값과 접속 정보는 전부 환경변수(서버의 .env → compose)로 받는다.
+# DB_URL이 아니라 DB_HOST · DB_NAME이다. 아래에서 url을 직접 조립한다. (포트·시간대·인코딩 포함)
+# 값이 빠지면 Could not resolve placeholder 로 부팅이 실패한다. 필요한 키는 deploy/prod.env.example에 있다.
 spring:
+  datasource:
+    url: jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
+    username: ${DB_USERNAME}
+    password: ${DB_PASSWORD}
   jpa:
     hibernate:
       ddl-auto: none
diff --git a/.dockerignore b/.dockerignore
new file mode 100644
index 0000000..fc056f6
--- /dev/null
+++ b/.dockerignore
@@ -0,0 +1,13 @@
+build/
+.gradle/
+.idea/
+*.iml
+.git/
+.github/
+.env
+.env.*
+application-secret.yml
+application-secret.yaml
+uploads/
+review-*.md
+deploy/
diff --git a/.github/workflows/cd.yml b/.github/workflows/cd.yml
new file mode 100644
index 0000000..f1a96ad
--- /dev/null
+++ b/.github/workflows/cd.yml
@@ -0,0 +1,52 @@
+name: CD
+on:
+  push:
+    branches: [main]
+
+permissions:
+  contents: read
+
+# 겹치는 배포가 순서 뒤바뀌어 실행되어도 최신 배포를 덮어쓰지 않도록 직렬화한다.
+concurrency:
+  group: production-deploy
+  cancel-in-progress: false
+
+jobs:
+  build-and-push:
+    runs-on: ubuntu-latest
+    steps:
+      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0 — 시크릿 다루는 잡 앞단이라 SHA 고정
+        with:
+          persist-credentials: false
+      - name: Compute image name
+        id: image
+        run: echo "name=${{ secrets.DOCKERHUB_USERNAME }}/wool_be" >> "$GITHUB_OUTPUT"
+      - uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 # v3.7.0 — 시크릿 다루는 서드파티 액션이라 SHA 고정
+        with:
+          username: ${{ secrets.DOCKERHUB_USERNAME }}
+          password: ${{ secrets.DOCKERHUB_TOKEN }}
+      - uses: docker/build-push-action@ca052bb54ab0790a636c9b5f226502c73d547a25 # v5 — 로그인 이후 실행되고 자격증명에 접근 가능한 서드파티 액션이라 SHA 고정
+        with:
+          context: .
+          push: true
+          tags: |
+            ${{ steps.image.outputs.name }}:latest
+            ${{ steps.image.outputs.name }}:${{ github.sha }}
+
+  # 운영 EC2에 설치한 self-hosted 러너(라벨 ec2-prod)에서 실행한다. 러너가 없으면 이 잡은 대기 상태로 남는다.
+  # Docker Hub 저장소는 Public이어야 한다. (EC2의 pull은 로그인하지 않는다. deploy/README.md 참고)
+  deploy:
+    needs: build-and-push
+    runs-on: [self-hosted, ec2-prod]
+    steps:
+      # 서버에 예전부터 놓여 있던 파일이 아니라, 이 커밋 시점의 compose.prod.yml과 Caddyfile을 쓴다.
+      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0 — deploy job은 운영 서버 접근 권한이 있는 self-hosted 러너에서 실행되어 SHA 고정
+        with:
+          persist-credentials: false
+      - name: Deploy (새 앱 이미지로 교체하고 healthy 될 때까지 대기, 실패하면 이전 이미지로 롤백)
+        env:
+          # 시크릿 값이 들어간 잡 출력은 GitHub가 다음 잡으로 넘기지 않으므로, 여기서 시크릿으로 직접 만든다.
+          BE_IMAGE: ${{ secrets.DOCKERHUB_USERNAME }}/wool_be
+          IMAGE_TAG: ${{ github.sha }}
+          WORKSPACE: ${{ github.workspace }}
+        run: bash "$GITHUB_WORKSPACE/deploy/deploy.sh"
diff --git a/Caddyfile b/Caddyfile
new file mode 100644
index 0000000..d9056ca
--- /dev/null
+++ b/Caddyfile
@@ -0,0 +1,7 @@
+# HTTPS를 붙여 프론트(HTTPS)가 백엔드를 호출할 수 있게 한다.
+# 도메인을 사지 않으면 sslip.io(IP를 도메인으로 바꿔 주는 무료 서비스)를 쓴다. 예: 13-124-27-143.sslip.io → 13.124.27.143
+# Let's Encrypt가 정상 인증서를 발급하므로 Caddy가 자동으로 받아 온다. EC2의 IP가 바뀌면 이 주소도 바뀌므로 Elastic IP를 붙여 둔다.
+# 주소는 저장소에 적지 않고 서버의 .env에 DOMAIN으로 둔다. (compose가 Caddy 컨테이너에 환경변수로 넘긴다)
+{$DOMAIN} {
+    reverse_proxy app:8080
+}
diff --git a/Dockerfile b/Dockerfile
new file mode 100644
index 0000000..e68f6fc
--- /dev/null
+++ b/Dockerfile
@@ -0,0 +1,18 @@
+# 한 올 백엔드 이미지. 빌드와 실행을 나눈 멀티스테이지다. (빌드 도구는 최종 이미지에 남기지 않는다)
+FROM eclipse-temurin:21-jdk AS build
+WORKDIR /app
+COPY gradlew settings.gradle build.gradle ./
+COPY gradle ./gradle
+RUN chmod +x gradlew
+RUN ./gradlew --version
+COPY src ./src
+# 테스트는 CI(ci.yml)가 이미 돌린다.
+RUN ./gradlew bootJar --no-daemon -x test
+
+# 헬스체크가 curl을 쓰므로 curl이 들어 있는 Ubuntu 기반 JRE 이미지를 쓴다. (alpine에는 curl이 없다)
+FROM eclipse-temurin:21-jre
+WORKDIR /app
+COPY --from=build /app/build/libs/*.jar app.jar
+EXPOSE 8080
+# LocalDateTime.now()와 DB 시각을 한국 시간으로 맞춘다. (JAVA_TOOL_OPTIONS로 메모리 옵션을 더할 때도 이 옵션은 유지한다)
+ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
diff --git a/compose.local.yml b/compose.local.yml
new file mode 100644
index 0000000..519411e
--- /dev/null
+++ b/compose.local.yml
@@ -0,0 +1,57 @@
+# 프론트엔드 등 백엔드 코드를 직접 건드리지 않는 사람이 로컬에서 API 서버만 띄우는 파일.
+# Java/MySQL 설치 없이 Docker만 있으면 된다. 여기 값은 전부 로컬 전용 더미값이라 그대로 커밋해도 된다.
+#
+# 사용법(이미지를 받아서 쓸 때): BE_IMAGE에 이미지 이름(예: 도커허브아이디/wool_be)을 주면 CD가 올린 최신 이미지를 쓴다.
+#   BE_IMAGE=도커허브아이디/wool_be docker compose -f compose.local.yml pull
+#   BE_IMAGE=도커허브아이디/wool_be docker compose -f compose.local.yml up -d
+# BE_IMAGE 없이 저장소에서 직접 빌드할 때:
+#   docker compose -f compose.local.yml up -d --build
+#   → http://localhost:8080/swagger-ui.html , 상태 확인 http://localhost:8080/api/health
+#
+# 주의: 운영 배포용이 아니다. (그건 compose.prod.yml)
+# 주의: GROQ_API_KEY는 더미라 음성 인식은 동작하지 않고, UPSTAGE_API_KEY가 비어 있어 위기 표현은 키워드 목록으로만 판단한다.
+#       나머지 API는 정상 동작한다.
+services:
+  app:
+    image: ${BE_IMAGE:-wool_be:local}
+    build: .
+    restart: unless-stopped
+    ports:
+      - "127.0.0.1:8080:8080"
+    environment:
+      SPRING_PROFILES_ACTIVE: prod
+      DB_HOST: mysql
+      DB_NAME: wool
+      DB_USERNAME: wool
+      DB_PASSWORD: local_dev_password
+      GROQ_API_KEY: local-dev-dummy
+      FILE_STORAGE_DIR: /data/uploads
+    volumes:
+      - wool_local_uploads:/data/uploads
+    depends_on:
+      mysql:
+        condition: service_healthy
+
+  mysql:
+    image: mysql:8
+    restart: unless-stopped
+    command:
+      - --character-set-server=utf8mb4
+      - --collation-server=utf8mb4_unicode_ci
+    environment:
+      MYSQL_ROOT_PASSWORD: local_dev_root_password
+      MYSQL_DATABASE: wool
+      MYSQL_USER: wool
+      MYSQL_PASSWORD: local_dev_password
+      TZ: Asia/Seoul
+    volumes:
+      - wool_local_mysql:/var/lib/mysql
+    healthcheck:
+      test: ["CMD-SHELL", "MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysqladmin ping -uroot --silent"]
+      interval: 5s
+      timeout: 3s
+      retries: 20
+
+volumes:
+  wool_local_mysql:
+  wool_local_uploads:
diff --git a/compose.prod.yml b/compose.prod.yml
new file mode 100644
index 0000000..19c5618
--- /dev/null
+++ b/compose.prod.yml
@@ -0,0 +1,80 @@
+# 운영용: EC2 한 대에 caddy(HTTPS) → app(Spring) → mysql 을 함께 띄운다. 외부로 열리는 포트는 80/443 뿐이다.
+# 값은 서버의 ~/wool/.env 에서 읽는다. (deploy/prod.env.example 참고)
+# BE_IMAGE 는 .env 에도 있어야 한다. docker compose logs/exec/ps 같은 운영 명령도 이 파일 전체를 읽기 때문이다.
+# 배포(deploy.sh)는 BE_IMAGE · IMAGE_TAG 를 환경변수로 넘겨 .env 값을 덮어쓴다.
+services:
+  caddy:
+    image: caddy@sha256:5f5c8640aae01df9654968d946d8f1a56c497f1dd5c5cda4cf95ab7c14d58648 # 2-alpine
+    restart: always
+    ports:
+      - "80:80"
+      - "443:443"
+    environment:
+      DOMAIN: "${DOMAIN:?DOMAIN이 필요합니다. 예) 13-124-27-143.sslip.io}"
+    volumes:
+      - ./Caddyfile:/etc/caddy/Caddyfile:ro
+      - caddy_data:/data
+      - caddy_config:/config
+    depends_on:
+      app:
+        condition: service_healthy
+
+  app:
+    image: ${BE_IMAGE:?BE_IMAGE가 필요합니다}:${IMAGE_TAG:-latest}
+    restart: always
+    # 호스트 포트로 열지 않는다. Caddy를 거치지 않는 평문 HTTP 우회로가 생기면 HTTPS의 의미가 없다.
+    environment:
+      SPRING_PROFILES_ACTIVE: prod
+      # 시간대는 Dockerfile의 -Duser.timezone=Asia/Seoul 이 맞춘다. 여기서는 메모리만 제한한다. (EC2 t3.micro 1GB 기준)
+      JAVA_TOOL_OPTIONS: "-Xmx384m"
+      DB_HOST: mysql
+      DB_NAME: ${DB_NAME:-wool}
+      DB_USERNAME: ${DB_USERNAME:-wool}
+      DB_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD가 필요합니다}
+      GROQ_API_KEY: ${GROQ_API_KEY:?GROQ_API_KEY가 필요합니다}
+      # 비어 있으면 호출하지 않고 위기 키워드 목록으로만 판단한다.
+      UPSTAGE_API_KEY: ${UPSTAGE_API_KEY:-}
+      # 손편지 사진. 컨테이너를 다시 띄워도 남도록 볼륨에 둔다.
+      FILE_STORAGE_DIR: /data/uploads
+    volumes:
+      - uploads:/data/uploads
+    depends_on:
+      mysql:
+        condition: service_healthy
+    healthcheck:
+      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
+      interval: 10s
+      timeout: 3s
+      retries: 12
+      start_period: 60s
+
+  mysql:
+    image: mysql:8
+    restart: always
+    # 3306은 호스트로 열지 않는다. compose 내부 네트워크에서 app만 접근한다.
+    # t3.micro(RAM 1GB)에서 앱과 함께 돌리려고 메모리를 줄인다.
+    command:
+      - --character-set-server=utf8mb4
+      - --collation-server=utf8mb4_unicode_ci
+      - --innodb-buffer-pool-size=128M
+      - --performance-schema=OFF
+      - --max-connections=30
+    environment:
+      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD가 필요합니다}
+      MYSQL_DATABASE: ${DB_NAME:-wool}
+      MYSQL_USER: ${DB_USERNAME:-wool}
+      MYSQL_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD가 필요합니다}
+      TZ: Asia/Seoul
+    volumes:
+      - mysql_data:/var/lib/mysql
+    healthcheck:
+      test: ["CMD-SHELL", "MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysqladmin ping -uroot --silent"]
+      interval: 5s
+      timeout: 3s
+      retries: 20
+
+volumes:
+  mysql_data:
+  uploads:
+  caddy_data:
+  caddy_config:
diff --git a/deploy/README.md b/deploy/README.md
new file mode 100644
index 0000000..aaf86f4
--- /dev/null
+++ b/deploy/README.md
@@ -0,0 +1,78 @@
+# 한 올 백엔드 배포 가이드 (AWS EC2 한 대 + Docker)
+
+구성: `Caddy(HTTPS) → Spring(app) → MySQL`, 전부 EC2 한 대의 Docker 컨테이너다. 외부로는 80/443만 열린다.
+`develop`을 `main`으로 머지하면 GitHub Actions(`.github/workflows/cd.yml`)가 이미지를 Docker Hub에 올리고, EC2의 러너가 새 이미지로 다시 띄운다.
+
+## 1. 한 번만 하는 준비
+
+### GitHub 저장소 시크릿 (Settings → Secrets and variables → Actions)
+| 이름 | 내용 |
+|---|---|
+| `DOCKERHUB_USERNAME` | Docker Hub 아이디 |
+| `DOCKERHUB_TOKEN` | Docker Hub 액세스 토큰(Read & Write). 비밀번호가 아니라 토큰을 쓴다 |
+
+이미지는 `<DOCKERHUB_USERNAME>/wool_be`로 올라간다. **Docker Hub 저장소는 Public으로 둔다.** EC2의 배포는 Docker Hub에 로그인하지 않고 이미지를 받고(서버에 토큰을 남기지 않기 위해), 프론트도 로그인 없이 받는다. 이미지에는 비밀값이 들어가지 않는다(`.dockerignore`가 `.env` 등을 제외하고, 접속 정보는 서버 `.env`로만 주입한다). 비공개로 바꾸려면 `deploy.sh`에 `docker login`(pull 전용 토큰)을 추가해야 한다.
+
+### AWS 콘솔
+1. **EC2 인스턴스**: Ubuntu 24.04 LTS, 타입 `t3.micro`(RAM 1GB, 메모리가 부족하면 `t3.small`), 디스크 20GB gp3, 키 페어는 새로 만들어 `.pem`을 안전하게 보관한다.
+2. **보안 그룹 인바운드**: 22(내 IP만), 80, 443(전체). **3306, 8080은 열지 않는다.**
+3. **Elastic IP**를 할당해 인스턴스에 연결한다. IP가 바뀌면 HTTPS 주소(sslip.io)가 깨져서 프론트 설정도 다시 고쳐야 한다.
+
+### 서버 세팅 (SSH 접속 후)
+```bash
+# 내 PC에서 deploy 폴더를 서버로 복사
+scp -i 키.pem -r deploy ubuntu@<탄력적IP>:~/deploy
+
+# 서버에서
+bash ~/deploy/setup-ec2.sh      # 스왑 2GB + Docker 설치 + ~/wool 폴더 생성. 끝나면 로그아웃 후 재접속
+cp ~/deploy/prod.env.example ~/wool/.env
+chmod 600 ~/wool/.env            # 비밀번호와 API 키가 들어가므로 본인만 읽게 한다
+cp ~/deploy/backup-mysql.sh ~/wool/backup-mysql.sh
+nano ~/wool/.env                 # 값 채우기
+```
+`.env` 값:
+- `BE_IMAGE`: `<도커허브아이디>/wool_be`. 배포 때는 `deploy.sh`가 같은 값을 넘기지만, `docker compose logs/exec/ps` 같은 수동 명령과 백업 스크립트도 이 값이 있어야 동작한다.
+- `DOMAIN`: Elastic IP가 `13.124.27.143`이면 `13-124-27-143.sslip.io`.
+- `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`: `openssl rand -hex 16`으로 만든 무작위 값.
+- `GROQ_API_KEY`(필수), `UPSTAGE_API_KEY`(비워도 됨).
+- `.env`와 `.pem`은 git이나 메신저에 올리지 않는다.
+- 운영 중에 `DB_PASSWORD`나 `MYSQL_ROOT_PASSWORD`를 바꾸려면 `.env`만 고쳐서는 안 된다. MySQL 데이터 볼륨에 이미 만들어진 계정의 비밀번호는 자동으로 바뀌지 않으므로, MySQL 안에서 `ALTER USER`로 먼저 바꾼 뒤 `.env`를 고치고 앱을 다시 띄운다.
+
+### GitHub Actions 러너 등록 (EC2에서)
+저장소 Settings → Actions → Runners → **New self-hosted runner**(Linux x64) 화면의 안내 명령을 EC2에서 그대로 실행하되, 설정 단계에서 라벨에 `ec2-prod`를 추가한다(`./config.sh ... --labels ec2-prod`). 그 뒤 서비스로 등록해 재부팅해도 살아 있게 한다.
+```bash
+sudo ./svc.sh install && sudo ./svc.sh start
+```
+러너 사용자가 `docker`를 쓸 수 있어야 한다(`setup-ec2.sh`가 docker 그룹에 추가한다).
+
+## 2. 배포
+- `develop`을 `main`으로 머지(merge commit)하면 자동 배포된다. 수동으로 다시 올릴 일은 없다.
+- 배포 순서(`deploy/deploy.sh`): Caddyfile 검증 → 새 앱 이미지만 받기 → mysql 확인 → app 교체 → caddy 교체. mysql은 이미지나 설정이 바뀔 때만 다시 만든다(데이터 보호).
+- 새 앱이 healthy가 되지 못하면 배포 직전에 돌던 이미지와 설정 파일(`compose.prod.yml`, `Caddyfile`)로 되돌리고, 그래도 이번 배포는 실패로 표시된다(서버에 `*.prev` 백업이 남는다). MySQL 컨테이너는 되돌리지 않는다. Flyway가 이미 스키마를 바꿨다면 이미지만 되돌려서는 DB가 되돌려지지 않으니, 마이그레이션은 앞으로만 가는 것을 전제로 한다.
+- 처음 배포는 되돌릴 이전 이미지가 없어서 실패하면 그대로 멈춘다. 이때는 `docker compose -f compose.prod.yml logs app`로 원인을 본다.
+- 확인: `curl https://<DOMAIN>/api/health` → `{"status":200,"code":"COMMON_200_1",...}`, Swagger는 `https://<DOMAIN>/swagger-ui.html`.
+- 서버 로그: `cd ~/wool && docker compose -f compose.prod.yml logs -f app`
+- 첫 배포 때 Flyway가 V1, V2 스키마를 자동으로 만든다.
+
+## 보안 메모
+- 러너는 EC2의 Docker 권한을 가진 실행 환경이다. `main`에 머지되는 `compose.prod.yml`과 `deploy/deploy.sh`는 서버에서 그대로 실행되므로, `main` 보호 규칙(PR 필수, CI 통과 필수, 직접 푸시 금지)을 계속 유지한다. 러너는 이 저장소 전용으로만 등록한다.
+- `.env`와 백업 파일은 본인만 읽게 한다(`chmod 600`, 백업 스크립트는 `umask 077`).
+
+## 3. 백업과 비용
+- 데이터는 MySQL 컨테이너의 볼륨에 있다. EC2나 디스크가 사라지면 같이 사라지므로 백업한다.
+  ```bash
+  bash ~/wool/backup-mysql.sh              # 수동
+  crontab -e   # 0 4 * * * bash ~/wool/backup-mysql.sh   (매일 새벽 4시, 7일 보관)
+  ```
+  덤프(`~/wool/backups/`)를 내 PC로 받아 두면 더 안전하다. 사용자 데이터가 들어 있으니 공개 저장소에 올리지 않는다.
+- 프론트가 연동하는 동안에는 EC2를 켜 둔다. 쓰지 않는 기간에만 중지(Stop)하고, 다시 시작해도 데이터는 남는다. Elastic IP를 연결해 두어야 같은 주소로 돌아온다.
+- 메모리가 부족해 앱이 죽으면 인스턴스 타입을 `t3.small`로 올린다. 코드 변경은 필요 없다.
+- t3.micro(RAM 1GB)에서 앱과 MySQL을 함께 돌리는 설정은 여유가 크지 않다(`-Xmx384m`은 JVM의 힙만 제한하고, JVM과 MySQL은 그 밖의 메모리도 쓴다). 첫 배포 뒤 `free -m`, `docker stats --no-stream`으로 사용량과 스왑을 확인하고, 스왑을 계속 많이 쓰면 `t3.small`로 올린다.
+
+## 4. 프론트가 로컬에서 API만 띄울 때
+`compose.local.yml`을 쓴다. Docker만 있으면 된다. 자세한 사용법은 파일 맨 위 주석을 본다.
+```bash
+BE_IMAGE=<도커허브아이디>/wool_be docker compose -f compose.local.yml pull
+BE_IMAGE=<도커허브아이디>/wool_be docker compose -f compose.local.yml up -d
+```
+→ http://localhost:8080/swagger-ui.html
diff --git a/deploy/backup-mysql.sh b/deploy/backup-mysql.sh
new file mode 100644
index 0000000..cc370c5
--- /dev/null
+++ b/deploy/backup-mysql.sh
@@ -0,0 +1,29 @@
+#!/usr/bin/env bash
+# MySQL 컨테이너의 덤프를 ~/wool/backups 에 날짜별로 저장하고 7일 지난 파일을 지운다.
+# 사용: bash ~/wool/backup-mysql.sh   (cron 예: 매일 새벽 4시 → 0 4 * * * bash ~/wool/backup-mysql.sh)
+# 덤프 파일에는 사용자 데이터가 들어 있으니 서버 밖으로 내보낼 때 주의한다.
+set -euo pipefail
+
+# 만드는 폴더와 파일을 만든 사용자만 읽을 수 있게 한다.
+umask 077
+
+cd ~/wool
+set -a
+. ./.env
+set +a
+
+mkdir -p backups
+chmod 700 backups
+FILE="backups/wool-$(date +%Y%m%d-%H%M%S).sql.gz"
+# 고유한 임시 파일을 만든다. (cron과 수동 실행이 겹쳐도 같은 임시 파일을 쓰지 않는다)
+TMP="$(mktemp backups/wool-tmp.XXXXXX)"
+# 실패하거나 중단돼도 불완전한 파일이 정상 백업처럼 남지 않게, 임시 파일에 받은 뒤 성공했을 때만 이름을 바꾼다.
+trap 'rm -f "$TMP"' EXIT
+
+# 비밀번호는 명령 인자(-p...)가 아니라 환경변수(MYSQL_PWD)로 넘겨서 프로세스 목록에 보이지 않게 한다.
+docker compose -f compose.prod.yml exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
+  mysqldump -uroot --single-transaction --no-tablespaces "$DB_NAME" | gzip > "$TMP"
+mv "$TMP" "$FILE"
+
+find backups -name 'wool-*.sql.gz' -mtime +7 -delete
+echo "백업 완료: $FILE"
diff --git a/deploy/deploy.sh b/deploy/deploy.sh
new file mode 100644
index 0000000..3a12983
--- /dev/null
+++ b/deploy/deploy.sh
@@ -0,0 +1,96 @@
+#!/usr/bin/env bash
+# CD(cd.yml)가 운영 서버(EC2)의 러너에서 실행하는 배포 스크립트.
+# 필요한 환경변수: BE_IMAGE(예: 도커허브아이디/wool_be), IMAGE_TAG(커밋 SHA), WORKSPACE(체크아웃한 저장소 경로)
+#
+# 동작: Caddyfile 검증 → 새 compose 파일 복사 → 새 앱 이미지 받기 → mysql 확인 → app 교체 → caddy 교체.
+#  - mysql은 이미지가 바뀌거나 설정이 바뀔 때만 다시 만들고, 매 배포마다 재생성하지 않는다. (데이터 보호)
+#  - 새 설정 파일을 복사한 뒤 앱 교체에 들어가기 전에 실패하면(예: pull app, mysql 확인) 설정 파일을 이전 상태로 되돌린다.
+#  - 새 app이 healthy가 되지 못하면 배포 직전에 돌던 이미지와 설정 파일(compose.prod.yml, Caddyfile)로 되돌린다. (같은 이미지 재배포도 포함) 되돌린 뒤에도 이 스크립트는 실패(종료 코드 1)로 끝난다.
+#    (mysql은 되돌리지 않는다. 새 compose가 mysql 설정을 바꿔서 이미 다시 만들어졌다면 그대로 남는다.)
+#  - Flyway가 새 버전에서 이미 DB 스키마를 바꿨다면 이미지만 되돌려서는 DB가 되돌려지지 않는다. (마이그레이션은 앞으로만 간다)
+set -euo pipefail
+
+: "${BE_IMAGE:?BE_IMAGE가 필요합니다}"
+: "${IMAGE_TAG:?IMAGE_TAG가 필요합니다}"
+: "${WORKSPACE:?WORKSPACE가 필요합니다}"
+
+cd ~/wool
+COMPOSE=(docker compose -f compose.prod.yml)
+
+# 문법 오류가 있는 Caddyfile을 운영에 복사하면 caddy 재생성이 실패해서 HTTPS 프록시가 죽는다. 복사 전에 같은 이미지로 미리 검증한다.
+docker run --rm -e DOMAIN=validate.example.com \
+  -v "$WORKSPACE/Caddyfile:/etc/caddy/Caddyfile:ro" \
+  caddy@sha256:5f5c8640aae01df9654968d946d8f1a56c497f1dd5c5cda4cf95ab7c14d58648 \
+  caddy validate --config /etc/caddy/Caddyfile
+
+# 배포 직전에 돌고 있던 앱 이미지를 기록한다. 처음 배포라면 비어 있다.
+previous=""
+current_id="$("${COMPOSE[@]}" ps -q app 2>/dev/null || true)"
+if [ -n "$current_id" ]; then
+  previous="$(docker inspect -f '{{.Config.Image}}' "$current_id" 2>/dev/null || true)"
+fi
+
+# 새 파일로 덮어쓰기 전에 지금 쓰는 설정 파일을 남겨 둔다. 앱 교체 전에 실패하거나 롤백할 때 이 파일로 되돌린다.
+# 이번 배포에서 백업한 파일만 복구하도록 파일별로 기록한다. (현재 파일이 없으면 예전 .prev가 남아 잘못 복구되지 않게 지운다)
+backed_up_compose=0
+backed_up_caddy=0
+if [ -f compose.prod.yml ]; then cp compose.prod.yml compose.prod.yml.prev; backed_up_compose=1; else rm -f compose.prod.yml.prev; fi
+if [ -f Caddyfile ]; then cp Caddyfile Caddyfile.prev; backed_up_caddy=1; else rm -f Caddyfile.prev; fi
+
+restore_configs() {
+  [ "$backed_up_compose" = 1 ] && cp compose.prod.yml.prev compose.prod.yml
+  [ "$backed_up_caddy" = 1 ] && cp Caddyfile.prev Caddyfile
+  return 0
+}
+
+# 새 파일을 복사한 뒤 앱 교체(deploy_app)에 들어가기 전에 스크립트가 어떤 이유로든 실패하면 설정 파일을 되돌린다.
+# (pull app, mysql 확인이 실패하면 set -e 로 바로 종료되는데, 그때 새 설정 파일이 서버에 남으면 다음 배포의 백업이 잘못된다)
+config_swapped=0
+on_exit() {
+  rc=$?
+  if [ "$rc" -ne 0 ] && [ "$config_swapped" = 1 ]; then
+    restore_configs
+    echo "::error::앱 교체 전에 실패해서 설정 파일을 이전 상태로 되돌렸습니다."
+  fi
+}
+trap on_exit EXIT
+
+cp "$WORKSPACE/compose.prod.yml" compose.prod.yml
+cp "$WORKSPACE/Caddyfile" Caddyfile
+config_swapped=1
+
+# 앱 이미지만 받는다. (mysql 이미지를 같이 받으면 태그가 가리키는 새 버전으로 DB 컨테이너가 바뀔 수 있다)
+"${COMPOSE[@]}" pull app
+"${COMPOSE[@]}" up -d --wait --wait-timeout 120 mysql
+
+# 앱 헬스체크는 start_period 60초 + 10초 간격 12회라서 최대 약 180초 걸린다. 그보다 길게 기다린다.
+# 주의: 이 함수는 `if deploy_app ...` 안에서 불려서 set -e 가 꺼진다. 그래서 각 명령이 실패하면 || return 1 로 직접 돌려보낸다.
+deploy_app() {
+  BE_IMAGE="$1" IMAGE_TAG="$2" "${COMPOSE[@]}" up -d --wait --wait-timeout 240 --force-recreate --no-deps app || return 1
+  BE_IMAGE="$1" IMAGE_TAG="$2" "${COMPOSE[@]}" up -d --wait --wait-timeout 60 --force-recreate --no-deps caddy || return 1
+}
+
+# 여기서부터는 아래 롤백 절차가 설정 파일 복구를 맡는다. (on_exit 가 한 번 더 복구하지 않게 끈다)
+config_swapped=0
+if deploy_app "$BE_IMAGE" "$IMAGE_TAG"; then
+  echo "배포 완료: $BE_IMAGE:$IMAGE_TAG"
+  exit 0
+fi
+
+echo "::error::새 버전이 healthy가 되지 못했습니다. 앱 로그 마지막 100줄:"
+"${COMPOSE[@]}" logs --tail 100 app || true
+
+# 이전 이미지가 있으면 롤백한다. 새 이미지와 같은 이미지여도 한다. (--force-recreate 로 기존 컨테이너가 이미 사라졌고,
+# 이전 설정 파일로 다시 만들면 설정 때문에 실패한 경우에는 복구될 수 있다)
+if [ -n "$previous" ]; then
+  echo "이전 이미지($previous)와 설정 파일로 되돌립니다."
+  restore_configs
+  if deploy_app "${previous%:*}" "${previous##*:}"; then
+    echo "::error::롤백 완료. 서비스는 이전 버전($previous)으로 돌아왔지만 이번 배포는 실패입니다."
+  else
+    echo "::error::롤백도 실패했습니다. 서버에서 직접 확인하세요: cd ~/wool && docker compose -f compose.prod.yml ps"
+  fi
+else
+  echo "::error::되돌릴 이전 이미지가 없습니다. (처음 배포)"
+fi
+exit 1
diff --git a/deploy/prod.env.example b/deploy/prod.env.example
new file mode 100644
index 0000000..36f7fd2
--- /dev/null
+++ b/deploy/prod.env.example
@@ -0,0 +1,19 @@
+# 운영 서버(EC2)의 ~/wool/.env 템플릿. 이 파일을 서버에서 .env 로 복사해 값을 채운다. 저장소에는 진짜 값을 넣지 않는다.
+# compose.prod.yml 이 이 파일을 읽는다. (IMAGE_TAG 는 deploy.sh 가 커밋 SHA로 넘기므로 적지 않는다)
+
+# Docker Hub 이미지 이름: <도커허브아이디>/wool_be (cd.yml 이 올리는 이름과 같아야 한다)
+# 배포 때는 deploy.sh 가 같은 값을 환경변수로 넘기지만, docker compose logs/exec 같은 수동 명령에도 필요해서 여기에도 둔다.
+BE_IMAGE=
+
+# Caddy가 인증서를 받을 주소. 도메인이 없으면 Elastic IP로 만든 sslip.io 주소를 쓴다. (예: 13-124-27-143.sslip.io)
+DOMAIN=
+
+# MySQL (컨테이너). 비밀번호는 `openssl rand -hex 16` 처럼 무작위로 만든다.
+DB_NAME=wool
+DB_USERNAME=wool
+DB_PASSWORD=
+MYSQL_ROOT_PASSWORD=
+
+# 외부 API. GROQ_API_KEY는 필수(음성 인식), UPSTAGE_API_KEY는 비워 두면 위기 표현을 키워드 목록으로만 판단한다.
+GROQ_API_KEY=
+UPSTAGE_API_KEY=
diff --git a/deploy/setup-ec2.sh b/deploy/setup-ec2.sh
new file mode 100644
index 0000000..80a69ed
--- /dev/null
+++ b/deploy/setup-ec2.sh
@@ -0,0 +1,24 @@
+#!/usr/bin/env bash
+# Ubuntu 22.04/24.04 EC2 최초 1회 세팅: 스왑 2GB + Docker 설치 + 배포 폴더(~/wool) 만들기.
+# 사용: bash setup-ec2.sh   (실행 후 로그아웃/재접속해야 docker 그룹이 적용된다)
+set -euo pipefail
+
+# 스왑: 램 1GB(t3.micro)에서 Spring과 MySQL을 함께 돌릴 때 메모리 부족(OOM)을 막는다.
+if ! swapon --show | grep -q /swapfile; then
+  sudo fallocate -l 2G /swapfile
+  sudo chmod 600 /swapfile
+  sudo mkswap /swapfile
+  sudo swapon /swapfile
+  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
+fi
+
+# Docker (공식 설치 스크립트)
+if ! command -v docker >/dev/null; then
+  curl -fsSL https://get.docker.com | sudo sh
+fi
+# 이미 Docker가 설치돼 있어도 이 사용자가 docker 그룹에 없을 수 있어서, 설치와 별개로 항상 추가한다. (러너가 docker 명령을 쓰려면 필요)
+getent group docker >/dev/null || sudo groupadd docker
+sudo usermod -aG docker "$USER"
+
+mkdir -p ~/wool ~/wool/backups
+echo "완료. 재접속 후 'docker compose version' 으로 확인하세요."
diff --git a/src/main/java/com/ganzithon/wool/global/controller/HealthController.java b/src/main/java/com/ganzithon/wool/global/controller/HealthController.java
new file mode 100644
index 0000000..dcb8eeb
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/global/controller/HealthController.java
@@ -0,0 +1,17 @@
+package com.ganzithon.wool.global.controller;
+
+import com.ganzithon.wool.global.response.ApiResponse;
+import com.ganzithon.wool.global.response.CommonResponseCode;
+import org.springframework.http.ResponseEntity;
+import org.springframework.web.bind.annotation.GetMapping;
+import org.springframework.web.bind.annotation.RestController;
+
+// 컨테이너 헬스체크용. 로그인 헤더 없이 부른다. DB 등 외부 연결은 확인하지 않고, 앱이 요청을 받을 수 있는지만 알려준다.
+@RestController
+public class HealthController {
+
+    @GetMapping("/api/health")
+    public ResponseEntity<ApiResponse<Void>> health() {
+        return ApiResponse.of(CommonResponseCode.OK).toResponseEntity();
+    }
+}
diff --git a/src/main/java/com/ganzithon/wool/global/response/CommonResponseCode.java b/src/main/java/com/ganzithon/wool/global/response/CommonResponseCode.java
new file mode 100644
index 0000000..9592104
--- /dev/null
+++ b/src/main/java/com/ganzithon/wool/global/response/CommonResponseCode.java
@@ -0,0 +1,16 @@
+package com.ganzithon.wool.global.response;
+
+import lombok.Getter;
+import lombok.RequiredArgsConstructor;
+import org.springframework.http.HttpStatus;
+
+@Getter
+@RequiredArgsConstructor
+public enum CommonResponseCode implements ResponseCode {
+
+    OK(HttpStatus.OK, "COMMON_200_1", "정상입니다.");
+
+    private final HttpStatus httpStatus;
+    private final String code;
+    private final String message;
+}
diff --git a/src/test/java/com/ganzithon/wool/global/controller/HealthControllerTest.java b/src/test/java/com/ganzithon/wool/global/controller/HealthControllerTest.java
new file mode 100644
index 0000000..20592d5
--- /dev/null
+++ b/src/test/java/com/ganzithon/wool/global/controller/HealthControllerTest.java
@@ -0,0 +1,25 @@
+package com.ganzithon.wool.global.controller;
+
+import com.ganzithon.wool.IntegrationTestSupport;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
+import org.springframework.test.web.servlet.MockMvc;
+
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+@AutoConfigureMockMvc
+class HealthControllerTest extends IntegrationTestSupport {
+
+    @Autowired private MockMvc mockMvc;
+
+    @Test
+    void 로그인_헤더_없이_200과_래퍼로_응답한다() throws Exception {
+        mockMvc.perform(get("/api/health"))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$.status").value(200))
+                .andExpect(jsonPath("$.code").value("COMMON_200_1"));
+    }
+}
```
