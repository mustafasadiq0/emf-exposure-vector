#!/bin/bash
# Builds a signed Android App Bundle (.aab) for Google Play.
# Requires: ./build.sh run first (produces build/apk/classes.dex), aapt2, bundletool jar.
# Usage: ./build-aab.sh /path/to/bundletool.jar
set -e
cd "$(dirname "$0")"
BT="${1:?path to bundletool jar}"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
W=build-aab
rm -rf $W && mkdir -p $W/compiled $W/module/manifest $W/module/dex $W/unz
aapt2 compile --dir res -o $W/compiled/res.zip
aapt2 link --proto-format -o $W/base.apk -I $AJ --manifest AndroidManifest.xml \
  --min-sdk-version 23 --target-sdk-version 36 --version-code 3 --version-name 1.2 $W/compiled/res.zip
(cd $W/unz && unzip -q ../base.apk)
mv $W/unz/AndroidManifest.xml $W/module/manifest/
mv $W/unz/res $W/unz/resources.pb $W/module/
cp build/apk/classes.dex $W/module/dex/
(cd $W/module && zip -qr ../base.zip .)
java -jar "$BT" build-bundle --modules=$W/base.zip --output=$W/unsigned.aab --overwrite
cp $W/unsigned.aab EMF-Exposure-Vector.aab
jarsigner -keystore "$EMF_KEYSTORE" -storepass:env EMF_STORE_PASSWORD -keypass:env EMF_KEY_PASSWORD \
  -sigalg SHA256withRSA -digestalg SHA-256 EMF-Exposure-Vector.aab "${EMF_KEY_ALIAS:-emf}" >/dev/null
jarsigner -verify EMF-Exposure-Vector.aab | tail -1
ls -la EMF-Exposure-Vector.aab
