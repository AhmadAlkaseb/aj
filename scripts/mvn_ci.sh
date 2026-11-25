#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: scripts/mvn_ci.sh <Maven arguments...>" >&2
  exit 2
fi

log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT

max_attempts=5
for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  : > "$log_file"
  if ((attempt == 1)); then
    mvn_command=(mvn "$@")
  else
    mvn_command=(mvn -U "$@")
  fi

  if "${mvn_command[@]}" 2>&1 | tee "$log_file"; then
    exit 0
  else
    exit_code=$?
  fi

  if [[ $attempt -eq $max_attempts ]] || ! grep -Eiq 'status code: 429|too many requests|HTTP 429' "$log_file"; then
    exit "$exit_code"
  fi

  delay=$((15 * (2 ** (attempt - 1)) + RANDOM % 15))
  echo "Maven repository returned HTTP 429; retrying in ${delay}s (attempt $((attempt + 1))/$max_attempts)." >&2
  sleep "$delay"
done
