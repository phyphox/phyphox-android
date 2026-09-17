#!/bin/sh
# The instrumented T1 suites - view and graph snapshots, view behavior, chrome, lifecycle,
# accessibility and the language rendering - as CI runs them and as a developer can run them
# against a booted emulator:
#
#     tools/t1_instrumented.sh .                 # everything except the language sweep
#     tools/t1_instrumented.sh . translations    # only the language sweep
#     tools/t1_instrumented.sh . all             # both, in one run
#     tools/t1_instrumented.sh . chrome tablet   # the same, labelled "tablet" in the summary
#
# The first argument is the app checkout (CI runs from the workspace that holds it next to
# phyphox-docs). The third is an optional label for the run's summary tables (see below). The
# second selects which suites to run, and exists because the language sweep is
# 222 of the 492 seconds the whole set took: walking every enabled language means restarting the
# app once per language, which nothing else does. Splitting it into its own job takes it off the
# critical path of the T1 workflow, at the price of a second emulator and a second install.
#
# The permission suite runs twice on purpose, once with the microphone revoked and once granted:
# "pm grant" and "pm revoke" restart the app, and the instrumentation lives in that process, so a
# test cannot change the permission itself (see PermissionFlowTest). It belongs to the chrome
# selection; the language sweep needs no permission of its own.
set -u

APP=${1:-.}
SUITES=${2:-chrome}
LABEL=${3:-}
RUNNER=de.rwth_aachen.phyphox.test/androidx.test.runner.AndroidJUnitRunner
PACKAGE=de.rwth_aachen.phyphox
LANGUAGE_SUITE=de.rwth_aachen.phyphox.TranslationsUiTest
PERMISSION_SUITE=de.rwth_aachen.phyphox.PermissionFlowTest

case $SUITES in
    chrome|translations|all) ;;
    *) echo "Unknown suite selection \"$SUITES\" (chrome, translations or all)" >&2; exit 2 ;;
esac

cd "$APP" && ./gradlew installRegularDebug installRegularDebugAndroidTest || exit 1
cd - > /dev/null || exit 1

# The fixtures that arrive "from elsewhere" are served over http from this host, which the
# emulator reaches at 10.0.2.2 (switch-bypassed-ui opens one that way).
python3 -m http.server 8115 --directory phyphox-docs/fixtures/views > /dev/null 2>&1 &
FIXTURE_SERVER=$!

rc=0

# The raw protocol (-r) goes to a file, and the verdict is read from the file afterwards; what
# reaches the job log is one line per test from tools/instrument_progress.py, which also appends a
# table per test class to the step summary when GitHub provides one. The table's title carries
# the label so that the phone and the tablet run read apart on the summary page.
# The language sweep can be narrowed with the convention both platforms share (test-matrix row
# translations-ui). Android has no environment inside the app process, so whichever of the two
# variables is set here is forwarded as an instrumentation argument under the same name:
#
#     PHYPHOX_TEST_LANGUAGE_SHARD=1/2 tools/t1_instrumented.sh . translations
#     PHYPHOX_TEST_LANGUAGES=de,fr    tools/t1_instrumented.sh . translations
LANGUAGE_ARGS=""
if [ -n "${PHYPHOX_TEST_LANGUAGE_SHARD:-}" ]; then
    LANGUAGE_ARGS="$LANGUAGE_ARGS -e PHYPHOX_TEST_LANGUAGE_SHARD $PHYPHOX_TEST_LANGUAGE_SHARD"
fi
if [ -n "${PHYPHOX_TEST_LANGUAGES:-}" ]; then
    LANGUAGE_ARGS="$LANGUAGE_ARGS -e PHYPHOX_TEST_LANGUAGES $PHYPHOX_TEST_LANGUAGES"
fi

run_suite() {
    log=$1
    title=$2
    shift 2
    adb shell am instrument -r -w "$@" $LANGUAGE_ARGS $RUNNER 2>&1 \
        | python3 "$APP/tools/instrument_progress.py" "$title${LABEL:+, $LABEL}" "$log"
    grep -q "^OK" "$log" || rc=1
}

if [ "$SUITES" != "translations" ]; then
    adb shell pm revoke $PACKAGE android.permission.RECORD_AUDIO
    run_suite instrumented-$SUITES-permission-denied.txt "Permission flow, microphone denied" -e class $PERMISSION_SUITE
fi

adb shell pm grant $PACKAGE android.permission.RECORD_AUDIO
adb shell pm grant $PACKAGE android.permission.CAMERA
adb shell pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION

LANGUAGE_TITLE="Languages${PHYPHOX_TEST_LANGUAGE_SHARD:+, shard $PHYPHOX_TEST_LANGUAGE_SHARD}"
case $SUITES in
    chrome)       run_suite instrumented-$SUITES-suites.txt "Chrome and view suites" -e notClass "$PERMISSION_SUITE,$LANGUAGE_SUITE" ;;
    translations) run_suite instrumented-$SUITES-suites.txt "$LANGUAGE_TITLE" -e class "$LANGUAGE_SUITE" ;;
    all)          run_suite instrumented-$SUITES-suites.txt "Chrome, view and language suites" -e notClass "$PERMISSION_SUITE" ;;
esac

kill $FIXTURE_SERVER 2>/dev/null

adb logcat -d > logcat-instrumented-$SUITES.txt 2>&1 || true
adb logcat -d -s phyphoxA11y > instrumented-$SUITES-accessibility-findings.txt 2>&1 || true
adb logcat -d -s phyphoxI18n > instrumented-$SUITES-language-findings.txt 2>&1 || true

exit $rc
