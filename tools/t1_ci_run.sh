#!/bin/sh
# Run one T1 suite against the emulator and keep the device log whatever happens:
#
#     tools/t1_ci_run.sh <name> <command> [args...]
#
# The log lands in logcat-<name>.txt / logcat-<name>-crash.txt next to the working
# directory, for the workflow to upload. Without this the interesting failures - an app that
# stops answering mid-run, an emulator that goes offline - leave nothing behind to look at,
# because a failing command ends the emulator action's script right there.
set -u

name=$1
shift

"$@"
rc=$?

adb logcat -d > "logcat-$name.txt" 2>&1 || true
adb logcat -d -b crash > "logcat-$name-crash.txt" 2>&1 || true

exit $rc
