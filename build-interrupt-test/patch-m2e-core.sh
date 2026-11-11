#!/bin/sh
# Put the m2e "build interrupt" classes into the installed org.eclipse.m2e.core jar.
#
# Usage:  patch-m2e-core.sh <eclipse plugins dir> [classes zip]
#   Linux: patch-m2e-core.sh ~/eclipse/2023/eclipse/plugins
#   macOS: patch-m2e-core.sh /Applications/Eclipse.app/Contents/Eclipse/plugins
# Undo:   patch-m2e-core.sh <eclipse plugins dir> --restore
#
# Close Eclipse first, or restart it after the patch. The original jar is kept
# in ~/eclipse-debug-backup/. The classes are built from m2e tag 2.12.0
# (org.eclipse.m2e.core 2.8.0), so the script refuses other versions.
set -eu

PLUGINS=${1:?plugins directory missing}
HERE=$(cd "$(dirname "$0")" && pwd)
CLASSES=${2:-$HERE/m2e-build-interrupt-classes.zip}
BACKUP=$HOME/eclipse-debug-backup

JAR=$(ls "$PLUGINS"/org.eclipse.m2e.core_*.jar 2>/dev/null | head -n 1)
[ -n "$JAR" ] || { echo "No org.eclipse.m2e.core jar in $PLUGINS" >&2; exit 1; }
NAME=$(basename "$JAR")

if [ "$CLASSES" = "--restore" ]; then
	[ -f "$BACKUP/$NAME" ] || { echo "No backup $BACKUP/$NAME" >&2; exit 1; }
	cp "$BACKUP/$NAME" "$JAR.tmp" && mv "$JAR.tmp" "$JAR"
	echo "Restored $JAR"
	exit 0
fi

case "$NAME" in
	org.eclipse.m2e.core_2.8.0.*) ;;
	*) echo "Installed version is $NAME, but the classes are for 2.8.0. Build them from the matching m2e tag." >&2; exit 1 ;;
esac

mkdir -p "$BACKUP"
[ -f "$BACKUP/$NAME" ] || cp "$JAR" "$BACKUP/$NAME"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$BACKUP/$NAME" "$WORK/$NAME"
# A changed jar does not match its signature any more.
zip -q -d "$WORK/$NAME" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.EC' 'META-INF/*.DSA' >/dev/null 2>&1 || true
mkdir "$WORK/classes"
(cd "$WORK/classes" && unzip -q "$CLASSES")
(cd "$WORK/classes" && zip -q -r "$WORK/$NAME" .)
cp "$WORK/$NAME" "$JAR.tmp" && mv "$JAR.tmp" "$JAR"
echo "Patched $JAR (original in $BACKUP/$NAME). Restart Eclipse."
