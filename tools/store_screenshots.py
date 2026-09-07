#!/usr/bin/env python3
"""Capture the Google Play / F-Droid screenshots from the shipped experiments.

The scenes, the locale mapping and the composition of a scene's experiment file
live in the sibling phyphox-docs checkout, shared with iOS. The recorded data is
baked into the file as init values: the remote interface's banner must not show.

    tools/store_screenshots.py --avd phyphox-shot-phone --form-factor phone
    tools/store_screenshots.py --avd phyphox-shot-phone --form-factor phone \
        --languages en,de --scenes accelerometer,strobe      # a quick look
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
# Play's locale names; tools/store_release.py copies the English plates into the fastlane tree
SHOTS = os.path.normpath(os.path.join(REPO, "..", "screenshots", "android"))
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
EMULATOR = os.path.join(SDK, "emulator", "emulator")
PACKAGE = "de.rwth_aachen.phyphox"
SIGNED_APK = "screenshots-signed.apk"   # what --build leaves and a later run picks up
PORT = 8099

# fastlane directory and required screen (Play: each side 320-3840 px, longer side at most twice the shorter)
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

    def screencap(self, path, tries=3):
        """Capture; adb exec-out returns what it managed, so only a whole PNG is written."""
        for attempt in range(tries):
            r = subprocess.run(
                [ADB, "-s", self.serial, "exec-out", "screencap", "-p"],
                capture_output=True)
            data = r.stdout
            if data[:8] == b"\x89PNG\r\n\x1a\n" and data[-8:-4] == b"IEND":
                with open(path, "wb") as f:
                    f.write(data)
                return
            if attempt + 1 < tries:
                print(f"    incomplete capture ({len(data)} bytes), retrying")
                time.sleep(3)
        raise RuntimeError(
            f"could not capture {os.path.basename(path)}: adb returned "
            f"{len(data)} bytes that are not a complete PNG. The device is "
            f"probably gone - check `adb devices`.")

    def dump_ui(self, path):
        """The accessibility tree, to find controls by resource id in every language."""
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
    """Start the emulator and wait for it."""
    before = set(_serials())
    env = dict(os.environ, DISPLAY=os.environ.get("DISPLAY", ":0"))
    subprocess.Popen(
        [EMULATOR, "-avd", avd, "-no-window", "-no-audio", "-gpu", "host",   # swiftshader drops horizontal lines
         "-no-snapshot"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
    # only the AVD we started: another session's emulator may appear meanwhile
    deadline = time.time() + timeout
    serial = None
    while time.time() < deadline and not serial:
        for candidate in sorted(set(_serials()) - before):
            name = sh(ADB, "-s", candidate, "emu", "avd", "name",
                      check=False).splitlines()
            if name and name[0].strip() == avd:
                serial = candidate
                break
        if not serial:
            time.sleep(2)
    if not serial:
        raise RuntimeError(
            f"{avd} did not appear on adb within {timeout} s (devices seen: "
            f"{', '.join(sorted(set(_serials()) - before)) or 'none new'})")
    d = Device(serial)
    while time.time() < deadline:
        if d.shell("getprop", "sys.boot_completed", check=False).strip() == "1":
            time.sleep(6)          # the launcher is still settling
            return d
        time.sleep(3)
    raise RuntimeError(f"{avd} booted no further than the splash screen")


def build_apk():
    """Assemble regularRelease from THIS checkout and sign it with the debug keystore (release has no signingConfig)."""
    print("  building regularRelease")
    sh(os.path.join(REPO, "gradlew"), "-p", REPO, "assembleRegularRelease")
    out = os.path.join(REPO, "app", "build", "outputs", "apk", "regular",
                       "release")
    unsigned = [os.path.join(out, f) for f in os.listdir(out)
                if f.endswith(".apk")]
    if not unsigned:
        raise RuntimeError(f"gradle produced no APK in {out}")
    apk = unsigned[0]
    signed = os.path.join(out, SIGNED_APK)
    shutil.copyfile(apk, signed)
    tools = sorted(os.listdir(os.path.join(SDK, "build-tools")))
    apksigner = os.path.join(SDK, "build-tools", tools[-1], "apksigner")
    ks = os.path.expanduser("~/.android/debug.keystore")
    sh(apksigner, "sign", "--ks", ks, "--ks-pass", "pass:android",
       "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey",
       signed)
    print(f"  signed {os.path.basename(signed)} with the debug keystore")
    return signed


def last_built_apk():
    """The APK when neither --apk nor --build was given: what --build left behind."""
    out = os.path.join(REPO, "app", "build", "outputs", "apk", "regular",
                       "release")
    for name in (SIGNED_APK, "app-regular-release.apk"):
        candidate = os.path.join(out, name)
        if os.path.exists(candidate):
            return candidate
    return None


def has_seams(apk):
    """Whether this build carries debug.phyphox.view (the audio-spectrum scene needs it)."""
    import zipfile
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                if b"debug.phyphox.view" in z.read(name):
                    return True
    return False


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

    # the damage warning shows until "do not show again" is ticked
    d.shell("am", "force-stop", PACKAGE)
    d.shell("am", "start", "-n", f"{PACKAGE}/.ExperimentList.ExperimentListActivity")
    time.sleep(9)
    dump = os.path.join(DOCS, "build", "_ui.xml")
    os.makedirs(os.path.dirname(dump), exist_ok=True)
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
        print("  damage warning not shown (already dismissed on this install)")

    # the start hint counts down UI taps only; R.id.playhint sits on the play button,
    # which itself is not in the accessibility tree
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
    return installed_stamp(d)


def installed_stamp(d):
    """When the package was last installed; another session's install replaces ours silently."""
    for line in d.shell("dumpsys", "package", PACKAGE, check=False).splitlines():
        if "lastUpdateTime=" in line:
            return line.strip()
    return None


def check_still_ours(d, stamp):
    now = installed_stamp(d)
    if stamp and now and now != stamp:
        raise RuntimeError(
            f"the app was reinstalled while this run was capturing "
            f"({stamp} -> {now}). Another session on this machine probably "
            f"installed over it, so the plates from here on would show a "
            f"different build. Pin ANDROID_SERIAL on the other side and re-run.")


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
    """Per-app language (API 33+); needs no permission and works on a release build."""
    d.shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", tag)
    time.sleep(1)


def set_theme(d, light):
    """Set dark mode by walking the app's own settings (SettingsActivity is not exported), in English."""
    set_language(d, "en")
    d.shell("am", "force-stop", PACKAGE)
    d.shell("am", "start", "-n", f"{PACKAGE}/.ExperimentList.ExperimentListActivity")
    time.sleep(7)
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
    """The node with exactly this label; relies on set_theme having chosen English."""
    for item in _labelled(d, tmp):
        if item[0] == label:
            return item
    raise RuntimeError(
        f"no control labelled {label!r} on this screen. Either the app is not "
        f"in English, or the settings were rearranged.")


def play_locales(row):
    """The Play listing locales one app language feeds. Usually one."""
    a = row["android"]
    return a if isinstance(a, list) else [a]


def view_index(composer, scene):
    """The view to open, resolved from the scene's view label so an inserted view errors."""
    if scene.get("kind") == "collection":
        return 0
    from lxml import etree
    root = etree.parse(os.path.join(COLLECTION, scene["experiment"])).getroot()
    return composer.resolve_view(root, scene["view"])


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except AttributeError:
        pass

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--avd", help="boot this AVD; omit to use --serial")
    ap.add_argument("--serial", help="an already running device")
    ap.add_argument("--form-factor", required=True, choices=sorted(FORM_FACTORS))
    ap.add_argument("--apk", help="the APK to photograph; default: whatever "
                                  "--build produced, else the release output")
    ap.add_argument("--build", action="store_true",
                    help="assemble regularRelease from the CURRENT checkout "
                         "and sign it for local use")
    ap.add_argument("--languages", help="comma separated app language tags "
                                        "(default: all of them)")
    ap.add_argument("--scenes", help="comma separated scene ids (default: all)")
    ap.add_argument("--out", default=SHOTS)
    ap.add_argument("--keep-emulator", action="store_true")
    args = ap.parse_args()

    sys.path.insert(0, os.path.join(DOCS, "tools", "screenshots"))
    import compose as composer
    import yaml

    scenes = composer.load_scenes()
    with open(os.path.join(DOCS, "screenshots", "locales.yml")) as f:
        locales = yaml.safe_load(f)
    # `order` stays the FULL scene list: it numbers the files; --scenes only narrows the capture
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

    apk = args.apk or (build_apk() if args.build else last_built_apk())
    if not apk or not os.path.exists(apk):
        raise SystemExit("no installable APK in the release output - pass "
                         "--apk, or --build to make one")
    if not has_seams(apk):
        raise SystemExit(
            f"{os.path.basename(apk)} has no debug.phyphox.view: it predates "
            f"the automation seams, so the audio-spectrum scene cannot reach "
            f"its History tab. Build from a checkout that includes them.")

    d = boot(args.avd) if args.avd else Device(args.serial)
    want = FORM_FACTORS[args.form_factor][1]
    if d.size() != want:
        raise SystemExit(f"{args.form_factor} must be {want[0]}x{want[1]}, "
                         f"this device is {d.size()[0]}x{d.size()[1]} - Google "
                         f"Play rejects a longer side more than twice the "
                         f"shorter, so the AVD profile matters")
    try:
        stamp = prepare(d, apk)

        # grouped by theme: two settings walks instead of one per language; the preference outlives the run
        groups = [(False, [s for s in capture if scenes[s].get("theme") != "light"]),
                  (True, [s for s in capture if scenes[s].get("theme") == "light"])]
        total = 0
        for light, group in groups:
            if not group:
                continue
            set_theme(d, light)
            for row in rows:
                check_still_ours(d, stamp)
                set_language(d, row["app"])
                # one app language may feed several listings: captured once, copied to each
                targets = [os.path.join(args.out, name, "images",
                                        FORM_FACTORS[args.form_factor][0])
                           for name in play_locales(row)]
                for t in targets:
                    os.makedirs(t, exist_ok=True)
                target = targets[0]
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
                    for extra in targets[1:]:
                        shutil.copyfile(shot, os.path.join(extra,
                                                           os.path.basename(shot)))
                    total += len(targets)
                    print(f"  {'/'.join(play_locales(row)):11s} "
                          f"{'light' if light else 'dark ':5s} {n:02d}-{sid}")
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
