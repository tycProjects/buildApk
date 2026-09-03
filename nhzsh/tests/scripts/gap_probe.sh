# Phase 8 gap probe — deliberately uses constructs OUTSIDE the nhzsh v1
# grammar. This script is EXPECTED to fail; run_phase8.sh records the
# failures as the honest "what to build next" signal the build plan asks
# for, rather than pretending coverage we don't have.
if [ -d /tmp ]; then echo conditional-works; fi
for f in *; do echo $f; done
x=$((1 + 2))
myfunc() { echo func-works; }
