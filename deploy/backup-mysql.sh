#!/usr/bin/env bash
# MySQL 컨테이너의 덤프를 ~/wool/backups 에 날짜별로 저장하고 7일 지난 파일을 지운다.
# 사용: bash ~/wool/backup-mysql.sh   (cron 예: 매일 새벽 4시 → 0 4 * * * bash ~/wool/backup-mysql.sh)
# 덤프 파일에는 사용자 데이터가 들어 있으니 서버 밖으로 내보낼 때 주의한다.
set -euo pipefail

# 만드는 폴더와 파일을 만든 사용자만 읽을 수 있게 한다.
umask 077

cd ~/wool
set -a
. ./.env
set +a

mkdir -p backups
chmod 700 backups
FILE="backups/wool-$(date +%Y%m%d-%H%M%S).sql.gz"
# 고유한 임시 파일을 만든다. (cron과 수동 실행이 겹쳐도 같은 임시 파일을 쓰지 않는다)
TMP="$(mktemp backups/wool-tmp.XXXXXX)"
# 실패하거나 중단돼도 불완전한 파일이 정상 백업처럼 남지 않게, 임시 파일에 받은 뒤 성공했을 때만 이름을 바꾼다.
trap 'rm -f "$TMP"' EXIT

# 비밀번호는 명령 인자(-p...)가 아니라 환경변수(MYSQL_PWD)로 넘겨서 프로세스 목록에 보이지 않게 한다.
docker compose -f compose.prod.yml exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysqldump -uroot --single-transaction --no-tablespaces "$DB_NAME" | gzip > "$TMP"
mv "$TMP" "$FILE"

find backups -name 'wool-*.sql.gz' -mtime +7 -delete
echo "백업 완료: $FILE"
