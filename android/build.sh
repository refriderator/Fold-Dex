#!/bin/bash
# Full build from source. Needs Android build-tools 35 + platform android-34 + JDK.
# Set BT and AJ to your SDK paths. Put deck.jks (the signing key, NOT in this repo) in android/.
set -e
cd "$(dirname "$0")"
BT=${BT:-$ANDROID_HOME/build-tools/35.0.0}; AJ=${AJ:-$ANDROID_HOME/platforms/android-34/android.jar}
rm -rf build && mkdir -p build/classes assets && cp ../ui/index.html assets/index.html
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I $AJ --manifest AndroidManifest.xml -A assets -o build/base.apk build/res.zip --auto-add-overlay
javac -nowarn -source 8 -target 8 -cp $AJ -d build/classes $(find src -name '*.java')
$BT/d8 --min-api 28 --lib $AJ --output build $(find build/classes -name '*.class')
cd build && cp base.apk unsigned.apk && zip -q -j unsigned.apk classes.dex && cd ..
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks deck.jks --out FoldDeck.apk build/aligned.apk
ls -la FoldDeck.apk
