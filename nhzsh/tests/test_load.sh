#!/bin/sh
# Phase 7 tests — the `load` builtin library system.
set -u
BIN="$(dirname "$0")/../nhzsh"
fails=0
pass=0

check() {
    if [ "$2" = "$3" ]; then
        pass=$((pass+1)); echo "  ok: $1"
    else
        fails=$((fails+1)); echo "  FAIL: $1 (expected [$2], got [$3])"
    fi
}
contains() { # contains <name> <haystack> <needle>
    case "$2" in
        *"$3"*) pass=$((pass+1)); echo "  ok: $1" ;;
        *) fails=$((fails+1)); echo "  FAIL: $1 (missing [$3])" ;;
    esac
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/lib" "$TMP/ulib"

cat > "$TMP/lib/hello.sh" <<'EOF'
echo sourced-marker
alias hello_main='echo hello-from-lib'
EOF

cat > "$TMP/ulib/ulib.sh" <<'EOF'
alias ulib_main='echo user-lib-ok'
EOF

echo "[load] script-local lib/: load, double-load no-op, alias, unload"
cat > "$TMP/t.sh" <<'EOF'
load hello as h
load hello as h
h
list-libs
unload hello
list-libs
h
EOF
out="$("$BIN" "$TMP/t.sh" 2>&1)"

count="$(printf '%s\n' "$out" | grep -c '^sourced-marker$')"
check "library sourced exactly once (2nd load is a no-op)" "1" "$count"
contains "alias wrapper runs the library entry point" "$out" "hello-from-lib"
contains "second load says already loaded" "$out" "already loaded"

listed="$(printf '%s\n' "$out" | grep -c '^hello	')"
check "list-libs shows it exactly once" "1" "$listed"
contains "list-libs empty after unload" "$out" "no libraries loaded"
contains "wrapper alias gone after unload" "$out" "h: not found"

echo "[load] user-level search path (\$NHZSH_USER_LIB)"
cat > "$TMP/t2.sh" <<'EOF'
load ulib as u
u
EOF
out2="$(NHZSH_USER_LIB="$TMP/ulib" "$BIN" "$TMP/t2.sh" 2>&1)"
contains "library found via NHZSH_USER_LIB" "$out2" "user-lib-ok"

echo "[load] missing library is an honest error"
printf 'load nosuchlib\n' > "$TMP/t3.sh"
out3="$("$BIN" "$TMP/t3.sh" 2>&1)"
rc=0
"$BIN" "$TMP/t3.sh" >/dev/null 2>&1 || rc=$?
contains "missing library message" "$out3" "not found in search path"
check "missing library sets status 1" "1" "$rc"

if [ "$fails" -eq 0 ]; then
    echo "LOAD TESTS: PASSED ($pass checks)"
else
    echo "LOAD TESTS: FAILED ($fails of $((fails+pass)))"
    exit 1
fi
