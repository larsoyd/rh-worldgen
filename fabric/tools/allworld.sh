#!/usr/bin/env bash
# usage: allworld.sh  runs every dedicated server scenario in sequence
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
rigged="$root/samples/rigged-1024/gm-key.json"
random="$root/samples/random-1024/gm-key.json"
pass=0
fail=0
start=$(date +%s)
step() {
    local name=$1
    shift
    local out
    out=$("$@" 2>&1)
    local rc=$?
    local line
    line=$(printf '%s\n' "$out" | grep -E 'verdict=' | grep -v rhworld-leak | tail -1)
    if [ $rc = 0 ]; then pass=$((pass + 1)); else fail=$((fail + 1)); printf '%s\n' "$out" | grep -E 'ok=false|reason=' | head -5; fi
    echo "[rhworld-all] step=$name exit=$rc $line"
}
step terrain timeout 900 "$here/worldtest.sh" terrain rhworld:wild
step realms timeout 900 "$here/worldtest.sh" realms rhworld:wild
step vanilla timeout 900 "$here/worldtest.sh" vanilla minecraft:normal
step hubs timeout 900 "$here/worldtest.sh" hubs rhworld:wild
step hubs-vanilla timeout 900 "$here/worldtest.sh" hubs minecraft:normal
step no-modulus timeout 900 "$here/worldtest.sh" refuse-no-modulus rhworld-test:unkeyed
step key-mismatch env RHWORLD_KEY="$random" timeout 900 "$here/worldtest.sh" refuse-key-file-mismatch rhworld:wild_rigged
step rides env RHWORLD_KEY="$rigged" timeout 900 "$here/worldtest.sh" rides rhworld:wild_rigged
step concord env RHWORLD_KEY="$rigged" timeout 900 "$here/worldtest.sh" concord rhworld:wild_rigged
step persist1 env RHWORLD_KEY="$rigged" timeout 900 "$here/worldtest.sh" persist1 rhworld:wild_rigged
step persist2 env RHWORLD_KEY="$rigged" RHWORLD_KEEP=1 timeout 900 "$here/worldtest.sh" persist2 rhworld:wild_rigged
step order timeout 1900 "$here/ordertest.sh" rhworld:wild
step leakscan-unit timeout 120 python3 -I "$here/test_leakscan.py"
echo "[rhworld-all] verdict=$([ $fail = 0 ] && echo pass || echo fail) passed=$pass failed=$fail secs=$(( $(date +%s) - start ))"
[ $fail = 0 ]
