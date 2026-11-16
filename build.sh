#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -n 1)
PLATFORM=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -n 1)
ANDROID_JAR="$PLATFORM/android.jar"

rm -rf build && mkdir -p build/classes build/dex build/res

# 1. Compile vector icon XMLs into binary flat resources
"$BUILD_TOOLS/aapt2" compile --dir res -o build/res/resources.zip

# 2. Link AndroidManifest.xml + compiled vector icon into base APK
"$BUILD_TOOLS/aapt2" link \
  -o build/unaligned.apk \
  --manifest AndroidManifest.xml \
  -R build/res/resources.zip \
  -I "$ANDROID_JAR"

# 3. Compile Java source directly against android.jar
javac -source 1.8 -target 1.8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" \
  -d build/classes \
  src/com/tiny/chess/MainActivity.java

# 4. Convert .class -> classes.dex
"$BUILD_TOOLS/d8" --release \
  --lib "$ANDROID_JAR" \
  --output build/dex \
  build/classes/com/tiny/chess/*.class

# 5. Pack classes.dex into the APK
zip -j -9 build/unaligned.apk build/dex/classes.dex

# 6. Align and sign
[ -f debug.keystore ] || keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TinyChess"

"$BUILD_TOOLS/zipalign" -f 4 build/unaligned.apk build/aligned.apk
"$BUILD_TOOLS/apksigner" sign \
  --ks debug.keystore --ks-pass pass:android \
  --out tinychess.apk build/aligned.apk

ls -lh tinychess.apk
