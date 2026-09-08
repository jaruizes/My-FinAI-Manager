#!/usr/bin/env bash

set -euo pipefail

TITLE="${1:-}"

if [[ -z "$TITLE" ]]; then
  echo "::error::Pull Request title is empty."
  exit 1
fi

ALLOWED_PREFIXES=(
  "feat/"
  "enabler/"
  "fix/"
  "chore/"
  "docs/"
  "refactor/"
  "test/"
  "ci/"
  "build/"
  "perf/"
)

for prefix in "${ALLOWED_PREFIXES[@]}"; do
  if [[ "$TITLE" == "$prefix"* ]] && [[ "${#TITLE}" -gt "${#prefix}" ]]; then
    echo "Valid Pull Request title: $TITLE"
    exit 0
  fi
done

echo "::error::Invalid Pull Request title: '$TITLE'"
echo "The title must start with one of:"
printf '  - %s<description>\n' "${ALLOWED_PREFIXES[@]}"

echo
echo "Examples:"
echo "  feat/FD001 create portfolio"
echo "  enabler/EN001 establish executable platform"
echo "  fix/EN001 correct hello endpoint"
echo "  chore/update dependencies"

exit 1
