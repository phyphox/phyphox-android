#!/usr/bin/env python3
"""Readable live log and a summary table for an instrumentation run.

    adb shell am instrument -r -w ... 2>&1 | python3 instrument_progress.py "<title>" <full-log>

Prints one line per test as it finishes (the raw protocol goes to <full-log>, which is what the
verdict is read from and what the job uploads) and appends a pass/fail table per test class to
$GITHUB_STEP_SUMMARY. Always exits 0: the verdict stays with the caller. Needs -r, the raw form:
without it am instrument prints only dots.
"""
import os
import re
import sys
import time
from collections import OrderedDict

STATUS = re.compile(r"^INSTRUMENTATION_STATUS: (\w+)=(.*)$")
CODE = re.compile(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)$")
PASSTHROUGH = re.compile(r"^(OK \(|FAILURES!!!|Tests run:|Time: |INSTRUMENTATION_RESULT: shortMsg=|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|INSTRUMENTATION_CODE:)")
# InstrumentationResultPrinter: 1 = started, 0 = ok, -1 = error, -2 = failure, -3 = ignored, -4 = assumption failed
FINISHED = {0: "passed", -1: "failed", -2: "failed", -3: "skipped", -4: "skipped"}


def main():
    title, log_path = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.dirname(os.path.abspath(log_path)) or ".", exist_ok=True)
    classes = OrderedDict()  # class -> {"passed": n, "failed": [names], "skipped": n, "seconds": s}
    started = time.time()
    bundle, key, test_started, current_class = {}, None, {}, None
    with open(log_path, "w") as log:
        for raw in sys.stdin:
            log.write(raw)
            line = raw.rstrip("\r\n")
            m = STATUS.match(line)
            if m:
                key = m.group(1)
                bundle[key] = m.group(2)
                continue
            m = CODE.match(line)
            if m:
                code = int(m.group(1))
                cls = bundle.get("class", "?").rsplit(".", 1)[-1]
                name = bundle.get("test", "?")
                if code == 1:
                    test_started[(cls, name)] = time.time()
                    if cls != current_class:
                        current_class = cls
                        print(cls, flush=True)
                elif code in FINISHED:
                    verdict = FINISHED[code]
                    seconds = time.time() - test_started.pop((cls, name), time.time())
                    stats = classes.setdefault(cls, {"passed": 0, "failed": [], "skipped": 0, "seconds": 0.0})
                    stats["seconds"] += seconds
                    if verdict == "passed":
                        stats["passed"] += 1
                    elif verdict == "failed":
                        stats["failed"].append(name)
                    else:
                        stats["skipped"] += 1
                    mark = {"passed": "ok  ", "failed": "FAIL", "skipped": "skip"}[verdict]
                    print(f"  {mark} {cls}.{name}  {seconds:6.1f} s", flush=True)
                    if verdict == "failed" and bundle.get("stack"):
                        print("       " + bundle["stack"].splitlines()[0][:300], flush=True)
                bundle, key = {}, None
                continue
            if line.startswith("INSTRUMENTATION_"):
                key = None
            elif key is not None:
                bundle[key] += "\n" + line  # continuation of a multi-line value (stack, stream)
                continue
            if PASSTHROUGH.match(line):
                print(line[:300], flush=True)
    summary(title, classes, time.time() - started)


def summary(title, classes, elapsed):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    failed = sum(len(s["failed"]) for s in classes.values())
    passed = sum(s["passed"] for s in classes.values())
    skipped = sum(s["skipped"] for s in classes.values())
    verdict = "no tests ran" if not classes else ("all passed" if not failed else f"{failed} failed")
    if skipped:
        verdict += f", {skipped} skipped"
    lines = [f"### {title}: {passed} passed, {verdict} ({elapsed / 60:.1f} min)", "",
             "| Test class | Passed | Failed | Time |", "|---|---:|---:|---:|"]
    for cls, s in classes.items():
        lines.append(f"| {cls} | {s['passed']} | {len(s['failed'])} | {s['seconds'] / 60:.1f} min |")
    for cls, s in classes.items():
        for name in s["failed"]:
            lines.append(f"- FAILED `{cls}.{name}`")
    with open(path, "a") as f:
        f.write("\n".join(lines) + "\n\n")


if __name__ == "__main__":
    main()
