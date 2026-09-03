#!/bin/sh
# Full nhzsh test suite: every phase of Part 2 has real tests behind it
# before the next one counts as done (build plan, Part 2 preamble).
set -u
cd "$(dirname "$0")/.."
fails=0

run() {
    echo "== $1 =="
    shift
    "$@" || fails=$((fails+1))
    echo
}

run "Phase 1 — lexer"            ./tests/test_lexer
run "Phase 2 — parser"           ./tests/test_parser
run "Phase 3 — expander"         ./tests/test_expander
run "Phase 4 — executor"         ./tests/test_executor
run "Phase 5 — state & REPL"     sh tests/test_shell.sh
run "Phase 6 — daemon link"      ./tests/test_daemon_link
run "Phase 7 — load builtin"     sh tests/test_load.sh
run "Phase 8 — real scripts"     sh tests/run_phase8.sh

echo "----------------------------------------"
if [ "$fails" -eq 0 ]; then
    echo "ALL PHASES PASSED"
else
    echo "$fails phase group(s) FAILED"
    exit 1
fi
