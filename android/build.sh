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
with zipfile.ZipFile('android/toolchain/platform-27_r03.zip') as sdk:
    Path('android/toolchain/android-27.jar').write_bytes(sdk.read('android-8.1.0/android.jar'))
for name in ['tensorflow-lite','tensorflow-lite-api']:
    with zipfile.ZipFile('android/toolchain/'+name+'-2.14.0.aar') as src:
        for entry in src.namelist():
            if entry=='classes.jar' or entry.startswith('jni/arm64-v8a/'):
                dst=Path('android/toolchain/'+name)/entry
                dst.parent.mkdir(parents=True,exist_ok=True)
                dst.write_bytes(src.read(entry))
for name in ['tasks-core','tasks-vision']:
    with zipfile.ZipFile('android/toolchain/'+name+'-0.10.26.aar') as aar:
        for entry in aar.namelist():
            if entry=='classes.jar':
                raw=aar.read(entry)
                if name=='tasks-core':
                    import io
                    with zipfile.ZipFile(io.BytesIO(raw)) as original,zipfile.ZipFile('android/toolchain/'+name+'.jar','w',zipfile.ZIP_DEFLATED) as out:
                        excluded=('TasksStatsLoggerFactory','TasksStatsProtoLogger','RemoteLoggingClient','LoggingClient')
                        for item in original.infolist():
                            if not any(item.filename.startswith('com/google/mediapipe/tasks/core/logging/'+x) for x in excluded):out.writestr(item,original.read(item.filename))
                else:Path('android/toolchain/'+name+'.jar').write_bytes(raw)
            elif entry.startswith('jni/arm64-v8a/') and entry.endswith('.so'):
                dst=Path('android/toolchain/'+name)/entry;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(aar.read(entry))
DEPS
MP_JARS=(android/toolchain/tasks-core.jar android/toolchain/tasks-vision.jar android/toolchain/guava-33.4.8-android.jar android/toolchain/failureaccess-1.0.3.jar android/toolchain/protobuf-java-4.26.1.jar android/toolchain/flogger-0.6.jar android/toolchain/flogger-system-backend-0.6.jar android/toolchain/annotation-1.1.0.jar)
MP_CLASSPATH=$(IFS=:; echo "${MP_JARS[*]}")
TF_CLASSPATH="android/toolchain/tensorflow-lite/classes.jar:android/toolchain/tensorflow-lite-api/classes.jar"
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
node node_modules/vite/bin/vite.js build --config vite.n5d.config.ts
mkdir -p android/build/classes android/build/dex android/toolchain android/dist
aapt package -f -0 task -0 tflite -M android/AndroidManifest.xml -S android/res -A dist-n5d -A android/assets -I android/toolchain/android-27.jar -F android/build/resources.apk
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR:$TF_CLASSPATH:$MP_CLASSPATH" -d android/build/classes $(find android/src -name "*.java")
"$JAVA_HOME/bin/jar" cf android/build/classes.jar -C android/build/classes .
"$JAVA_HOME/bin/java" -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 24 --lib android/toolchain/android-27.jar --output android/build/dex android/build/classes.jar android/toolchain/tensorflow-lite/classes.jar android/toolchain/tensorflow-lite-api/classes.jar "${MP_JARS[@]}"
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('android/build/resources.apk') as src, zipfile.ZipFile('android/build/unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():
        if entry.filename=='resources.arsc': entry.compress_type=zipfile.ZIP_STORED
        dst.writestr(entry,src.read(entry.filename))
    for dex in Path('android/build/dex').glob('*.dex'): dst.write(dex,dex.name)
    for name in ['tensorflow-lite','tasks-core','tasks-vision']:
        for lib in Path('android/toolchain/'+name+'/jni/arm64-v8a').glob('*.so'): dst.write(lib,'lib/arm64-v8a/'+lib.name)
PY
zipalign -f 4 android/build/unsigned.apk android/build/aligned.apk
if [[ ! -f android/toolchain/development.jks ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore android/toolchain/development.jks -storepass android -keypass android -alias bloub -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Bloub N5D Development,O=Local,C=CN"
fi
apksigner sign --ks android/toolchain/development.jks --ks-key-alias bloub --ks-pass pass:android --key-pass pass:android --out android/dist/Bloub-N5D-0.9.2.apk android/build/aligned.apk
zipalign -c 4 android/dist/Bloub-N5D-0.9.2.apk
apksigner verify --verbose android/dist/Bloub-N5D-0.9.2.apk
(cd android/dist && sha256sum Bloub-N5D-0.9.2.apk > SHA256SUMS.txt)
