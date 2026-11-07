#!/usr/bin/env bash
# The gate: everything `./gradlew check` runs (compile with -Werror, Error Prone and NullAway,
# every module's tests under the E8 and E22 judges, mutation testing of the domain, and the
# architecture meta-test over every check and breach), plus a smoke run of the host, held to
# the guide's full-gate budget of 2 minutes (E8).
set -euo pipefail
cd "$(dirname "$0")/.."

budget_seconds=120
start=$(date +%s)
./gradlew --quiet check
./gradlew --quiet :host:run --args="build/smoke-notices.jsonl"
took=$(( $(date +%s) - start ))

echo "gate: green in ${took}s (budget ${budget_seconds}s)"
if (( took > budget_seconds )); then
    echo "gate: over the ${budget_seconds}s budget (E8)" >&2
    exit 1
fi
