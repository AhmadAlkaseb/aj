#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: scripts/mvn_ci.sh <Maven arguments...>" >&2
  exit 2
fi

log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
settings_file="$repo_root/.github/maven-settings.xml"
max_attempts=5
for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  : > "$log_file"
  if ((attempt == 1)); then
    mvn_command=(mvn "$@")
  else
    mvn_command=(mvn --settings "$settings_file" -U "$@")
  fi

  if "${mvn_command[@]}" 2>&1 | tee "$log_file"; then
    exit 0
  else
    exit_code=$?
  fi

  if [[ $attempt -eq $max_attempts ]] || ! grep -Eiq 'status code: 429|too many requests|HTTP 429' "$log_file"; then
    exit "$exit_code"
  fi

  if ((attempt == 1)); then
    echo "Maven Central returned HTTP 429; retrying via the fallback mirror." >&2
  else
    delay=$((15 * (2 ** (attempt - 2)) + RANDOM % 15))
    echo "Fallback mirror returned HTTP 429; retrying in ${delay}s (attempt $((attempt + 1))/$max_attempts)." >&2
    sleep "$delay"
  fi
done
