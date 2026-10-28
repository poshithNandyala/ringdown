#!/bin/bash
# Builds, without Gradle:
#   out/apk/Ringdown.apk   signed APK (GitHub download, install by hand)
#   out/aab/Ringdown.aab   signed Android App Bundle (upload this to Google Play)
# Pipeline: aapt2 -> javac -> d8 -> apksig, and aapt2 (proto) -> bundletool -> jarsigner.
#
# Tools are found in this order:
#   1. env vars SDK_JAR (android.jar, API 36), AAPT2 (binary), R8_CP (classpath with D8 + apksig),
#      BUNDLETOOL (bundletool-all.jar)
#   2. $ANDROID_HOME (platforms/android-36, newest build-tools)
#   3. ./buildtools/ (android-36.jar, aapt2, r8-apksig.jar, bundletool.jar)
# Signing key: keystore/ringdown.p12, password in keystore/password.txt or $RINGDOWN_KEY_PASS.
# Both are created on the first build if missing. Never commit them; back them up.
set -euo pipefail
cd "$(dirname "$0")"

MIN=26
TARGET=36
VERSION_CODE=5
VERSION_NAME=1.3.1
OUT=out/apk
AAB=out/aab

SDK_JAR=${SDK_JAR:-}
AAPT2=${AAPT2:-}
R8_CP=${R8_CP:-}
BUNDLETOOL=${BUNDLETOOL:-}
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    BTV=$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)
    SDK_JAR=${SDK_JAR:-$ANDROID_HOME/platforms/android-36/android.jar}
    AAPT2=${AAPT2:-$ANDROID_HOME/build-tools/$BTV/aapt2}
    R8_CP=${R8_CP:-$ANDROID_HOME/build-tools/$BTV/lib/d8.jar:$ANDROID_HOME/build-tools/$BTV/lib/apksigner.jar}
fi
SDK_JAR=${SDK_JAR:-buildtools/android-36.jar}
AAPT2=${AAPT2:-buildtools/aapt2}
R8_CP=${R8_CP:-buildtools/r8-apksig.jar}
BUNDLETOOL=${BUNDLETOOL:-buildtools/bundletool.jar}
for f in "$SDK_JAR" "$AAPT2"; do [ -e "$f" ] || { echo "missing $f (see README: Build from source)"; exit 1; }; done

quiet() { grep -v -E "JAVA_TOOL_OPTIONS|^warning: \[options\]|^[0-9]+ warnings?$|^Note:" || true; }

# ---- signing key
KEYSTORE=keystore/ringdown.p12
ALIAS=ringdown
if [ ! -f "$KEYSTORE" ]; then
    echo "creating a new signing key in keystore/ (back it up: every update must be signed with it)"
    mkdir -p keystore
    head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32 > keystore/password.txt
    chmod 600 keystore/password.txt
    KP=$(cat keystore/password.txt)
    keytool -genkeypair -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
        -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KP" -keypass "$KP" \
        -dname "CN=Ringdown" 2>&1 | quiet
fi
KEY_PASS=${RINGDOWN_KEY_PASS:-$(cat keystore/password.txt 2>/dev/null || true)}
[ -n "$KEY_PASS" ] || { echo "no key password: put it in keystore/password.txt or RINGDOWN_KEY_PASS"; exit 1; }
export KEY_PASS

rm -rf "$OUT" "$AAB"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/res" "$OUT/gen" "$OUT/signer" "$AAB"

echo "[1/8] compiling resources"
"$AAPT2" compile --dir res -o "$OUT/res/res.zip"

echo "[2/8] linking resources"
LINK_ARGS=(-I "$SDK_JAR" --manifest AndroidManifest.xml --min-sdk-version $MIN --target-sdk-version $TARGET
    --version-code $VERSION_CODE --version-name $VERSION_NAME -A assets)
"$AAPT2" link -o "$OUT/base.apk" "${LINK_ARGS[@]}" --java "$OUT/gen" --emit-ids "$OUT/ids.txt" "$OUT/res/res.zip"

echo "[3/8] compiling Java"
javac -proc:none -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK_JAR" \
    -d "$OUT/classes" $(find src/core src/app "$OUT/gen" -name '*.java') 2>&1 | quiet
test -f "$OUT/classes/com/poshith/ringdown/RingsWallpaperService.class"

echo "[4/8] dexing"
java -cp "$R8_CP" com.android.tools.r8.D8 --release --min-api $MIN --lib "$SDK_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class') 2>&1 | quiet
test -f "$OUT/dex/classes.dex"

echo "[5/8] packaging and signing the APK (v1+v2+v3)"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -9 ../unsigned.apk classes.dex)
javac -proc:none -cp "$R8_CP" -d "$OUT/signer" buildtools/ApkSign.java 2>&1 | quiet
java -cp "$R8_CP:$OUT/signer" ApkSign sign "$OUT/unsigned.apk" "$OUT/Ringdown.apk" "$KEYSTORE" "$ALIAS" "$KEY_PASS" $MIN 2>&1 | quiet
java -cp "$R8_CP:$OUT/signer" ApkSign verify "$OUT/Ringdown.apk" 2>&1 | quiet

if [ ! -f "$BUNDLETOOL" ]; then
    echo "bundletool not found ($BUNDLETOOL): skipping the Play bundle"
    ls -la "$OUT/Ringdown.apk"
    exit 0
fi

echo "[6/8] linking resources for the bundle"
"$AAPT2" link -o "$AAB/proto.apk" "${LINK_ARGS[@]}" --proto-format --stable-ids "$OUT/ids.txt" "$OUT/res/res.zip"

echo "[7/8] building the bundle"
mkdir -p "$AAB/base/manifest" "$AAB/base/dex"
(cd "$AAB/base" && unzip -q ../proto.apk)
mv "$AAB/base/AndroidManifest.xml" "$AAB/base/manifest/"
cp "$OUT/dex/classes.dex" "$AAB/base/dex/"
(cd "$AAB/base" && zip -q -r -D ../base.zip manifest dex res assets resources.pb)
java -jar "$BUNDLETOOL" build-bundle --modules="$AAB/base.zip" --output="$AAB/unsigned.aab" --overwrite 2>&1 | quiet

echo "[8/8] signing the bundle"
jarsigner -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env KEY_PASS -keypass:env KEY_PASS \
    -sigalg SHA256withRSA -digestalg SHA-256 -signedjar "$AAB/Ringdown.aab" "$AAB/unsigned.aab" "$ALIAS" 2>&1 \
    | quiet | grep -v -E "^jar signed\.$|^Warning: ?$|self-signed|^$" || true
jarsigner -verify "$AAB/Ringdown.aab" 2>&1 | grep -q "jar verified" || { echo "bundle signature check failed"; exit 1; }
java -jar "$BUNDLETOOL" validate --bundle="$AAB/Ringdown.aab" > /dev/null 2>&1 || { echo "bundletool validate failed"; exit 1; }
rm -rf "$AAB/base" "$AAB/base.zip" "$AAB/proto.apk" "$AAB/unsigned.aab"

ls -la "$OUT/Ringdown.apk" "$AAB/Ringdown.aab"
