#!/usr/bin/env bash
set -euo pipefail

VARIANT="${1:-stable}"

case "$VARIANT" in
  stable)
    MANIFEST="AndroidManifest.xml"
    RES_DIR="res"
    WORK="build/stable"
    OUT_APK="tinychess.apk"
    DEX_ZIP_OPTS=(-9)
    ;;
  nightly)
    MANIFEST="nightly/AndroidManifest.xml"
    RES_DIR="nightly/res"
    WORK="build/nightly"
    OUT_APK="tinychess-nightly.apk"
    # useEmbeddedDex requires classes.dex to be stored uncompressed and aligned.
    DEX_ZIP_OPTS=(-0)
    ;;
  *)
    echo "usage: $0 [stable|nightly]" >&2
    exit 2
    ;;
esac

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -n 1)
PLATFORM=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -n 1)
ANDROID_JAR="$PLATFORM/android.jar"

rm -rf "$WORK" && mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/res"

# 1. Compile vector icon XMLs into binary flat resources
"$BUILD_TOOLS/aapt2" compile --dir "$RES_DIR" -o "$WORK/res/resources.zip"

# 2. Link AndroidManifest.xml + compiled vector icon into base APK
"$BUILD_TOOLS/aapt2" link \
  -o "$WORK/unaligned.apk" \
  --manifest "$MANIFEST" \
  -R "$WORK/res/resources.zip" \
  -I "$ANDROID_JAR"

# 3. Compile Java source directly against android.jar
javac -source 1.8 -target 1.8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" \
  -d "$WORK/classes" \
  src/com/tiny/chess/MainActivity.java

# 4. Convert .class -> classes.dex
"$BUILD_TOOLS/d8" --release \
  --lib "$ANDROID_JAR" \
  --output "$WORK/dex" \
  "$WORK/classes/com/tiny/chess/"*.class

# 5. Pack classes.dex into the APK (stored uncompressed for nightly)
zip -j "${DEX_ZIP_OPTS[@]}" "$WORK/unaligned.apk" "$WORK/dex/classes.dex"

# 6. Align and sign
[ -f debug.keystore ] || keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TinyChess"

"$BUILD_TOOLS/zipalign" -f 4 "$WORK/unaligned.apk" "$WORK/aligned.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks debug.keystore --ks-pass pass:android \
  --out "$OUT_APK" "$WORK/aligned.apk"

ls -lh "$OUT_APK"
