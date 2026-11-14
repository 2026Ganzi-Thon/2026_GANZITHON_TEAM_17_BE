#!/usr/bin/env bash
# CD(cd.yml)가 운영 서버(EC2)의 러너에서 실행하는 배포 스크립트.
# 필요한 환경변수: BE_IMAGE(예: 도커허브아이디/wool_be), IMAGE_TAG(커밋 SHA), WORKSPACE(체크아웃한 저장소 경로)
#
# 동작: Caddyfile 검증 → 새 compose 파일 복사 → 새 앱 이미지 받기 → mysql 확인 → app 교체 → caddy 교체.
#  - mysql은 이미지가 바뀌거나 설정이 바뀔 때만 다시 만들고, 매 배포마다 재생성하지 않는다. (데이터 보호)
#  - 새 설정 파일을 복사한 뒤 앱 교체에 들어가기 전에 실패하면(예: pull app, mysql 확인) 설정 파일을 이전 상태로 되돌린다.
#  - 새 app이 healthy가 되지 못하면 배포 직전에 돌던 이미지와 설정 파일(compose.prod.yml, Caddyfile)로 되돌린다. (같은 이미지 재배포도 포함) 되돌린 뒤에도 이 스크립트는 실패(종료 코드 1)로 끝난다.
#    (mysql은 되돌리지 않는다. 새 compose가 mysql 설정을 바꿔서 이미 다시 만들어졌다면 그대로 남는다.)
#  - Flyway가 새 버전에서 이미 DB 스키마를 바꿨다면 이미지만 되돌려서는 DB가 되돌려지지 않는다. (마이그레이션은 앞으로만 간다)
set -euo pipefail

: "${BE_IMAGE:?BE_IMAGE가 필요합니다}"
: "${IMAGE_TAG:?IMAGE_TAG가 필요합니다}"
: "${WORKSPACE:?WORKSPACE가 필요합니다}"

cd ~/wool
COMPOSE=(docker compose -f compose.prod.yml)

# 문법 오류가 있는 Caddyfile을 운영에 복사하면 caddy 재생성이 실패해서 HTTPS 프록시가 죽는다. 복사 전에 같은 이미지로 미리 검증한다.
docker run --rm -e DOMAIN=validate.example.com \
  -v "$WORKSPACE/Caddyfile:/etc/caddy/Caddyfile:ro" \
  caddy@sha256:5f5c8640aae01df9654968d946d8f1a56c497f1dd5c5cda4cf95ab7c14d58648 \
  caddy validate --config /etc/caddy/Caddyfile

# 배포 직전에 돌고 있던 앱 이미지를 기록한다. 처음 배포라면 비어 있다.
previous=""
current_id="$("${COMPOSE[@]}" ps -q app 2>/dev/null || true)"
if [ -n "$current_id" ]; then
  previous="$(docker inspect -f '{{.Config.Image}}' "$current_id" 2>/dev/null || true)"
fi

# 새 파일로 덮어쓰기 전에 지금 쓰는 설정 파일을 남겨 둔다. 앱 교체 전에 실패하거나 롤백할 때 이 파일로 되돌린다.
# 이번 배포에서 백업한 파일만 복구하도록 파일별로 기록한다. (현재 파일이 없으면 예전 .prev가 남아 잘못 복구되지 않게 지운다)
backed_up_compose=0
backed_up_caddy=0
if [ -f compose.prod.yml ]; then cp compose.prod.yml compose.prod.yml.prev; backed_up_compose=1; else rm -f compose.prod.yml.prev; fi
if [ -f Caddyfile ]; then cp Caddyfile Caddyfile.prev; backed_up_caddy=1; else rm -f Caddyfile.prev; fi

restore_configs() {
  [ "$backed_up_compose" = 1 ] && cp compose.prod.yml.prev compose.prod.yml
  [ "$backed_up_caddy" = 1 ] && cp Caddyfile.prev Caddyfile
  return 0
}

# 새 파일을 복사한 뒤 앱 교체(deploy_app)에 들어가기 전에 스크립트가 어떤 이유로든 실패하면 설정 파일을 되돌린다.
# (pull app, mysql 확인이 실패하면 set -e 로 바로 종료되는데, 그때 새 설정 파일이 서버에 남으면 다음 배포의 백업이 잘못된다)
config_swapped=0
on_exit() {
  rc=$?
  if [ "$rc" -ne 0 ] && [ "$config_swapped" = 1 ]; then
    restore_configs
    echo "::error::앱 교체 전에 실패해서 설정 파일을 이전 상태로 되돌렸습니다."
  fi
}
trap on_exit EXIT

cp "$WORKSPACE/compose.prod.yml" compose.prod.yml
cp "$WORKSPACE/Caddyfile" Caddyfile
config_swapped=1

# 앱 이미지만 받는다. (mysql 이미지를 같이 받으면 태그가 가리키는 새 버전으로 DB 컨테이너가 바뀔 수 있다)
"${COMPOSE[@]}" pull app
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 mysql

# 앱 헬스체크는 start_period 60초 + 10초 간격 12회라서 최대 약 180초 걸린다. 그보다 길게 기다린다.
# 주의: 이 함수는 `if deploy_app ...` 안에서 불려서 set -e 가 꺼진다. 그래서 각 명령이 실패하면 || return 1 로 직접 돌려보낸다.
deploy_app() {
  BE_IMAGE="$1" IMAGE_TAG="$2" "${COMPOSE[@]}" up -d --wait --wait-timeout 240 --force-recreate --no-deps app || return 1
  BE_IMAGE="$1" IMAGE_TAG="$2" "${COMPOSE[@]}" up -d --wait --wait-timeout 60 --force-recreate --no-deps caddy || return 1
}

# 여기서부터는 아래 롤백 절차가 설정 파일 복구를 맡는다. (on_exit 가 한 번 더 복구하지 않게 끈다)
config_swapped=0
if deploy_app "$BE_IMAGE" "$IMAGE_TAG"; then
  echo "배포 완료: $BE_IMAGE:$IMAGE_TAG"
  exit 0
fi

echo "::error::새 버전이 healthy가 되지 못했습니다. 앱 로그 마지막 100줄:"
"${COMPOSE[@]}" logs --tail 100 app || true

# 이전 이미지가 있으면 롤백한다. 새 이미지와 같은 이미지여도 한다. (--force-recreate 로 기존 컨테이너가 이미 사라졌고,
# 이전 설정 파일로 다시 만들면 설정 때문에 실패한 경우에는 복구될 수 있다)
if [ -n "$previous" ]; then
  echo "이전 이미지($previous)와 설정 파일로 되돌립니다."
  restore_configs
  if deploy_app "${previous%:*}" "${previous##*:}"; then
    echo "::error::롤백 완료. 서비스는 이전 버전($previous)으로 돌아왔지만 이번 배포는 실패입니다."
  else
    echo "::error::롤백도 실패했습니다. 서버에서 직접 확인하세요: cd ~/wool && docker compose -f compose.prod.yml ps"
  fi
else
  echo "::error::되돌릴 이전 이미지가 없습니다. (처음 배포)"
fi
exit 1
