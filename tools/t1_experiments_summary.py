#!/usr/bin/env python3
"""Summary table for a t1_experiments.py sweep, appended to $GITHUB_STEP_SUMMARY.

    t1_experiments_summary.py "<title>" <results.json>

Counts what the driver counts: an experiment has a finding when it was neither skipped nor declined
by the app and had an error, did not load, or produced a bad export (t1_experiments.py, main()).
"""
import json
import os
import sys


def main():
    title, path = sys.argv[1], sys.argv[2]
    out = os.environ.get("GITHUB_STEP_SUMMARY")
    if not out or not os.path.exists(path):
        return
    results = json.load(open(path))["results"]
    ok, findings, declined, skipped = [], [], [], []
    for r in results:
        if r.get("skipped"):
            skipped.append(r)
        elif r.get("not_loadable"):
            declined.append(r)
        elif r["errors"] or not r["loaded"] or any(p for p in r["exports"].values()):
            findings.append(r)
        else:
            ok.append(r)
    lines = [f"### {title}: {len(ok)} ok, {len(findings)} with findings, {len(declined)} declined by the app, {len(skipped)} skipped", "",
             "| Experiment | Result |", "|---|---|"]
    for r in findings:
        problems = list(r["errors"]) + [f"export {f}: {'; '.join(p)}" for f, p in r["exports"].items() if p]
        lines.append(f"| `{r['experiment']}` | {'; '.join(problems) or 'did not load'} |")
    for r in ok:
        lines.append(f"| `{r['experiment']}` | ok, {len(r['filled'])} buffer(s) filled |")
    with open(out, "a") as f:
        f.write("\n".join(lines) + "\n\n")


if __name__ == "__main__":
    main()
