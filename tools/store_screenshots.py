#!/usr/bin/env python3
"""Capture the Google Play / F-Droid screenshots, from the shipped experiments.

The Android half of the store release system described in the working root's
STORE-RELEASE-PLAN.md. The shared half - which six scenes, what data they show,
which locale maps to which store directory, and how a scene's experiment file is
built - lives in the sibling phyphox-docs checkout, because iOS needs exactly the
same answers.

    tools/store_screenshots.py --avd phyphox-shot-phone --form-factor phone
    tools/store_screenshots.py --avd phyphox-shot-phone --form-factor phone \
        --languages en,de --scenes accelerometer,strobe      # a quick look

What it does per device, in this order, and why each step is there:

1.  Boots the AVD with **-gpu host**. Not optional: the software rasteriser
    drops axis-aligned horizontal lines, which empties the stroboscope's square
    wave (plan, S9).
2.  Installs, then grants CAMERA and RECORD_AUDIO. A system permission dialog
    is not something the app's autoConfirm may dismiss, and the camera scene
    stops dead behind one.
3.  Dismisses the damage warning and warms away the start-hint balloon. Both are
    SharedPreferences, so they survive until the app's data is wiped - which is
    exactly what an install with a different signature does, hence: every run.
4.  Puts the system UI into demo mode, so the status bar reads 9:41 with a full
    battery instead of the emulator's own clock and charging bolt.
5.  For each language and scene: sets the app's language, composes the scene's
    experiment file from the CURRENT shipped collection, serves it over an adb
    reverse tunnel, opens it by URL, captures, and repairs the emulator's black
    graph margins.

The device never runs the remote interface: its banner would be in every
screenshot. That is why the recorded data is baked into the experiment file as
init values rather than pushed in with /set.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
DOCS = os.path.normpath(os.path.join(REPO, "..", "phyphox-docs"))
COLLECTION = os.path.join(REPO, "app", "src", "main", "assets", "experiments")
METADATA = os.path.join(REPO, "fastlane", "metadata", "android")
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
EMULATOR = os.path.join(SDK, "emulator", "emulator")
PACKAGE = "de.rwth_aachen.phyphox"
PORT = 8099

# Where each form factor's images belong in the fastlane tree, and the screen
# the AVD must report. The sizes are Google Play's: each side 320-3840 px and
# the longer side at most twice the shorter, which rules out every stock phone
# profile (a medium phone is 1080x2400, i.e. 2.22:1).
FORM_FACTORS = {
    "phone": ("phoneScreenshots", (1080, 1920)),
    "sevenInch": ("sevenInchScreenshots", (1200, 1920)),
    "tenInch": ("tenInchScreenshots", (1600, 2560)),
}


def sh(*args, check=True, quiet=False):
    r = subprocess.run(args, capture_output=True, text=True)
    if check and r.returncode:
        raise RuntimeError(f"{' '.join(args)}\n{r.stdout}{r.stderr}")
    if not quiet and r.stdout.strip():
        pass
    return r.stdout


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, **kw):
        return sh(ADB, "-s", self.serial, *args, **kw)

    def shell(self, *args, **kw):
        return self.adb("shell", *args, **kw)

    def screencap(self, path):
        r = subprocess.run([ADB, "-s", self.serial, "exec-out", "screencap", "-p"],
                           capture_output=True)
        with open(path, "wb") as f:
            f.write(r.stdout)

    def dump_ui(self, path):
        """The accessibility tree. Used to find controls by resource id rather
        than by coordinates - ids are the same in all 23 languages."""
        self.shell("uiautomator", "dump", "/sdcard/ui.xml", check=False)
        with open(path, "w", encoding="utf-8", errors="replace") as f:
            f.write(self.shell("cat", "/sdcard/ui.xml", check=False))

    def find(self, dump, resource_id):
        m = re.search(r'resource-id="[^"]*' + re.escape(resource_id)
                      + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump)
        if not m:
            return None
        l, t, r, b = map(int, m.groups())
        return (l + r) // 2, (t + b) // 2

    def tap(self, x, y, settle=1.0):
        self.shell("input", "tap", str(x), str(y))
        time.sleep(settle)

    def size(self):
        m = re.search(r"(\d+)x(\d+)", self.shell("wm", "size"))
        return (int(m.group(1)), int(m.group(2))) if m else None


def boot(avd, timeout=300):
    """Start the emulator and wait for it. -gpu host is the point (S9)."""
    before = set(_serials())
    env = dict(os.environ, DISPLAY=os.environ.get("DISPLAY", ":0"))
    subprocess.Popen(
        [EMULATOR, "-avd", avd, "-no-window", "-no-audio", "-gpu", "host",
         "-no-snapshot"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
    deadline = time.time() + timeout
    serial = None
    while time.time() < deadline:
        new = set(_serials()) - before
        if new:
            serial = sorted(new)[0]
            break
        time.sleep(2)
    if not serial:
        raise RuntimeError(f"{avd} did not appear on adb within {timeout} s")
    d = Device(serial)
    while time.time() < deadline:
        if d.shell("getprop", "sys.boot_completed", check=False).strip() == "1":
            time.sleep(6)          # the launcher is still settling
            return d
        time.sleep(3)
    raise RuntimeError(f"{avd} booted no further than the splash screen")


def _serials():
    out = sh(ADB, "devices")
    return [l.split()[0] for l in out.splitlines()[1:]
            if l.strip() and l.split()[-1] == "device"]


def serve(directory):
    handler = partial(SimpleHTTPRequestHandler, directory=directory)
    httpd = ThreadingHTTPServer(("127.0.0.1", PORT), handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd


def prepare(d, apk):
    """Everything that has to be true before the first capture."""
    print(f"  installing {os.path.basename(apk)}")
    out = d.adb("install", "-r", apk, check=False)
    if "Success" not in out:
        # a signature change (a store build being replaced) needs the old one gone
        d.adb("uninstall", PACKAGE, check=False)
        d.adb("install", "-r", apk)
    for perm in ("CAMERA", "RECORD_AUDIO"):
        d.shell("pm", "grant", PACKAGE, f"android.permission.{perm}", check=False)
    d.shell("setprop", "debug.phyphox.autoConfirm", "1")
    d.adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")

    # The damage warning shows on every start until "do not show again" is
    # ticked; the controls are found by id because this also runs in 23 languages.
    d.shell("am", "force-stop", PACKAGE)
    d.shell("am", "start", "-n", f"{PACKAGE}/.ExperimentList.ExperimentListActivity")
    time.sleep(9)
    dump = os.path.join(os.path.dirname(apk), "_ui.xml")
    d.dump_ui(dump)
    with open(dump, encoding="utf-8", errors="replace") as f:
        tree = f.read()
    box = d.find(tree, "id/donotshowagain")
    ok = d.find(tree, "android:id/button1")
    if box and ok:
        print("  dismissing the damage warning")
        d.tap(*box)
        d.tap(*ok, settle=2)
        d.dump_ui(dump)
        with open(dump, encoding="utf-8", errors="replace") as f:
            if d.find(f.read(), "id/donotshowagain"):
                raise RuntimeError(
                    "the damage warning is still up after being dismissed - "
                    "every screenshot would have it in the middle")
    else:
        # Already ticked away on this install, which is the normal case for a
        # device that has been used before. Said out loud because a silent skip
        # and a mistap look identical afterwards.
        print("  damage warning not shown (already dismissed on this install)")

    # The start hint does not time out and is only counted down by the UI
    # handler, so a remote start would not do: six taps on play/pause, which
    # share a position, are three action_play events and retire it for good.
    #
    # The anchor is R.id.playhint - the hint animation's own action view, which
    # exists exactly while the hint is showing and sits where the play button
    # is. `action_play` is not in the accessibility tree at all, and a fraction
    # of the screen is a phone constant that misses on a tablet.
    d.shell("am", "start", "-a", "android.intent.action.VIEW",
            "-d", "phyphox://asset=accelerometer.phyphox", check=False)
    time.sleep(9)
    d.dump_ui(dump)
    with open(dump, encoding="utf-8", errors="replace") as f:
        play = d.find(f.read(), "id/playhint")
    if play:
        print("  warming away the start hint")
        for _ in range(6):
            d.tap(*play, settle=1.3)
        d.dump_ui(dump)
        with open(dump, encoding="utf-8", errors="replace") as f:
            if d.find(f.read(), "id/playhint"):
                raise RuntimeError(
                    "the start hint is still showing after six taps - it would "
                    "be in every experiment screenshot")
    else:
        print("  start hint not shown (already warmed on this install)")
    d.shell("am", "force-stop", PACKAGE)

    demo_mode(d)


def demo_mode(d, on=True):
    """A store status bar: 9:41, full battery, wifi, nothing else."""
    if not on:
        d.shell("am", "broadcast", "-a", "com.android.systemui.demo",
                "-e", "command", "exit", check=False)
        return
    d.shell("settings", "put", "global", "sysui_demo_allowed", "1")
    b = ["am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command"]
    d.shell(*b, "enter", check=False)
    d.shell(*b, "clock", "-e", "hhmm", "0941", check=False)
    d.shell(*b, "battery", "-e", "level", "100", "-e", "plugged", "false", check=False)
    d.shell(*b, "network", "-e", "mobile", "hide", check=False)
    d.shell(*b, "network", "-e", "wifi", "show", "-e", "level", "4",
            "-e", "fully", "true", check=False)
    d.shell(*b, "notifications", "-e", "visible", "false", check=False)


def set_language(d, tag):
    """Per-app language (API 33+). No permission, no reboot, and it works on a
    release build - which is why no screenshot flavor is needed any more."""
    d.shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", tag)
    time.sleep(1)


def set_theme(d, light):
    """Set the app's dark-mode preference by walking its own settings.

    The theme is an app preference (default: permanently dark) that the shell
    cannot reach on a production build, and SettingsActivity is not exported,
    so the app's menu is the only way in.

    **The app is put into English first.** Everything on that path is localized
    - the menu entries, the preference titles, the three choices - and matching
    any of it by text is what broke the first version of this, in German. In
    English the labels are known, so the walk can assert what it is tapping
    instead of counting rows and hoping. The caller sets the real language
    afterwards; the theme is global and outlives it.
    """
    set_language(d, "en")
    d.shell("am", "force-stop", PACKAGE)
    d.shell("am", "start", "-n", f"{PACKAGE}/.ExperimentList.ExperimentListActivity")
    time.sleep(7)
    # The button that opens the menu is R.id.credits in the collection layout.
    # Found by id, not by a fraction of the screen: the first version tapped
    # at 93% width and a fixed y, which is a phone's toolbar and misses a
    # tablet's - the 7-inch profile is a different density, so the bar sits
    # somewhere else entirely.
    tmp0 = os.path.join(DOCS, "build", "_menu.xml")
    os.makedirs(os.path.dirname(tmp0), exist_ok=True)
    d.dump_ui(tmp0)
    with open(tmp0, encoding="utf-8", errors="replace") as f:
        menu_button = d.find(f.read(), "id/credits")
    if not menu_button:
        raise RuntimeError(
            "the collection's menu button (R.id.credits) is not on screen - "
            "the app may not have finished starting")
    d.tap(*menu_button, settle=3)
    tmp = tmp0
    _tap_item(d, _labelled_exact(d, tmp, "Settings"), settle=5)
    _tap_item(d, _labelled_exact(d, tmp, "Dark mode"), settle=3)
    _tap_item(d, _labelled_exact(d, tmp, "Off" if light else "On (Default)"),
              settle=4)
    d.shell("am", "force-stop", PACKAGE)


def _labelled(d, tmp):
    """Every node with a visible label, as (text, x, y), in document order."""
    d.dump_ui(tmp)
    with open(tmp, encoding="utf-8", errors="replace") as f:
        tree = f.read()
    out = []
    for m in re.finditer(
            r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tree):
        t, l, tp, r, b = m.group(1), *map(int, m.groups()[1:])
        out.append((t, (l + r) // 2, (tp + b) // 2))
    return out


def _tap_item(d, item, settle=1.0):
    d.tap(item[1], item[2], settle=settle)


def _labelled_exact(d, tmp, label):
    """The node with exactly this label. Safe only because set_theme forces the
    app into English first - see its docstring."""
    for item in _labelled(d, tmp):
        if item[0] == label:
            return item
    raise RuntimeError(
        f"no control labelled {label!r} on this screen. Either the app is not "
        f"in English, or the settings were rearranged.")


def view_index(composer, scene):
    """Which view the app should open on. Resolved from the scene's view LABEL
    against the shipped file, so an inserted view is an error rather than a
    silently wrong screenshot."""
    if scene.get("kind") == "collection":
        return 0
    from lxml import etree
    root = etree.parse(os.path.join(COLLECTION, scene["experiment"])).getroot()
    return composer.resolve_view(root, scene["view"])


def main():
    # A full run is 414 captures over a couple of hours; with stdout redirected
    # to a log, block buffering would show nothing until it ended.
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except AttributeError:
        pass

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--avd", help="boot this AVD; omit to use --serial")
    ap.add_argument("--serial", help="an already running device")
    ap.add_argument("--form-factor", required=True, choices=sorted(FORM_FACTORS))
    ap.add_argument("--apk", default=os.path.join(
        REPO, "app", "build", "outputs", "apk", "regular", "release",
        "app-regular-release.apk"))
    ap.add_argument("--languages", help="comma separated app language tags "
                                        "(default: all of them)")
    ap.add_argument("--scenes", help="comma separated scene ids (default: all)")
    ap.add_argument("--out", default=METADATA)
    ap.add_argument("--keep-emulator", action="store_true")
    args = ap.parse_args()

    sys.path.insert(0, os.path.join(DOCS, "tools", "screenshots"))
    import compose as composer
    import fix_emulator_graphs as fixer
    import yaml
    from PIL import Image

    scenes = composer.load_scenes()
    with open(os.path.join(DOCS, "screenshots", "locales.yml")) as f:
        locales = yaml.safe_load(f)
    # `order` stays the FULL scene list: it is what numbers the files, and the
    # store shows them in that order. --scenes narrows what gets captured, not
    # what things are called - otherwise a one-scene re-run writes
    # 01-tone-generator.png beside a stale 06-tone-generator.png.
    order = [s["id"] for s in yaml.safe_load(
        open(os.path.join(DOCS, "screenshots", "scenes.yml")))["scenes"]]
    capture = order
    if args.scenes:
        asked = args.scenes.split(",")
        unknown = [s for s in asked if s not in order]
        if unknown:
            sys.exit(f"unknown scene(s): {', '.join(unknown)}")
        capture = [s for s in order if s in asked]
    wanted = args.languages.split(",") if args.languages else None
    rows = [l for l in locales["locales"]
            if (not wanted or l["app"] in wanted)
            and not (l["app"] == "sr-Latn"
                     and locales.get("serbian_screenshots") != "sr-Latn")]

    build = os.path.join(DOCS, "build", "screenshots")
    shutil.rmtree(build, ignore_errors=True)
    os.makedirs(build, exist_ok=True)
    for sid in capture:
        scene = scenes[sid]
        if scene.get("kind") == "collection":
            continue
        blob, _view, touched = composer.compose(scene, COLLECTION)
        composer.check(os.path.join(COLLECTION, scene["experiment"]), blob,
                       touched, False)
        with open(os.path.join(build, f"{sid}.phyphox"), "wb") as f:
            f.write(blob)
    httpd = serve(build)

    d = boot(args.avd) if args.avd else Device(args.serial)
    want = FORM_FACTORS[args.form_factor][1]
    if d.size() != want:
        raise SystemExit(f"{args.form_factor} must be {want[0]}x{want[1]}, "
                         f"this device is {d.size()[0]}x{d.size()[1]} - Google "
                         f"Play rejects a longer side more than twice the "
                         f"shorter, so the AVD profile matters")
    try:
        prepare(d, args.apk)

        # Grouped by THEME, not by language. One scene is shot in light mode
        # and the rest in dark; walking the settings once per language would be
        # 23 walks through a localized menu, and each one is a chance to tap the
        # wrong row. Two walks per device instead, with the language set inside.
        # The theme is deliberately set even for the first group rather than
        # assumed: it is a stored preference, so whatever the last run left is
        # what the first scene would otherwise be shot in.
        groups = [(False, [s for s in capture if scenes[s].get("theme") != "light"]),
                  (True, [s for s in capture if scenes[s].get("theme") == "light"])]
        total = 0
        for light, group in groups:
            if not group:
                continue
            set_theme(d, light)
            for row in rows:
                set_language(d, row["app"])
                target = os.path.join(args.out, row["android"], "images",
                                      FORM_FACTORS[args.form_factor][0])
                os.makedirs(target, exist_ok=True)
                for sid in group:
                    scene = scenes[sid]
                    n = order.index(sid) + 1        # the store's display order
                    d.shell("setprop", "debug.phyphox.view",
                            str(view_index(composer, scene)))
                    d.shell("am", "force-stop", PACKAGE)
                    time.sleep(1)
                    if scene.get("kind") == "collection":
                        d.shell("am", "start", "-n",
                                f"{PACKAGE}/.ExperimentList.ExperimentListActivity")
                    else:
                        d.shell("am", "start", "-a", "android.intent.action.VIEW",
                                "-d", f"phyphox://127.0.0.1:{PORT}/{sid}.phyphox")
                    time.sleep(scene.get("settle", 16))
                    shot = os.path.join(target, f"{n:02d}-{sid}.png")
                    d.screencap(shot)
                    dump = os.path.join(build, "_ui.xml")
                    d.dump_ui(dump)
                    with open(dump, encoding="utf-8", errors="replace") as f:
                        frames = fixer.graph_frames(f.read())
                    if frames:
                        im = Image.open(shot).convert("RGB")
                        im, _ = fixer.repair(im, frames)
                        im.save(shot)
                    total += 1
                    print(f"  {row['android']:6s} {'light' if light else 'dark ':5s} "
                          f"{n:02d}-{sid}")
        print(f"{total} screenshot(s) into {args.out}")
    finally:
        # leave the app as a user would find it
        try:
            set_theme(d, light=False)
        except Exception as e:
            print(f"  (could not restore the dark theme: {e})")
        demo_mode(d, on=False)
        httpd.shutdown()
        if args.avd and not args.keep_emulator:
            d.adb("emu", "kill", check=False)


if __name__ == "__main__":
    main()
