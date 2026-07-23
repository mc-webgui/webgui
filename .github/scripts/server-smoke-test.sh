#!/usr/bin/env bash
#
# CI smoke test: boot a REAL dedicated server with the mod and assert it reaches
# "Done (…s)!" with the WebGUI mod initialized. Headless — no display needed
# (a dedicated server never creates a GL context).
#
# Usage: server-smoke-test.sh <gradle-module> [timeout-seconds]
#   e.g. server-smoke-test.sh :1.21.11-neoforge 360
#
# Exit 0 = server started with the mod; non-zero = never reached "Done", the mod
# failed to init, or a mod-loading/startup crash was detected.

set -uo pipefail

MODULE="${1:?usage: server-smoke-test.sh <gradle-module> [timeout-seconds]}"
TIMEOUT="${2:-360}"

NAME="${MODULE#:}"                 # strip leading ':'
RUN_DIR="versions/${NAME}/run"
mkdir -p "$RUN_DIR"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
# Flat world + tiny view distance keeps first-run world-gen fast.
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-players=1\nview-distance=4\n' \
    > "$RUN_DIR/server.properties"

LOG="$(mktemp)"
cleanup() {
    kill -TERM "${GRADLE_PID:-}" 2>/dev/null || true
    pkill -TERM -f 'net.neoforged.devlaunch.Main' 2>/dev/null || true
    pkill -TERM -f 'fabric.dli.env=server'        2>/dev/null || true
    pkill -TERM -f 'net.fabricmc.loader.impl.launch.knot.KnotServer' 2>/dev/null || true
    sleep 2
    pkill -KILL -f 'net.neoforged.devlaunch.Main' 2>/dev/null || true
    pkill -KILL -f 'fabric.dli.env=server'        2>/dev/null || true
    pkill -KILL -f 'net.fabricmc.loader.impl.launch.knot.KnotServer' 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Booting ${MODULE}:runServer (timeout ${TIMEOUT}s)"
# stdin from /dev/null: the console reader hits EOF and stops reading, but the
# server keeps running — we stop it ourselves once it has started.
./gradlew "${MODULE}:runServer" --console=plain --no-daemon </dev/null >"$LOG" 2>&1 &
GRADLE_PID=$!

DONE_RE='Done \([0-9.]+s\)!'
FAIL_RE='invalid dist|ModLoadingException|has failed to load correctly|Failed to start the minecraft server|Exception in thread "main"|Crash report|BUILD FAILED'

result=1
elapsed=0
while (( elapsed < TIMEOUT )); do
    if grep -qE "$DONE_RE" "$LOG"; then
        result=0
        break
    fi
    if grep -qiE "$FAIL_RE" "$LOG"; then
        echo "::error::Detected a startup/mod-loading failure before 'Done'."
        result=1
        break
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo "::error::Server process exited before reaching 'Done'."
        result=1
        break
    fi
    sleep 3
    elapsed=$((elapsed + 3))
done

if (( result == 0 )); then
    if grep -q 'WebGUI common init' "$LOG"; then
        echo "✅ Dedicated server started with the WebGUI mod loaded:"
        grep -E 'mod loading, version|WebGUI common init|webgui token secret|'"$DONE_RE" "$LOG" | sed 's/^/    /'
    else
        echo "::error::Server reached 'Done' but the WebGUI mod did not initialize."
        result=1
    fi
else
    (( elapsed >= TIMEOUT )) && echo "::error::Server did not reach 'Done' within ${TIMEOUT}s."
fi

if (( result != 0 )); then
    echo "::group::Server log (last 150 lines)"
    tail -n 150 "$LOG"
    echo "::endgroup::"
fi

exit "$result"
