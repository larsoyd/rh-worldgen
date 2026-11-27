#!/usr/bin/env bash
# usage: ordertest.sh <level-type>
set -uo pipefail
level_type=${1:?level type}
here=$(cd "$(dirname "$0")" && pwd)
out=$(mktemp -d "${TMPDIR:-/tmp}/rhworld-order.XXXXXX")
trap 'rm -rf "$out"' EXIT
start=$(date +%s)
for spec in "1 2" "2 8"; do
    read -r order threads <<< "$spec"
    RHWORLD_ORDER=$order RHWORLD_THREADS=$threads timeout 900 "$here/worldtest.sh" hashdump "$level_type" >"$out/run-$order.txt" 2>&1 || {
        tail -5 "$out/run-$order.txt"
        echo "[rhworld-order] verdict=fail reason=run-failed order=$order threads=$threads"
        exit 1
    }
    grep -a '^\[rhworld-chunk\]' "$out/run-$order.txt" | sort >"$out/chunks-$order.txt"
done
a=$(wc -l <"$out/chunks-1.txt")
differ=$(diff "$out/chunks-1.txt" "$out/chunks-2.txt" | grep -c '^<')
secs=$(( $(date +%s) - start ))
if [ "$a" -gt 0 ] && [ "$differ" = 0 ]; then
    echo "[rhworld-order] verdict=pass level_type=$level_type chunks=$a differ=0 secs=$secs"
    exit 0
fi
diff "$out/chunks-1.txt" "$out/chunks-2.txt" | head -6
echo "[rhworld-order] verdict=fail level_type=$level_type chunks=$a differ=$differ secs=$secs"
exit 1
