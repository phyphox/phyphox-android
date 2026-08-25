#!/bin/sh
# The T1 experiments-end-to-end run, as CI performs it and as a developer can perform it
# locally against a booted emulator:
#
#     DOCS=../phyphox-docs tools/t1_experiments_ci.sh --serial emulator-5554 --subset pendulum
#
# DOCS points at the phyphox-docs checkout (default: ./phyphox-docs, the CI layout); every
# argument is passed on to the driver.
#
# This is a script rather than a few lines in the workflow because the emulator action runs its
# "script:" one line per shell - a for loop, or an exit code kept in a variable, does not
# survive that (it fails with "Syntax error: end of file unexpected").
set -u

DOCS=${DOCS:-phyphox-docs}

# An unattended run cannot answer a runtime permission dialog, and an experiment that asks for
# one never finishes loading - the audio, camera, GPS and depth experiments would all be
# reported as "remote API not reachable" instead of being tested.
for permission in RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION CAMERA; do
    adb shell pm grant de.rwth_aachen.phyphox "android.permission.$permission" || true
done

python3 "$DOCS/tools/t1_experiments.py" --platform android --emulator --require-rows "$@"
rc=$?

# Keep the device log whatever the driver said: an app that died and an emulator that merely
# answered too slowly look the same from the host without it.
adb logcat -d > logcat-experiments.txt 2>&1 || true
adb logcat -d -b crash > logcat-experiments-crash.txt 2>&1 || true

exit $rc
