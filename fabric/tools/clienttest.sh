#!/usr/bin/env bash
# usage: clienttest.sh [limit-seconds], env RHWORLD_KEY, private muted display
set -uo pipefail
limit=${1:-900}
here=$(cd "$(dirname "$0")/../.." && pwd)
run="$here/fabric/build/run/clientGameTest"
key=${RHWORLD_KEY:-$here/samples/rigged-1024/gm-key.json}

ulimit -c 0
export KDE_DEBUG=1
[ "$(ulimit -c)" = 0 ] || { echo "[rhworld-client] verdict=fail reason=core-limit-not-zero"; exit 2; }
[ -f "$key" ] || { echo "[rhworld-client] verdict=fail reason=key-file-missing"; exit 2; }
tmp=$(mktemp -d "${TMPDIR:-/tmp}/rhworld-client.XXXXXX")
disp=""
for n in $(seq 140 199); do
    [ -e "/tmp/.X11-unix/X$n" ] || [ -e "/tmp/.X$n-lock" ] || { disp=$n; break; }
done
[ -n "$disp" ] || { echo "[rhworld-client] verdict=fail reason=no-free-display"; exit 2; }
Xvfb ":$disp" -screen 0 1280x720x24 -nolisten tcp -noreset >"$tmp/xvfb.log" 2>&1 &
xpid=$!
pgid=""
cleanup() {
    if [ -n "$pgid" ] && kill -0 -- "-$pgid" 2>/dev/null; then
        ps -o pid,pgid,args -g "$pgid"
        kill -- "-$pgid" 2>/dev/null
    fi
    kill "$xpid" 2>/dev/null
    wait "$xpid" 2>/dev/null
    rm -rf "$tmp"
}
trap cleanup EXIT
for _ in $(seq 1 50); do [ -e "/tmp/.X11-unix/X$disp" ] && break; sleep 0.1; done
[ -e "/tmp/.X11-unix/X$disp" ] || { echo "[rhworld-client] verdict=fail reason=xvfb-did-not-start"; exit 2; }

printf '[general]\ndrivers=null\n' >"$tmp/alsoft.conf"
mkdir -p "$tmp/xdg" && chmod 700 "$tmp/xdg"
unset WAYLAND_DISPLAY
export DISPLAY=":$disp" ALSOFT_CONF="$tmp/alsoft.conf" ALSOFT_DRIVERS=null SDL_AUDIO_DRIVER=dummy SDL_VIDEO_DRIVER=x11 \
    PULSE_SERVER=unix:/nonexistent XDG_RUNTIME_DIR="$tmp/xdg" LIBGL_ALWAYS_SOFTWARE=1 __GLX_VENDOR_LIBRARY_NAME=mesa
[ -z "${WAYLAND_DISPLAY:-}" ] && [ "$DISPLAY" = ":$disp" ] || { echo "[rhworld-client] verdict=fail reason=display-not-private"; exit 2; }

key=$(cd "$(dirname "$key")" && pwd)/$(basename "$key")
log="$here/fabric/build/clienttest.log"
start=$(date +%s)
setsid timeout "$limit" "$here/gradlew" -p "$here" :fabric:runClientGameTest --no-daemon -q "-Prhworld.key=$key" >"$log" 2>&1 </dev/null &
pid=$!
pgid=$(ps -o pgid= -p "$pid" | tr -d ' ')
wait "$pid"
rc=$?
pgid=""
secs=$(( $(date +%s) - start ))
grep -a -E '\[rhworld-client\]|OpenAL initialized|Using graphics device' "$log" | sed 's/.*\]: //'
scan=("$log")
for d in saves logs screenshots; do [ -e "$run/$d" ] && scan+=("$run/$d"); done
timeout 300 python3 -I "$here/fabric/tools/leakscan.py" "$key" "${scan[@]}" || rc=1
ls "$run/screenshots" 2>/dev/null | sed 's/^/[rhworld-client] screenshot=/'
echo "[rhworld-client] verdict=$([ $rc = 0 ] && echo pass || echo fail) gradle_exit=$rc secs=$secs log=$log"
exit $rc
