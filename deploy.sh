#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
command -v docker >/dev/null || { echo 'Docker with Compose is required.'; exit 1; }
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo 'Created .env. Configure your deployment credentials and model settings, then rerun.'
  exit 1
fi
docker compose config --quiet
docker compose up -d --build --wait
echo 'AI Knowledge Hub is available on http://localhost:8088 (or WEB_PORT from .env).'
