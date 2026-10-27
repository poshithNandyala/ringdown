#!/bin/bash
# Render a release sequence and a contact sheet.  usage: sheet.sh <name> <frames "a b c ..."> <render args...>
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
S=${PREVIEW_DIR:-/tmp/ringdown-preview}
NAME=$1; FRAMES=$2; shift 2
rm -rf "$S/$NAME"; mkdir -p "$S/$NAME"
java -Djava.awt.headless=true -cp "$ROOT/out/h" com.ringdown.harness.Render out="$S/$NAME" "$@" 2>&1 | grep -v JAVA_TOOL
cd "$S/$NAME"
LIST=""; for f in $FRAMES; do LIST="$LIST $(printf 'f%04d.png' $f)"; done
montage $LIST -tile 5x -geometry 270x585+4+4 -background '#000' "$S/${NAME}_sheet.png"
echo "$S/${NAME}_sheet.png"
