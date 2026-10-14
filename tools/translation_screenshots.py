#!/usr/bin/env python3
"""Screenshots that give Weblate's translators context, and the strings each one shows.

Runs TranslationContextScreenshotTest on a device (the emulator, not a personal phone), pulls the
shots and resolves the texts each one shows to Weblate identifiers: app strings to their key in
values/strings.xml (component "android"), experiment strings to their msgid in
../phyphox-translation/experiments/en.po (component "experiments"). The result is a directory of
PNGs plus index.json, which phyphox-translation/python/upload_screenshots.py sends to Weblate.
Nothing here is committed; rerunning reproduces it.

    tools/translation_screenshots.py --install                 # build, install, capture everything
    tools/translation_screenshots.py --only experiments --experiments gps.phyphox,light.phyphox
    tools/translation_screenshots.py --resolve-only            # redo the lookup on the last pull
"""

import argparse
import difflib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
STRINGS = os.path.join(REPO, "app", "src", "main", "res", "values", "strings.xml")
EN_PO = os.path.normpath(os.path.join(REPO, "..", "phyphox-translation", "experiments", "en.po"))
DEFAULT_OUT = os.path.normpath(os.path.join(REPO, "..", "screenshots", "translation-context", "android"))
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
PACKAGE = "de.rwth_aachen.phyphox"
TEST_CLASS = PACKAGE + ".TranslationContextScreenshotTest"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
DEVICE_DIR = f"/sdcard/Android/data/{PACKAGE}/files/translation-screenshots"
METHODS = ["collection", "collectionItems", "settings", "experimentChrome", "experiments"]
PERMISSIONS = ["CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
               "POST_NOTIFICATIONS", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"]
KEYED = ("_title", "_category", "_description")
MAX_SHOTS = 4   # per string: "Start" or "s" would otherwise list every experiment


def run(*args, check=True):
    r = subprocess.run(args, capture_output=True, text=True)
    if check and r.returncode:
        sys.exit(f"{' '.join(args)} failed:\n{r.stdout}{r.stderr}")
    return r.stdout


def norm(text):
    return re.sub(r"\s+", " ", text or "").strip()


# ------------------------------------------------------------------ the two string tables

def android_unescape(text):
    text = text.strip()
    if len(text) >= 2 and text[0] == text[-1] == '"':
        text = text[1:-1]
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), text)


def unused_keys():
    """Keys whose translator note says the app does not show them (a same text elsewhere is not them)."""
    text = open(STRINGS, encoding="utf-8").read()
    return set(re.findall(r'<!-- Not shown by the Android app[^\n]*-->\s*<string name="([^"]+)"', text))


def load_android():
    """key -> (normalized English text, compiled template or None) for every translatable string."""
    strings, unused = {}, unused_keys()
    for e in ET.parse(STRINGS).getroot():
        if e.tag != "string" or e.get("translatable") == "false" or e.get("name") in unused:
            continue
        value = norm(android_unescape("".join(e.itertext())))
        template = None
        if re.search(r"%(\d+\$)?[sd]", value):
            parts = re.split(r"%(?:\d+\$)?[sd]", value.replace("%%", "%"))
            literal = sum(len(p) for p in parts)
            if literal >= 6:
                pattern = r"(.+?)".join(re.escape(p) for p in parts)
                #a short frame such as "%1$s: %2$s to %3$s" fits any prose unless it is the whole text
                template = re.compile(pattern if literal >= 20 else "^(?=.{0,80}$)" + pattern + "$", re.IGNORECASE | re.S)
        strings[e.get("name")] = (value, template)
    return strings


def load_po():
    """List of (msgid, English text, set of experiment files) from experiments/en.po."""
    entries, refs, current, field = [], set(), {}, None

    def flush():
        if current.get("msgid"):
            entries.append((current["msgid"], current.get("msgstr") or current["msgid"], set(refs)))

    for line in open(EN_PO, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            flush()
            refs, current, field = set(), {}, None
        elif line.startswith("#:"):
            refs.update(line[2:].split())
        elif line.startswith("#"):
            continue
        else:
            m = re.match(r'(msgid|msgstr) (".*")$', line)
            if m:
                field = m.group(1)
                current[field] = json.loads(m.group(2))
            elif line.startswith('"') and field:
                current[field] += json.loads(line)
    flush()
    return entries


# ------------------------------------------------------------------------- resolving

def visible_texts(xml_path):
    """Texts and content descriptions of the app's own nodes in a UiAutomator window dump."""
    texts = []
    for node in ET.parse(xml_path).iter("node"):
        if node.get("package") != PACKAGE:
            continue
        for attr in ("text", "content-desc"):
            value = norm(node.get(attr))
            if value:
                texts.append({"text": value, "source": attr, "bounds": node.get("bounds"),
                              "id": node.get("resource-id", "").split("/")[-1]})
    return texts


def resolve(texts, android, po, experiment):
    keys, msgids = set(), set()
    if experiment:
        candidates = [e for e in po if experiment in e[2]]
    else:
        candidates = [e for e in po if e[0].endswith(KEYED)]
    for item in texts:
        text, source = item["text"], item["source"]
        folded = text.casefold()
        canvas = source.startswith(("graph.", "value."))
        found = experiment_matches(folded, source, item.get("id", ""), candidates, experiment)
        msgids.update(found)
        #an experiment's own text ("Settings" as a tab, "Light" as a title) is not the app string that reads the same
        own = bool(found) and (experiment is not None or item.get("id") in ("expTitle", "")) and source != "content-desc"
        for key, (value, template) in android.items():
            if not value:
                continue
            if key.startswith("common_"):
                #symbols are a letter or two: only where the app draws exactly that, never inside prose
                if text == value and (canvas or len(value) >= 3):
                    keys.add(key)
                continue
            if canvas and source != "graph.status":
                continue
            #categoryRawSensor has to equal the category's translation: both are worth showing
            if (folded == value.casefold() and (not own or key.startswith("category"))) \
                    or (len(value) >= 10 and value.casefold() in folded) \
                    or (template is not None and template.search(text)):
                keys.add(key)
    return sorted(keys), sorted(msgids)


def experiment_matches(folded, source, node_id, candidates, experiment):
    if source in ("graph.unit", "value.unit", "graph.status"):
        return set()
    found = set()
    for msgid, english, _ in candidates:
        value = norm(english).casefold()
        if not value:
            continue
        #in the collection only the list entries are experiment texts, not a checkbox that reads "Light"
        if experiment is None and not (node_id in ("expTitle", "expInfo") or (msgid.endswith("_category") and not node_id)):
            continue
        if folded == value \
                or (len(folded) >= 20 and folded in value) \
                or (len(value) >= 20 and value in folded) \
                or similar(folded, value):
            found.add(msgid)
    return found


def similar(a, b):
    """A long text whose English was polished in en.po after the app's experiments submodule was
    last bumped still shows the same string."""
    if min(len(a), len(b)) < 40:
        return False
    m = difflib.SequenceMatcher(None, a, b, autojunk=False)
    return m.real_quick_ratio() >= 0.9 and m.quick_ratio() >= 0.9 and m.ratio() >= 0.9


def resolve_all(out):
    android, po = load_android(), load_po()
    raw = os.path.join(out, "raw")
    index = []
    for root, _, files in os.walk(raw):
        for f in sorted(files):
            if not f.endswith(".json"):
                continue
            meta = json.load(open(os.path.join(root, f), encoding="utf-8"))
            base = os.path.join(root, f[:-5])
            if not os.path.exists(base + ".png") or not os.path.exists(base + ".xml"):
                continue
            texts = visible_texts(base + ".xml") + [dict(t) for t in meta.get("canvas", [])]
            keys, msgids = resolve(texts, android, po, meta.get("experiment"))
            image = meta["name"] + ".png"
            os.makedirs(os.path.dirname(os.path.join(out, image)), exist_ok=True)
            shutil.copyfile(base + ".png", os.path.join(out, image))
            index.append({"name": meta["name"], "image": image, "experiment": meta.get("experiment"),
                          "android": keys, "experiments": msgids, "texts": texts})
    index.sort(key=lambda e: e["name"])
    cap(index, "android", lambda e: not e["name"].startswith("app/"))
    cap(index, "experiments", lambda e: e["name"].startswith("app/"))
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"screenshots": index}, f, ensure_ascii=False, indent=1)

    shown = {k for e in index for k in e["android"]}
    print(f"{len(index)} screenshots; {len(shown)} of {len(android)} app strings and "
          f"{len({m for e in index for m in e['experiments']})} of {len(po)} experiment strings linked")
    return index


def cap(index, component, later):
    """Keep a string on its MAX_SHOTS most telling screenshots: the preferred kind first, then the
    ones showing the fewest strings (a dialog about it rather than a screen that merely contains it)."""
    shots = {}
    for e in index:
        for key in e[component]:
            shots.setdefault(key, []).append(e)
    for key, entries in shots.items():
        entries.sort(key=lambda e: (later(e), len(e["android"]) + len(e["experiments"]), e["name"]))
        for e in entries[MAX_SHOTS:]:
            e[component].remove(key)


# ------------------------------------------------------------------------- capturing

def capture(serial, methods, experiments, out):
    adb = [ADB, "-s", serial]
    #A fresh collection: what earlier test runs saved into it would end up on the screenshots
    run(*adb, "shell", "pm", "clear", PACKAGE)
    for permission in PERMISSIONS:
        run(*adb, "shell", "pm", "grant", PACKAGE, "android.permission." + permission, check=False)
    run(*adb, "shell", "rm", "-rf", DEVICE_DIR)
    #Through the system rather than from the test: an app locale left by an earlier run wins otherwise
    run(*adb, "shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", "en")
    for method in methods:
        args = [*adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", f"{TEST_CLASS}#{method}",
                "-e", "translationScreenshots", "1"]
        if experiments and method == "experiments":
            args += ["-e", "experiments", experiments]
        print(f"capturing {method} …", flush=True)
        result = run(*args, RUNNER, check=False)
        failure = re.search(r"INSTRUMENTATION_STATUS: stack=(.*?)INSTRUMENTATION_STATUS", result, re.S)
        if failure or "FAILURES!!!" in result or "INSTRUMENTATION_FAILED" in result:
            print(f"  {method} did not finish:\n{failure.group(1) if failure else result[-2000:]}")
    run(*adb, "shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", "")
    raw = os.path.join(out, "raw")
    shutil.rmtree(raw, ignore_errors=True)
    os.makedirs(out, exist_ok=True)
    run(*adb, "pull", DEVICE_DIR, raw)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    p.add_argument("--out", default=DEFAULT_OUT)
    p.add_argument("--install", action="store_true", help="build and install app and test APK first")
    p.add_argument("--only", help="comma-separated subset of " + ",".join(METHODS))
    p.add_argument("--experiments", help="comma-separated experiment files (default: all shipped)")
    p.add_argument("--resolve-only", action="store_true", help="only redo the lookup on the last pull")
    a = p.parse_args()

    if not os.path.exists(EN_PO):
        sys.exit(f"{EN_PO} is missing: phyphox-translation must be checked out next to this repository")
    if not a.resolve_only:
        if a.install:
            subprocess.run(["./gradlew", "installRegularDebug", "installRegularDebugAndroidTest"],
                           cwd=REPO, check=True, env={**os.environ, "ANDROID_SERIAL": a.serial})
        methods = a.only.split(",") if a.only else METHODS
        capture(a.serial, methods, a.experiments, a.out)
    resolve_all(a.out)
    print("written to", a.out)


if __name__ == "__main__":
    main()
