#!/usr/bin/env bash
# Builds and signs the APK with only the Android SDK command-line tools (no Gradle).
#   ANDROID_HOME                          Android SDK (default ~/Library/Android/sdk)
#   JAVA_HOME                             JDK 17+ (default: whatever is on PATH)
#   KEYSTORE / KEYSTORE_PASS / KEY_ALIAS  signing key; without them a throwaway debug key is used
set -euo pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
bt=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
platform=$(ls -d "$sdk"/platforms/android-* | sort -V | tail -1)/android.jar
bin=${JAVA_HOME:+$JAVA_HOME/bin/}
rm -rf build && mkdir -p build/classes build/dex
"${bin}javac" -source 8 -target 8 -nowarn -encoding UTF-8 -classpath "$platform" -d build/classes src/io/github/lemonteaau/zuiiconhelper/*.java
"$bt/d8" --lib "$platform" --output build/dex $(find build/classes -name '*.class')
"$bt/aapt" package -f -M AndroidManifest.xml -I "$platform" -S res -F build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ -z "${KEYSTORE:-}" ]]; then
  KEYSTORE=build/debug.jks KEYSTORE_PASS=android KEY_ALIAS=debug
  "${bin}keytool" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias debug -keyalg RSA -validity 3650 -dname 'CN=Debug' >/dev/null 2>&1
fi
"$bt/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "${KEY_ALIAS:-release}" --out build/zui-icon-helper.apk build/aligned.apk
"$bt/apksigner" verify build/zui-icon-helper.apk
echo "Built app/build/zui-icon-helper.apk"
