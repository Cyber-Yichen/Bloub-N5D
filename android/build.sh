#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}
R8_JAR=${R8_JAR:-android/toolchain/r8-8.3.37.jar}
test -f "$R8_JAR"
sha256sum --check android/dependencies.sha256
python3 - <<'DEPS'
import zipfile
from pathlib import Path
for name in ['tensorflow-lite','tensorflow-lite-api']:
    with zipfile.ZipFile('android/toolchain/'+name+'-2.14.0.aar') as src:
        for entry in src.namelist():
            if entry=='classes.jar' or entry.startswith('jni/arm64-v8a/'):
                dst=Path('android/toolchain/'+name)/entry
                dst.parent.mkdir(parents=True,exist_ok=True)
                dst.write_bytes(src.read(entry))
DEPS
TF_CLASSPATH="android/toolchain/tensorflow-lite/classes.jar:android/toolchain/tensorflow-lite-api/classes.jar"
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
node node_modules/vite/bin/vite.js build --config vite.n5d.config.ts
mkdir -p android/build/classes android/build/dex android/toolchain android/dist
aapt package -f -M android/AndroidManifest.xml -S android/res -A dist-n5d -A android/assets -I "$ANDROID_JAR" -F android/build/resources.apk
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR:$TF_CLASSPATH" -d android/build/classes android/src/com/cyberyichen/bloub/*.java
"$JAVA_HOME/bin/jar" cf android/build/classes.jar -C android/build/classes .
"$JAVA_HOME/bin/java" -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 23 --lib "$ANDROID_JAR" --output android/build/dex android/build/classes.jar android/toolchain/tensorflow-lite/classes.jar android/toolchain/tensorflow-lite-api/classes.jar
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('android/build/resources.apk') as src, zipfile.ZipFile('android/build/unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():
        if entry.filename=='resources.arsc': entry.compress_type=zipfile.ZIP_STORED
        dst.writestr(entry,src.read(entry.filename))
    for dex in Path('android/build/dex').glob('*.dex'): dst.write(dex,dex.name)
    for lib in Path('android/toolchain/tensorflow-lite/jni/arm64-v8a').glob('*.so'): dst.write(lib,'lib/arm64-v8a/'+lib.name)
PY
zipalign -f 4 android/build/unsigned.apk android/build/aligned.apk
if [[ ! -f android/toolchain/development.jks ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore android/toolchain/development.jks -storepass android -keypass android -alias bloub -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Bloub N5D Development,O=Local,C=CN"
fi
apksigner sign --ks android/toolchain/development.jks --ks-key-alias bloub --ks-pass pass:android --key-pass pass:android --out android/dist/Bloub-N5D-0.6.0.apk android/build/aligned.apk
zipalign -c 4 android/dist/Bloub-N5D-0.6.0.apk
apksigner verify --verbose android/dist/Bloub-N5D-0.6.0.apk
(cd android/dist && sha256sum Bloub-N5D-0.6.0.apk > SHA256SUMS.txt)
