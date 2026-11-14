# 한 올 백엔드 배포 가이드 (AWS EC2 한 대 + Docker)

구성: `Caddy(HTTPS) → Spring(app) → MySQL`, 전부 EC2 한 대의 Docker 컨테이너다. 외부로는 80/443만 열린다.
`develop`을 `main`으로 머지하면 GitHub Actions(`.github/workflows/cd.yml`)가 이미지를 Docker Hub에 올리고, EC2의 러너가 새 이미지로 다시 띄운다.

## 1. 한 번만 하는 준비

### GitHub 저장소 시크릿 (Settings → Secrets and variables → Actions)
| 이름 | 내용 |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 아이디 |
| `DOCKERHUB_TOKEN` | Docker Hub 액세스 토큰(Read & Write). 비밀번호가 아니라 토큰을 쓴다 |

이미지는 `<DOCKERHUB_USERNAME>/wool_be`로 올라간다. **Docker Hub 저장소는 Public으로 둔다.** EC2의 배포는 Docker Hub에 로그인하지 않고 이미지를 받고(서버에 토큰을 남기지 않기 위해), 프론트도 로그인 없이 받는다. 이미지에는 비밀값이 들어가지 않는다(`.dockerignore`가 `.env` 등을 제외하고, 접속 정보는 서버 `.env`로만 주입한다). 비공개로 바꾸려면 `deploy.sh`에 `docker login`(pull 전용 토큰)을 추가해야 한다.

### AWS 콘솔
1. **EC2 인스턴스**: Ubuntu 24.04 LTS, 타입 `t3.micro`(RAM 1GB, 메모리가 부족하면 `t3.small`), 디스크 20GB gp3, 키 페어는 새로 만들어 `.pem`을 안전하게 보관한다.
2. **보안 그룹 인바운드**: 22(내 IP만), 80, 443(전체). **3306, 8080은 열지 않는다.**
3. **Elastic IP**를 할당해 인스턴스에 연결한다. IP가 바뀌면 HTTPS 주소(sslip.io)가 깨져서 프론트 설정도 다시 고쳐야 한다.

### 서버 세팅 (SSH 접속 후)
```bash
# 내 PC에서 deploy 폴더를 서버로 복사
scp -i 키.pem -r deploy ubuntu@<탄력적IP>:~/deploy

# 서버에서
bash ~/deploy/setup-ec2.sh      # 스왑 2GB + Docker 설치 + ~/wool 폴더 생성. 끝나면 로그아웃 후 재접속
cp ~/deploy/prod.env.example ~/wool/.env
chmod 600 ~/wool/.env            # 비밀번호와 API 키가 들어가므로 본인만 읽게 한다
cp ~/deploy/backup-mysql.sh ~/wool/backup-mysql.sh
nano ~/wool/.env                 # 값 채우기
```
`.env` 값:
- `BE_IMAGE`: `<도커허브아이디>/wool_be`. 배포 때는 `deploy.sh`가 같은 값을 넘기지만, `docker compose logs/exec/ps` 같은 수동 명령과 백업 스크립트도 이 값이 있어야 동작한다.
- `DOMAIN`: Elastic IP가 `13.124.27.143`이면 `13-124-27-143.sslip.io`.
- `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`: `openssl rand -hex 16`으로 만든 무작위 값.
- `GROQ_API_KEY`(필수), `UPSTAGE_API_KEY`(비워도 됨).
- `.env`와 `.pem`은 git이나 메신저에 올리지 않는다.
- 운영 중에 `DB_PASSWORD`나 `MYSQL_ROOT_PASSWORD`를 바꾸려면 `.env`만 고쳐서는 안 된다. MySQL 데이터 볼륨에 이미 만들어진 계정의 비밀번호는 자동으로 바뀌지 않으므로, MySQL 안에서 `ALTER USER`로 먼저 바꾼 뒤 `.env`를 고치고 앱을 다시 띄운다.

### GitHub Actions 러너 등록 (EC2에서)
저장소 Settings → Actions → Runners → **New self-hosted runner**(Linux x64) 화면의 안내 명령을 EC2에서 그대로 실행하되, 설정 단계에서 라벨에 `ec2-prod`를 추가한다(`./config.sh ... --labels ec2-prod`). 그 뒤 서비스로 등록해 재부팅해도 살아 있게 한다.
```bash
sudo ./svc.sh install && sudo ./svc.sh start
```
러너 사용자가 `docker`를 쓸 수 있어야 한다(`setup-ec2.sh`가 docker 그룹에 추가한다).

## 2. 배포
- `develop`을 `main`으로 머지(merge commit)하면 자동 배포된다. 수동으로 다시 올릴 일은 없다.
- 배포 순서(`deploy/deploy.sh`): Caddyfile 검증 → 새 앱 이미지만 받기 → mysql 확인 → app 교체 → caddy 교체. mysql은 이미지나 설정이 바뀔 때만 다시 만든다(데이터 보호).
- 새 앱이 healthy가 되지 못하면 배포 직전에 돌던 이미지와 설정 파일(`compose.prod.yml`, `Caddyfile`)로 되돌리고, 그래도 이번 배포는 실패로 표시된다(서버에 `*.prev` 백업이 남는다). MySQL 컨테이너는 되돌리지 않는다. Flyway가 이미 스키마를 바꿨다면 이미지만 되돌려서는 DB가 되돌려지지 않으니, 마이그레이션은 앞으로만 가는 것을 전제로 한다.
- 처음 배포는 되돌릴 이전 이미지가 없어서 실패하면 그대로 멈춘다. 이때는 `docker compose -f compose.prod.yml logs app`로 원인을 본다.
- 확인: `curl https://<DOMAIN>/api/health` → `{"status":200,"code":"COMMON_200_1",...}`, Swagger는 `https://<DOMAIN>/swagger-ui.html`.
- 서버 로그: `cd ~/wool && docker compose -f compose.prod.yml logs -f app`
- 첫 배포 때 Flyway가 V1, V2 스키마를 자동으로 만든다.

## 보안 메모
- 러너는 EC2의 Docker 권한을 가진 실행 환경이다. `main`에 머지되는 `compose.prod.yml`과 `deploy/deploy.sh`는 서버에서 그대로 실행되므로, `main` 보호 규칙(PR 필수, CI 통과 필수, 직접 푸시 금지)을 계속 유지한다. 러너는 이 저장소 전용으로만 등록한다.
- `.env`와 백업 파일은 본인만 읽게 한다(`chmod 600`, 백업 스크립트는 `umask 077`).

## 3. 백업과 비용
- 데이터는 MySQL 컨테이너의 볼륨에 있다. EC2나 디스크가 사라지면 같이 사라지므로 백업한다.
  ```bash
  bash ~/wool/backup-mysql.sh              # 수동
  crontab -e   # 0 4 * * * bash ~/wool/backup-mysql.sh   (매일 새벽 4시, 7일 보관)
  ```
  덤프(`~/wool/backups/`)를 내 PC로 받아 두면 더 안전하다. 사용자 데이터가 들어 있으니 공개 저장소에 올리지 않는다.
- 프론트가 연동하는 동안에는 EC2를 켜 둔다. 쓰지 않는 기간에만 중지(Stop)하고, 다시 시작해도 데이터는 남는다. Elastic IP를 연결해 두어야 같은 주소로 돌아온다.
- 메모리가 부족해 앱이 죽으면 인스턴스 타입을 `t3.small`로 올린다. 코드 변경은 필요 없다.
- t3.micro(RAM 1GB)에서 앱과 MySQL을 함께 돌리는 설정은 여유가 크지 않다(`-Xmx384m`은 JVM의 힙만 제한하고, JVM과 MySQL은 그 밖의 메모리도 쓴다). 첫 배포 뒤 `free -m`, `docker stats --no-stream`으로 사용량과 스왑을 확인하고, 스왑을 계속 많이 쓰면 `t3.small`로 올린다.

## 4. 프론트가 로컬에서 API만 띄울 때
`compose.local.yml`을 쓴다. Docker만 있으면 된다. 자세한 사용법은 파일 맨 위 주석을 본다.
```bash
BE_IMAGE=<도커허브아이디>/wool_be docker compose -f compose.local.yml pull
BE_IMAGE=<도커허브아이디>/wool_be docker compose -f compose.local.yml up -d
```
→ http://localhost:8080/swagger-ui.html
