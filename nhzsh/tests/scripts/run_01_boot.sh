# NHZENV-style boot-sequence script (Phase 8 validation, script 1).
# Everything here is inside the nhzsh v1 grammar (concept doc §5.2):
# words, pipes, redirects, &&/||/;, variables, command substitution,
# tilde, and the builtin set. Must run unmodified.
export NHZ_ROOT=$HOME/nhz-p8
export NHZ_LOG=$HOME/nhz-p8-boot.log
mkdir -p $NHZ_ROOT/system/bin
mkdir -p $NHZ_ROOT/etc
cd $NHZ_ROOT
echo "boot: root is $NHZ_ROOT" > $NHZ_LOG
echo "boot: cwd is $(pwd)" >> $NHZ_LOG
pwd
cat $NHZ_LOG
export BOOT_STAGE=2
echo stage-$BOOT_STAGE
test -d $NHZ_ROOT/system/bin && echo dirs-ok
rm -rf $NHZ_ROOT
rm -f $NHZ_LOG
