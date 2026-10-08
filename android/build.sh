#!/usr/bin/env bash
# Builds Nest.apk without Gradle or the Android SDK:
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

rm -rf "$OUT"/*; mkdir -p "$OUT/classes"
# Optional: bake your Worker URL into the app so nobody has to type it:  NEST_SERVER=https://nest-sync.you.workers.dev ./build.sh
cp -r app/src/main/assets "$OUT/assets"
[ -z "${PLAY:-}" ] || sed -i 's#const SMS_INBOX = true;#const SMS_INBOX = false;#' "$OUT/assets/www/index.html"
[ -z "${NEST_SERVER:-}" ] || sed -i "s#const DEFAULT_SERVER = \"\";#const DEFAULT_SERVER = \"${NEST_SERVER%/}\";#" "$OUT/assets/www/index.html"
# PLAY=1 -> Play-policy build: no READ_SMS permission, inbox scan hidden (users paste/share bank texts instead)
cp app/src/main/AndroidManifest.xml "$OUT/AndroidManifest.xml"
[ -z "${PLAY:-}" ] || { sed -i '/READ_SMS/d;/RECEIVE_SMS/d;/POST_NOTIFICATIONS/d' "$OUT/AndroidManifest.xml"; python3 - "$OUT/AndroidManifest.xml" <<'PY'
import re,sys
p=sys.argv[1]; s=open(p).read(); s=re.sub(r'\s*<!-- sideload build only.*?</receiver>','',s,flags=re.S); open(p,'w').write(s)
PY
}
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$TOOLS/aapt2" link -o "$OUT/base.apk" -I "$FW" --manifest "$OUT/AndroidManifest.xml" \
  -A "$OUT/assets" --min-sdk-version 26 --target-sdk-version 35 --version-code 16 --version-name 2.0.0 "$OUT/res.zip"
javac -nowarn --release 8 -cp "$FW" -d "$OUT/classes" $(find app/src/main/java -name '*.java') 2>&1 | grep -v '^Note:\|warning' || true
java -cp "$TOOLS/dalvik-dx-16.0.1.jar" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"
python3 tools/align.py "$OUT/base.apk" "$OUT/classes.dex" "$OUT/unsigned.apk"

KS=tools/nest-release.p12
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass nestnest -keypass nestnest -alias nest \
  -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Nest, O=Nest, C=IN"
javac -cp "$TOOLS/apksig-2.3.0.jar" -d "$OUT" tools/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp "$OUT:$TOOLS/apksig-2.3.0.jar" Sign "$OUT/unsigned.apk" "$OUT/Nest.apk" "$KS" nestnest nest
mkdir -p ../dist && OUTAPK=${PLAY:+Nest-play.apk}; OUTAPK=${OUTAPK:-Nest.apk}; cp "$OUT/Nest.apk" "../dist/$OUTAPK"
"$TOOLS/aapt2" dump badging "../dist/$OUTAPK" | head -12
ls -la "../dist/$OUTAPK"
