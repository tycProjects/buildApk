#!/bin/sh
# nhzterm release helper — keeps the directory copy, git history, and zip
# copy permanently in sync (agreed workflow, 2026-09-03).
#
# Usage:
#   tools/release.sh "one-line summary of this build"
#
# It will:
#   1. read VERSION from the repo root
#   2. commit everything with message  "vX.Y.Z — <summary>"
#   3. tag the commit vX.Y.Z
#   4. rebuild /home/user/releases/nhzterm-vX.Y.Z.zip from the full directory
#   5. rebuild /home/user/releases/nhzterm-vX.Y.Z-studio.zip — project
#      contents at the ARCHIVE ROOT (no nhzterm/ wrapper, no .git, no
#      build artifacts). THIS is the zip to upload to Valence Studio:
#      its extractCart() does NOT strip a top-level folder, so a wrapped
#      zip buries settings.gradle.kts one level too deep and Studio
#      silently falls back to the WebView template tier.
#
# Bump the VERSION file BEFORE running (PATCH for fixes, MINOR for feature/
# phase milestones), and add the matching entry to BUILDLOG.md first.
set -eu

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
VERSION="$(tr -d '[:space:]' < VERSION)"
[ -n "$VERSION" ] || { echo "error: VERSION file empty"; exit 1; }
SUMMARY="${1:-}"
[ -n "$SUMMARY" ] || { echo "usage: tools/release.sh \"summary\""; exit 1; }

echo "==> nhzterm v$VERSION"

git add -A
if git diff --cached --quiet; then
    echo "    (no file changes to commit)"
else
    git commit -m "v$VERSION — $SUMMARY"
fi
git tag -f "v$VERSION"

RELEASE_DIR="/home/user/releases"
mkdir -p "$RELEASE_DIR"
ZIP="$RELEASE_DIR/nhzterm-v$VERSION.zip"
rm -f "$ZIP"
( cd "$(dirname "$ROOT")" && zip -qr "$ZIP" "$(basename "$ROOT")" )
echo "==> committed + tagged v$VERSION"
echo "==> zip: $ZIP ($(du -h "$ZIP" | cut -f1))  [full archive — backup/sharing]"

STUDIO_ZIP="$RELEASE_DIR/nhzterm-v$VERSION-studio.zip"
rm -f "$STUDIO_ZIP"
( cd "$ROOT" && zip -qr "$STUDIO_ZIP" . \
    -x ".git/*" "*.o" "nhzsh/nhzsh" "nhzsh/build-android/*" )
echo "==> studio zip: $STUDIO_ZIP ($(du -h "$STUDIO_ZIP" | cut -f1))  <-- UPLOAD THIS ONE TO STUDIO"
