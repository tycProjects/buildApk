#!/bin/sh
# NIVEX Gradle bootstrap wrapper for Linux/macOS CI and EAS Build.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=9.3.1
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin"
DIST_ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --retry-delay 2 -o "$DIST_ZIP" "$URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$DIST_ZIP" "$URL"
    else
      echo "ERROR: curl or wget is required to bootstrap Gradle." >&2
      exit 1
    fi
  fi
  rm -rf "$GRADLE_HOME"
  tmp="$DIST_DIR/.extract-$$"
  rm -rf "$tmp"
  mkdir -p "$tmp"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$DIST_ZIP" -d "$tmp"
  else
    echo "ERROR: unzip is required to bootstrap Gradle." >&2
    exit 1
  fi
  mv "$tmp/gradle-$GRADLE_VERSION" "$GRADLE_HOME"
  rm -rf "$tmp"
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
