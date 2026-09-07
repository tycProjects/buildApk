#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v gradle >/dev/null 2>&1; then
  exec gradle -p "$APP_HOME" "$@"
fi
echo "Gradle executable not found. Install Gradle or use Android Studio's Gradle runner." >&2
exit 1
