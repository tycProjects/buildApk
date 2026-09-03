# Pipeline / glob / redirect exercises (Phase 8 validation, script 2).
# Must run unmodified under nhzsh.
mkdir -p /tmp/nhzsh-p8 && cd /tmp/nhzsh-p8
echo alpha > a.txt
echo beta > b.txt
ls *.txt | sort | tr '\n' ' '
echo
echo files: $(ls *.txt | wc -l)
grep -h . a.txt b.txt | sort -r
cat < a.txt
rm -rf /tmp/nhzsh-p8
