#!/usr/bin/env bash
# usage: worldtest.sh <scenario> <level-type> [seed] [limit-seconds]
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
rm -rf "$run/world"
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

start=$(date +%s)
setsid timeout "$limit" "$here/gradlew" -p "$here" :fabric:runWorldTest --no-daemon -q \
    "-Prhworld.worldtest=$scenario" >"$log" 2>&1 </dev/null &
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
verdict=$(grep -a '\[rhworld-test\] verdict=' "$log" | tail -1 | sed 's/.*\]: //')
echo "[rhworld-test] world_dir=$run/world log=$log gradle_exit=$rc secs=$secs"
case "$verdict" in
    *"verdict=pass"*) exit 0 ;;
    "") echo "[rhworld-test] verdict=fail reason=no-verdict-line scenario=$scenario"; exit 1 ;;
    *) exit 1 ;;
esac
