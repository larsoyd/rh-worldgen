#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")" || exit 1
rc=0
for dir in tests verify/tests; do
    [ -d "$dir" ] || { echo "[tests] refused reason=missing dir=$dir"; exit 1; }
    start=$SECONDS
    timeout "${TEST_TIMEOUT:-300}" python3 -m unittest discover -s "$dir" -t . "$@"
    status=$?
    echo "[tests] suite=$dir exit=$status seconds=$((SECONDS - start))"
    [ $status -eq 0 ] || rc=1
done
exit $rc
