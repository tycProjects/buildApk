#!/bin/sh
# Phase 5 tests — state & the REPL loop, driven through the real binary.
set -u
BIN="$(dirname "$0")/../nhzsh"
fails=0
pass=0

check() { # check <name> <expected> <actual>
    if [ "$2" = "$3" ]; then
        pass=$((pass+1)); echo "  ok: $1"
    else
        fails=$((fails+1)); echo "  FAIL: $1 (expected [$2], got [$3])"
    fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "[shell] script mode: cwd & env persist across lines"
cat > "$TMP/s.sh" <<'EOF'
cd /tmp
pwd
export FOO_S=hello
echo $FOO_S-$?
EOF
out="$("$BIN" "$TMP/s.sh")"
check "line 2 sees line 1's cd" "/tmp" "$(printf '%s\n' "$out" | sed -n 1p)"
check "line 4 sees line 3's export, and \$?" "hello-0" "$(printf '%s\n' "$out" | sed -n 2p)"

echo "[shell] no prompt when not a TTY"
case "$out" in
    *'$ '*) fails=$((fails+1)); echo "  FAIL: prompt leaked into script output" ;;
    *) pass=$((pass+1)); echo "  ok: prompt suppressed when not attached to a TTY" ;;
esac

echo "[shell] stdin mode (piped commands, same code path)"
out="$(printf 'echo stdin-ok\npwd\ncd /\npwd\n' | "$BIN")"
check "command from stdin runs" "stdin-ok" "$(printf '%s\n' "$out" | sed -n 1p)"
check "cd persists on stdin path" "/" "$(printf '%s\n' "$out" | sed -n 3p)"

echo "[shell] -c mode"
out="$("$BIN" -c 'echo c-mode-works')"
check "-c runs one command" "c-mode-works" "$out"
"$BIN" -c 'sh -c "exit 7"'
check "-c propagates exit status" "7" "$?"

echo "[shell] script exit status is the last command's"
printf 'true\nfalse\n' > "$TMP/rc.sh"
"$BIN" "$TMP/rc.sh"
check "script exits with last status" "1" "$?"

echo "[shell] exit builtin stops a script early"
printf 'echo before\nexit 5\necho after\n' > "$TMP/ex.sh"
out="$("$BIN" "$TMP/ex.sh")"
check "exit stops execution" "before" "$out"
"$BIN" "$TMP/ex.sh"
check "exit code honored" "5" "$?"

if [ "$fails" -eq 0 ]; then
    echo "SHELL TESTS: PASSED ($pass checks)"
else
    echo "SHELL TESTS: FAILED ($fails of $((fails+pass)))"
    exit 1
fi
