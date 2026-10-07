#!/bin/bash
# GitHub Actions 构建脚本：使用 runner 自带的 Android SDK
set -e
cd "$(dirname "$0")"
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
AJ="$ANDROID_HOME/platforms/android-34/android.jar"
[ -f "$AJ" ] || AJ=$(ls "$ANDROID_HOME"/platforms/*/android.jar | sort -V | tail -1)
echo "build-tools: $BT  android.jar: $AJ"
rm -rf build && mkdir -p build/gen build/classes build/dex
"$BT/aapt" package -f -m -J build/gen -M app/AndroidManifest.xml -S app/res -I "$AJ"
javac --release 11 -encoding UTF-8 -nowarn -cp "$AJ" -d build/classes $(find app/src build/gen -name "*.java")
"$BT/d8" --release --min-api 24 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt" package -f -M app/AndroidManifest.xml -S app/res -A app/assets -I "$AJ" -0 woff2 -0 png -F build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
KS=${KEYSTORE_FILE:-build/ci.keystore}
if [ ! -f "$KS" ]; then
  echo "::warning::未设置签名 Secrets，使用临时签名（无法覆盖安装正式版）"
  KEYSTORE_PASSWORD=android KEY_ALIAS=ci KEY_PASSWORD=android
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias ci -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CI" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KEYSTORE_PASSWORD" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASSWORD" --out build/ClashPanel.apk build/aligned.apk
"$BT/apksigner" verify build/ClashPanel.apk
