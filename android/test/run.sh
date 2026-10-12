#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
JSON_JAR=${JSON_JAR:-/usr/share/java/com.android.json.jar}
mkdir -p android/build/core-tests
base=android/src/com/cyberyichen/bloub
javac -encoding UTF-8 -cp "$JSON_JAR" -d android/build/core-tests "$base"/{AccessCode,ImageTransform,VideoRange,SettingsSpec,PresenceTimeline,MotionWindow,MotionDetector,UploadSigner,StorageDestination,ExposureMeter,PhotoAnnotation,WebDavFolders,CameraSchedule,SeatLearner}.java android/test/com/cyberyichen/bloub/*.java
for name in SeatCoreTest StorageCoreTest Release091CoreTest Release092CoreTest CameraScheduleTest; do
 java -cp "android/build/core-tests:$JSON_JAR" "com.cyberyichen.bloub.$name"
done
