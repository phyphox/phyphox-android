#!/bin/sh
# The instrumented T1 suites - view and graph snapshots, view behavior, chrome, lifecycle,
# accessibility and the language rendering - as CI runs them and as a developer can run them
# against a booted emulator:
#
#     tools/t1_instrumented.sh .
#
# The argument is the app checkout (CI runs from the workspace that holds it next to
# phyphox-docs).
#
# The permission suite runs twice on purpose, once with the microphone revoked and once granted:
# "pm grant" and "pm revoke" restart the app, and the instrumentation lives in that process, so a
# test cannot change the permission itself (see PermissionFlowTest).
set -u

APP=${1:-.}
RUNNER=de.rwth_aachen.phyphox.test/androidx.test.runner.AndroidJUnitRunner
PACKAGE=de.rwth_aachen.phyphox

cd "$APP" && ./gradlew installRegularDebug installRegularDebugAndroidTest || exit 1
cd - > /dev/null || exit 1

# The fixtures that arrive "from elsewhere" are served over http from this host, which the
# emulator reaches at 10.0.2.2 (switch-bypassed-ui opens one that way).
python3 -m http.server 8115 --directory phyphox-docs/fixtures/views > /dev/null 2>&1 &
FIXTURE_SERVER=$!

rc=0

adb shell pm revoke $PACKAGE android.permission.RECORD_AUDIO
adb shell am instrument -w -e class de.rwth_aachen.phyphox.PermissionFlowTest $RUNNER \
    | tee instrumented-permission-denied.txt | grep -q "^OK" || rc=1

adb shell pm grant $PACKAGE android.permission.RECORD_AUDIO
adb shell pm grant $PACKAGE android.permission.CAMERA
adb shell pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION

adb shell am instrument -w -e notClass de.rwth_aachen.phyphox.PermissionFlowTest $RUNNER \
    | tee instrumented-suites.txt | grep -q "^OK" || rc=1

kill $FIXTURE_SERVER 2>/dev/null

adb logcat -d > logcat-instrumented.txt 2>&1 || true
adb logcat -d -s phyphoxA11y > instrumented-accessibility-findings.txt 2>&1 || true
adb logcat -d -s phyphoxI18n > instrumented-language-findings.txt 2>&1 || true

exit $rc
