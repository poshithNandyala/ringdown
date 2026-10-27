#!/bin/bash
# Builds out/apk/Ringdown.apk without Gradle: javac -> d8 -> aapt2 -> apksig.
#
# Tools are found in this order:
#   1. env vars SDK_JAR (android.jar, API 35), AAPT2 (binary), R8_CP (classpath with D8 + apksig)
#   2. $ANDROID_HOME (platforms/android-35, newest build-tools)
#   3. ./buildtools/ (android-35.jar, aapt2, r8-apksig.jar)
# Signing key: keystore/ringdown.p12 (created on first build if missing).
set -euo pipefail
cd "$(dirname "$0")"

MIN=26
TARGET=34
VERSION_CODE=2
VERSION_NAME=1.1
OUT=out/apk

SDK_JAR=${SDK_JAR:-}
AAPT2=${AAPT2:-}
R8_CP=${R8_CP:-}
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    BTV=$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)
    SDK_JAR=${SDK_JAR:-$ANDROID_HOME/platforms/android-35/android.jar}
    AAPT2=${AAPT2:-$ANDROID_HOME/build-tools/$BTV/aapt2}
    R8_CP=${R8_CP:-$ANDROID_HOME/build-tools/$BTV/lib/d8.jar:$ANDROID_HOME/build-tools/$BTV/lib/apksigner.jar}
fi
SDK_JAR=${SDK_JAR:-buildtools/android-35.jar}
AAPT2=${AAPT2:-buildtools/aapt2}
R8_CP=${R8_CP:-buildtools/r8-apksig.jar}
for f in "$SDK_JAR" "$AAPT2"; do [ -e "$f" ] || { echo "missing $f (see README: Build)"; exit 1; }; done

quiet() { grep -v -E "JAVA_TOOL_OPTIONS|^warning: \[options\]|^[0-9]+ warnings?$|^Note:" || true; }

if [ ! -f keystore/ringdown.p12 ]; then
    echo "creating a new signing key in keystore/ (keep it: updates must use the same key)"
    mkdir -p keystore
    keytool -genkeypair -alias ringdown -keyalg RSA -keysize 2048 -validity 36500 \
        -keystore keystore/ringdown.p12 -storetype PKCS12 -storepass ringdown -keypass ringdown \
        -dname "CN=Ringdown" 2>&1 | quiet
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/res" "$OUT/signer"

echo "[1/6] compiling Java"
javac -proc:none -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK_JAR" \
    -d "$OUT/classes" $(find src/core src/app -name '*.java') 2>&1 | quiet
test -f "$OUT/classes/com/poshith/ringdown/RingsWallpaperService.class"

echo "[2/6] dexing"
java -cp "$R8_CP" com.android.tools.r8.D8 --release --min-api $MIN --lib "$SDK_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class') 2>&1 | quiet
test -f "$OUT/dex/classes.dex"

echo "[3/6] compiling resources"
"$AAPT2" compile --dir res -o "$OUT/res/res.zip"

echo "[4/6] linking"
"$AAPT2" link -o "$OUT/base.apk" -I "$SDK_JAR" --manifest AndroidManifest.xml \
    --min-sdk-version $MIN --target-sdk-version $TARGET \
    --version-code $VERSION_CODE --version-name $VERSION_NAME -A assets "$OUT/res/res.zip"

echo "[5/6] packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -9 ../unsigned.apk classes.dex)

echo "[6/6] signing (v1+v2+v3)"
javac -proc:none -cp "$R8_CP" -d "$OUT/signer" buildtools/ApkSign.java 2>&1 | quiet
java -cp "$R8_CP:$OUT/signer" ApkSign sign "$OUT/unsigned.apk" "$OUT/Ringdown.apk" keystore/ringdown.p12 ringdown ringdown $MIN 2>&1 | quiet
java -cp "$R8_CP:$OUT/signer" ApkSign verify "$OUT/Ringdown.apk" 2>&1 | quiet
ls -la "$OUT/Ringdown.apk"
