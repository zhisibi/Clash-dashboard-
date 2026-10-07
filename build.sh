#!/bin/bash
set -e
cd "$(dirname "$0")"
AJ=tools/android-34/android.jar
rm -rf build && mkdir -p build/gen build/classes build/dex
aapt package -f -m -J build/gen -M app/AndroidManifest.xml -S app/res -I $AJ
javac --release 11 -encoding UTF-8 -nowarn -cp $AJ -d build/classes $(find app/src build/gen -name "*.java")
java -cp tools/r8.jar com.android.tools.r8.D8 --release --min-api 24 --lib $AJ --output build/dex $(find build/classes -name '*.class')
aapt package -f -M app/AndroidManifest.xml -S app/res -A app/assets -I $AJ -0 woff2 -0 png -F build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
[ -f tools/release.keystore ] || keytool -genkeypair -keystore tools/release.keystore -storepass zashpanel -keypass zashpanel -alias zash -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Clash Panel" >/dev/null 2>&1
apksigner sign --ks tools/release.keystore --ks-pass pass:zashpanel --key-pass pass:zashpanel --out build/ClashPanel.apk build/aligned.apk
apksigner verify --print-certs build/ClashPanel.apk | head -2
ls -la build/ClashPanel.apk
