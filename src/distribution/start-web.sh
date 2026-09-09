#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE=${1:-"$PWD"}
PORT=${2:-8787}
printf 'Open http://127.0.0.1:%s/ in your browser.\n' "$PORT"
if [ "$#" -ge 3 ]; then
  exec "$SCRIPT_DIR/project-sentinel.sh" --serve "$WORKSPACE" "$PORT" "$3"
fi
exec "$SCRIPT_DIR/project-sentinel.sh" --serve "$WORKSPACE" "$PORT"
