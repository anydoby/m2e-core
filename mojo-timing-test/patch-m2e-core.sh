#!/bin/sh
# Put the m2e "build interrupt" and "slow mojo warning" classes into the installed m2e jars.
#
# Usage:  patch-m2e-core.sh <eclipse plugins dir> [classes zip]
#   Linux: patch-m2e-core.sh ~/eclipse/2023/eclipse/plugins
#   macOS: patch-m2e-core.sh /Applications/Eclipse.app/Contents/Eclipse/plugins
# Undo:   patch-m2e-core.sh <eclipse plugins dir> --restore
#
# Close Eclipse first, or restart it after the patch. The original jars are kept
# in ~/eclipse-debug-backup/. The zip has one folder for each bundle. The classes
# are built from m2e tag 2.12.0, so the script refuses other bundle versions.
set -eu

PLUGINS=${1:?plugins directory missing}
HERE=$(cd "$(dirname "$0")" && pwd)
CLASSES=${2:-$HERE/m2e-build-interrupt-classes.zip}
BACKUP=$HOME/eclipse-debug-backup
BUNDLES="org.eclipse.m2e.core:2.8.0 org.eclipse.m2e.core.ui:2.4.201 org.eclipse.m2e.editor:2.1.200"

find_jar() {
	# exact bundle name: org.eclipse.m2e.core_ must not match org.eclipse.m2e.core.ui_
	ls "$PLUGINS"/"$1"_[0-9]*.jar 2>/dev/null | head -n 1
}

if [ "$CLASSES" = "--restore" ]; then
	for entry in $BUNDLES; do
		bundle=${entry%%:*}
		jar=$(find_jar "$bundle")
		[ -n "$jar" ] || continue
		name=$(basename "$jar")
		if [ -f "$BACKUP/$name" ]; then
			cp "$BACKUP/$name" "$jar.tmp" && mv "$jar.tmp" "$jar"
			echo "Restored $jar"
		fi
	done
	exit 0
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/zip"
(cd "$WORK/zip" && unzip -q "$CLASSES")

# Check all bundles first, so that the script changes nothing if one does not fit.
for entry in $BUNDLES; do
	bundle=${entry%%:*}
	version=${entry#*:}
	[ -d "$WORK/zip/$bundle" ] || continue
	jar=$(find_jar "$bundle")
	[ -n "$jar" ] || { echo "No $bundle jar in $PLUGINS" >&2; exit 1; }
	case "$(basename "$jar")" in
		"${bundle}_$version".*) ;;
		*) echo "Installed $(basename "$jar"), but the classes are for $bundle $version." >&2; exit 1 ;;
	esac
done

mkdir -p "$BACKUP"
for entry in $BUNDLES; do
	bundle=${entry%%:*}
	[ -d "$WORK/zip/$bundle" ] || continue
	jar=$(find_jar "$bundle")
	name=$(basename "$jar")
	[ -f "$BACKUP/$name" ] || cp "$jar" "$BACKUP/$name"
	cp "$BACKUP/$name" "$WORK/$name"
	# A changed jar does not match its signature any more.
	zip -q -d "$WORK/$name" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.EC' 'META-INF/*.DSA' >/dev/null 2>&1 || true
	(cd "$WORK/zip/$bundle" && zip -q -r "$WORK/$name" .)
	cp "$WORK/$name" "$jar.tmp" && mv "$jar.tmp" "$jar"
	echo "Patched $jar (original in $BACKUP/$name)"
done
echo "Restart Eclipse."
