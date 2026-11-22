#!/bin/bash
set -e
cd "$(dirname "$0")"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/classes build/apk
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I $AJ -F build/apk/res.apk --min-sdk-version 23 --target-sdk-version 36
javac -nowarn -source 8 -target 8 -bootclasspath $AJ -encoding UTF-8 -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v "warning: \[options\]" || true
ls build/classes/org/emfvector/app/MainActivity.class >/dev/null
dalvik-exchange --dex --min-sdk-version=23 --output=build/apk/classes.dex build/classes
cp build/apk/res.apk build/unsigned.apk
(cd build/apk && zip -q -u ../unsigned.apk classes.dex)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
[ -n "$EMF_KEYSTORE" ] || { echo "Set EMF_KEYSTORE, EMF_STORE_PASSWORD, EMF_KEY_PASSWORD"; exit 1; }
apksigner sign --ks "$EMF_KEYSTORE" --ks-pass env:EMF_STORE_PASSWORD --key-pass env:EMF_KEY_PASSWORD --ks-key-alias "${EMF_KEY_ALIAS:-emf}" \
   --min-sdk-version 23 --out EMF-Exposure-Vector.apk build/aligned.apk
apksigner verify --verbose EMF-Exposure-Vector.apk | head -5
ls -la EMF-Exposure-Vector.apk
