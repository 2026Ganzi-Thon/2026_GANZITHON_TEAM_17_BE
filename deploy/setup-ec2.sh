#!/usr/bin/env bash
# Ubuntu 22.04/24.04 EC2 최초 1회 세팅: 스왑 2GB + Docker 설치 + 배포 폴더(~/wool) 만들기.
# 사용: bash setup-ec2.sh   (실행 후 로그아웃/재접속해야 docker 그룹이 적용된다)
set -euo pipefail

# 스왑: 램 1GB(t3.micro)에서 Spring과 MySQL을 함께 돌릴 때 메모리 부족(OOM)을 막는다.
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# Docker (공식 설치 스크립트)
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi
# 이미 Docker가 설치돼 있어도 이 사용자가 docker 그룹에 없을 수 있어서, 설치와 별개로 항상 추가한다. (러너가 docker 명령을 쓰려면 필요)
getent group docker >/dev/null || sudo groupadd docker
sudo usermod -aG docker "$USER"

mkdir -p ~/wool ~/wool/backups
echo "완료. 재접속 후 'docker compose version' 으로 확인하세요."
