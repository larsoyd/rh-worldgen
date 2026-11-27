#!/usr/bin/env bash
# usage: worldtest.sh <scenario> <level-type> [seed] [limit], env RHWORLD_KEY ORDER THREADS KEEP
set -uo pipefail
scenario=${1:?scenario}
level_type=${2:?level type}
seed=${3:-12345}
limit=${4:-600}
here=$(cd "$(dirname "$0")/../.." && pwd)
run="$here/fabric/build/worldtest"
log="$run/worldtest-$scenario.log"

ulimit -c 0
export KDE_DEBUG=1
unset DISPLAY WAYLAND_DISPLAY
[ "$(ulimit -c)" = 0 ] || { echo "[rhworld-test] verdict=fail reason=core-limit-not-zero"; exit 2; }

mkdir -p "$run"
[ -n "${RHWORLD_KEEP:-}" ] || rm -rf "$run/world"
rm -rf "$run/logs" "$run/crash-reports" "$run/config/rhworld"
if [ -n "${RHWORLD_KEY:-}" ]; then
    [ -f "$RHWORLD_KEY" ] || { echo "[rhworld-test] verdict=fail reason=key-file-missing"; exit 2; }
    mkdir -p "$run/config/rhworld"
    cp "$RHWORLD_KEY" "$run/config/rhworld/key.json"
fi
printf 'eula=true\n' >"$run/eula.txt"
port=$((25600 + RANDOM % 2000))
cat >"$run/server.properties" <<EOF
level-type=${level_type/:/\\:}
level-seed=$seed
online-mode=false
server-ip=127.0.0.1
server-port=$port
spawn-protection=0
sync-chunk-writes=true
EOF

rev=$(git -C "$here" rev-parse --short HEAD 2>/dev/null || echo unknown)
dirty=$([ -z "$(git -C "$here" status --porcelain -- fabric engine mechanics 2>/dev/null)" ] && echo no || echo yes)
start=$(date +%s)
setsid timeout "$limit" "$here/gradlew" -p "$here" :fabric:runWorldTest --no-daemon -q \
    "-Prhworld.worldtest=$scenario" "-Prhworld.order=${RHWORLD_ORDER:-0}" \
    ${RHWORLD_THREADS:+"-Prhworld.threads=$RHWORLD_THREADS"} >"$log" 2>&1 </dev/null &
pid=$!
pgid=$(ps -o pgid= -p "$pid" | tr -d ' ')
cleanup() {
    if [ -n "$pgid" ] && kill -0 -- "-$pgid" 2>/dev/null; then
        ps -o pid,pgid,args -g "$pgid"
        kill -- "-$pgid" 2>/dev/null
    fi
}
trap cleanup EXIT
wait "$pid"
rc=$?
secs=$(( $(date +%s) - start ))

grep -a '\[rhworld' "$log" | sed 's/.*\]: //'
[ -d "$run/world" ] && printf 'rev=%s dirty=%s scenario=%s level_type=%s seed=%s\n' "$rev" "$dirty" "$scenario" "$level_type" "$seed" >"$run/world/rhworld-provenance.txt"
echo "[rhworld-test] provenance rev=$rev dirty=$dirty"
leak=0
if [ -n "${RHWORLD_KEY:-}" ]; then
    scan=("$log")
    for d in world logs crash-reports; do [ -e "$run/$d" ] && scan+=("$run/$d"); done
    timeout 300 python3 -I "$here/fabric/tools/leakscan.py" "$RHWORLD_KEY" "${scan[@]}" || leak=1
fi
[ $leak = 0 ] || { echo "[rhworld-test] verdict=fail reason=factor-leak scenario=$scenario"; exit 1; }
if [[ $scenario == refuse-* ]]; then
    want="refused: ${scenario#refuse-}"
    want=${want//-/ }
    echo "[rhworld-test] world_dir=$run/world log=$log gradle_exit=$rc secs=$secs"
    if grep -aq "event=load .*result=$want" "$log" && ! grep -aq 'Done (' "$log"; then
        echo "[rhworld-test] verdict=pass scenario=$scenario reason=\"$want\""
        exit 0
    fi
    echo "[rhworld-test] verdict=fail scenario=$scenario expected=\"$want\""
    exit 1
fi
verdict=$(grep -a '\[rhworld-test\] verdict=' "$log" | tail -1 | sed 's/.*\]: //')
echo "[rhworld-test] world_dir=$run/world log=$log gradle_exit=$rc secs=$secs"
case "$verdict" in
    *"verdict=pass"*) exit 0 ;;
    "") echo "[rhworld-test] verdict=fail reason=no-verdict-line scenario=$scenario"; exit 1 ;;
    *) exit 1 ;;
esac
