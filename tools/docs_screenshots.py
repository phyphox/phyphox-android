#!/usr/bin/env python3
"""Capture the view-element screenshots of the phyphox documentation.

The scenes - one small experiment per view element - are built by the sibling
phyphox-docs checkout (tools/screenshots/views.py). This script serves them to
an emulator, opens each one, crops the capture to the experiment's content
(R.id.experimentView) and writes one image per theme, straight into
phyphox-docs/docs/assets/screenshots/views/<id>-dark.png and <id>-light.png (.jpg where the scene is a photograph).
The device plumbing is shared with store_screenshots.py.

    tools/docs_screenshots.py --avd phyphox-shot-phone
    tools/docs_screenshots.py --serial emulator-5554 --scenes graph,grid --themes dark
"""

import argparse
import io
import json
import os
import re
import subprocess
import sys
import time

import store_screenshots as shots
from store_screenshots import DOCS, PACKAGE, PORT, REPO

OUT = os.path.join(DOCS, "docs", "assets", "screenshots", "views")
DEBUG_APK = os.path.join(REPO, "app", "build", "outputs", "apk", "regular",
                         "debug", "app-regular-debug.apk")


def build_scenes(ids):
    """Let phyphox-docs write the experiments; returns the directory and the scene list."""
    out = os.path.join(DOCS, "build", "docs-views")
    cmd = [sys.executable, os.path.join(DOCS, "tools", "screenshots", "views.py"),
           "--out", out, "--list"]
    if ids:
        cmd += ["--scenes", ids]
    listing = json.loads(subprocess.run(cmd, check=True, capture_output=True,
                                        text=True).stdout)
    if ids:
        unknown = set(ids.split(",")) - {s["id"] for s in listing}
        if unknown:
            sys.exit(f"unknown scene(s): {', '.join(sorted(unknown))}")
    return out, listing


def bounds(d, tmp, resource_id):
    d.dump_ui(tmp)
    with open(tmp, encoding="utf-8", errors="replace") as f:
        m = re.search(r'resource-id="[^"]*id/' + resource_id + r'"[^>]*bounds="'
                      r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', f.read())
    if not m:
        raise RuntimeError(f"no {resource_id} on screen - the experiment did "
                           f"not open, or the layout changed")
    return tuple(map(int, m.groups()))


def centre(box):
    return (box[0] + box[2]) // 2, (box[1] + box[3]) // 2


def pick(d, scene, tmp):
    """Maximize the graph, switch to pick mode, tap the scene's point."""
    d.tap(*centre(bounds(d, tmp, "graph_frame")), settle=3)
    d.tap(*centre(bounds(d, tmp, "graph_tools_pick")), settle=2)
    l, t, r, b = bounds(d, tmp, "graph_frame")
    fx, fy = scene["pick_at"]
    d.tap(int(l + fx * (r - l)), int(t + fy * (b - t)), settle=2)


def rotate(d, landscape):
    d.shell("settings", "put", "system", "accelerometer_rotation", "0")
    d.shell("settings", "put", "system", "user_rotation", "1" if landscape else "0")
    time.sleep(2)


def capture(d, scene, path, tmp):
    from PIL import Image
    landscape = scene["orientation"] == "landscape"
    rotate(d, landscape)
    d.shell("am", "force-stop", PACKAGE)
    time.sleep(1)
    d.shell("am", "start", "-a", "android.intent.action.VIEW",
            "-d", f"phyphox://127.0.0.1:{PORT}/{scene['file']}")
    time.sleep(12 if scene["id"] == "camera-gui" else 8)
    if scene.get("interaction") == "picker":
        pick(d, scene, tmp)
    box = bounds(d, tmp, "experimentView")
    raw = tmp + ".png"
    d.screencap(raw)
    img = Image.open(raw).convert("RGB").crop(box)
    if scene["format"] == "jpg":
        img.save(path, "JPEG", quality=85, optimize=True)
    else:
        img.save(path, "PNG", optimize=True)
    if landscape:
        rotate(d, False)


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except AttributeError:
        pass
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--avd", help="boot this AVD; omit to use --serial")
    ap.add_argument("--serial", help="an already running device")
    ap.add_argument("--apk", default=DEBUG_APK,
                    help="default: the regular debug build of this checkout")
    ap.add_argument("--scenes", help="comma separated scene ids (default: all)")
    ap.add_argument("--themes", default="dark,light")
    ap.add_argument("--keep-emulator", action="store_true")
    args = ap.parse_args()

    directory, scenes = build_scenes(args.scenes)
    if not os.path.exists(args.apk):
        sys.exit(f"{args.apk} does not exist - ./gradlew assembleRegularDebug")
    httpd = shots.serve(directory)
    d = shots.boot(args.avd) if args.avd else shots.Device(args.serial)
    os.makedirs(OUT, exist_ok=True)
    tmp = os.path.join(DOCS, "build", "_docs_ui.xml")
    try:
        stamp = shots.prepare(d, args.apk)
        shots.set_language(d, "en")
        for theme in args.themes.split(","):
            shots.set_theme(d, light=theme == "light")
            for scene in scenes:
                shots.check_still_ours(d, stamp)
                path = os.path.join(OUT, f"{scene['id']}-{theme}.{scene['format']}")
                capture(d, scene, path, tmp)
                print(f"  {theme:5s} {scene['id']}")
    finally:
        try:
            rotate(d, False)
            shots.set_theme(d, light=False)
        except Exception as e:
            print(f"  (could not restore the device: {e})")
        shots.demo_mode(d, on=False)
        d.shell("am", "force-stop", PACKAGE, check=False)
        httpd.shutdown()
        if args.avd and not args.keep_emulator:
            d.adb("emu", "kill", check=False)


if __name__ == "__main__":
    main()
