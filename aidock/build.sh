#!/usr/bin/env bash
# Builds AIDock.apk without Gradle or the Android SDK (same pipeline as ../android/build.sh):
#   aapt2 (from Apktool) -> javac -> dx -> align -> apksig (v2)
# Needs: JDK 17+, python3, curl. Downloads its tools from Maven Central on first run.
set -euo pipefail
cd "$(dirname "$0")"
TOOLS=${NEST_TOOLS:-$HOME/.cache/nest-tools}; MC=https://repo.maven.apache.org/maven2
OUT=build; mkdir -p "$TOOLS" "$OUT"
get() { [ -s "$TOOLS/$2" ] || curl -sfL --retry 3 -o "$TOOLS/$2" "$MC/$1/$2"; }
get org/apktool/apktool-cli/3.0.3 apktool-cli-3.0.3.jar
get com/jakewharton/android/repackaged/dalvik-dx/16.0.1 dalvik-dx-16.0.1.jar
get com/android/tools/build/apksig/2.3.0 apksig-2.3.0.jar
get org/robolectric/android-all/15-robolectric-13954326 android-all-15-robolectric-13954326.jar
FW="$TOOLS/android-all-15-robolectric-13954326.jar"
[ -x "$TOOLS/aapt2" ] || { unzip -p "$TOOLS/apktool-cli-3.0.3.jar" prebuilt/linux/aapt2 > "$TOOLS/aapt2"; chmod +x "$TOOLS/aapt2"; }

rm -rf "$OUT"/*; mkdir -p "$OUT/classes" "$OUT/gen"
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$TOOLS/aapt2" link -o "$OUT/base.apk" -I "$FW" --manifest app/src/main/AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 1.0.0 "$OUT/res.zip"
javac -Xlint:-options --release 8 -cp "$FW" -d "$OUT/classes" $(find app/src/main/java "$OUT/gen" -name '*.java')
java -cp "$TOOLS/dalvik-dx-16.0.1.jar" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"
python3 tools/align.py "$OUT/base.apk" "$OUT/classes.dex" "$OUT/unsigned.apk"

KS=tools/aidock-release.p12
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass aidockaidock -keypass aidockaidock \
  -alias aidock -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=AI Dock, O=AI Dock, C=IN"
javac -cp "$TOOLS/apksig-2.3.0.jar" -d "$OUT" tools/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp "$OUT:$TOOLS/apksig-2.3.0.jar" Sign "$OUT/unsigned.apk" "$OUT/AIDock.apk" "$KS" aidockaidock aidock
mkdir -p ../dist && cp "$OUT/AIDock.apk" ../dist/AIDock.apk
"$TOOLS/aapt2" dump badging ../dist/AIDock.apk | head -6
ls -la ../dist/AIDock.apk
