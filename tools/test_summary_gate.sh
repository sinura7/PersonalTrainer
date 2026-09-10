#!/bin/sh
# Prove preflight's summary gate fails on a non-zero count.
#
# It did not. `grep -F "0 mismatch(es)"` is satisfied by "10 mismatch(es) across 680
# files", so check-named-args, check-when-exhaustive and check-unused-imports — the three
# checkers judged on their summary line rather than an exit code — reported clean at 10,
# 20, 30 ... findings. Nothing had ever watched that gate fail, which is the same reason
# every other checker here exists.
set -e
cd "$(dirname "$0")/.."

# The exact predicate preflight uses, kept in one place so the two cannot drift.
gate() { printf '%s\n' "$2" | awk -v want="$1" 'index($0, want) == 1 { hit = 1 } END { exit !hit }'; }

fail=0
check() { # check <description> <expect pass|fail> <want> <output>
    if gate "$3" "$4"; then got=pass; else got=fail; fi
    if [ "$got" = "$2" ]; then
        echo "ok  $1"
    else
        echo "FAIL $1 — expected $2, got $got"
        fail=1
    fi
}

check "a clean count passes"                pass "0 mismatch(es)"    "$(printf '\n0 mismatch(es) across 680 files')"
check "ten findings fail (the false green)" fail "0 mismatch(es)"    "$(printf '\n10 mismatch(es) across 680 files')"
check "twenty findings fail"                fail "0 mismatch(es)"    "$(printf '\n20 mismatch(es) across 680 files')"
check "one finding fails"                   fail "0 mismatch(es)"    "$(printf '\n1 mismatch(es) across 680 files')"
check "unused imports, clean"               pass "0 unused import(s)" "$(printf '\n0 unused import(s)')"
check "unused imports, thirty"              fail "0 unused import(s)" "$(printf '\n30 unused import(s)')"
check "when-exhaustive, clean"              pass "0 non-exhaustive"   "$(printf '\n0 non-exhaustive when block(s); 103 types indexed')"
check "when-exhaustive, forty"              fail "0 non-exhaustive"   "$(printf '\n40 non-exhaustive when block(s); 103 types indexed')"
check "the count must start the line"       fail "0 mismatch(es)"     "found 0 mismatch(es) somewhere mid-line"

[ "$fail" = 0 ] || exit 1
echo "test_summary_gate: all assertions passed"
