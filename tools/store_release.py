#!/usr/bin/env python3
"""The whole Android store release, in one command.

    tools/store_release.py                  # everything, asking once before publishing
    tools/store_release.py --skip-capture   # reuse the plates already captured
    tools/store_release.py --no-publish     # stop after the rehearsal

Drives tools/store_screenshots.py, phyphox-docs's verify.py and
tools/play_upload.py in order: preflight, release notes, one build photographed
on three AVDs, verify, the F-Droid metadata, a discarded Play rehearsal, then
the real upload after one question. It never touches git and never uploads the
app bundle. The iOS counterpart is phyphox-ios/tools/store_release.py.
"""

import argparse
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ROOT = os.path.normpath(os.path.join(REPO, ".."))
DOCS = os.path.join(ROOT, "phyphox-docs")
TRANSLATION = os.path.join(ROOT, "phyphox-translation")
SHOTS = os.path.join(ROOT, "screenshots", "android")
METADATA = os.path.join(REPO, "fastlane", "metadata", "android")

# form factor -> AVD. Play rejects a longer side more than twice the shorter, hence our own profiles
AVDS = [("phone", "phyphox-shot-phone"),
        ("sevenInch", "phyphox-shot-7in"),
        ("tenInch", "phyphox-shot-10in")]

FDROID_LOCALE = "en-US"          # as the capture names it (Play's spelling)
FDROID_DIR = "en"                # as F-Droid names it in the metadata tree
FDROID_KIND = "phoneScreenshots"


def step(n, what):
    print(f"\n{'=' * 72}\n{n}. {what}\n{'=' * 72}")


def run(*cmd, cwd=REPO):
    print("  $ " + " ".join(str(c) for c in cmd))
    return subprocess.call([str(c) for c in cmd], cwd=cwd)


def must(*cmd, cwd=REPO, why=""):
    rc = run(*cmd, cwd=cwd)
    if rc:
        raise SystemExit(f"\nstopped: {' '.join(str(c) for c in cmd)} "
                         f"exited {rc}{'. ' + why if why else ''}")


def ask(question):
    try:
        return input(f"\n{question} [y/N] ").strip().lower() in ("y", "yes")
    except EOFError:
        return False


def preflight(args):
    """Everything knowable before three hours of capturing starts."""
    problems = []
    for name, path in (("phyphox-docs", DOCS),
                       ("phyphox-translation", TRANSLATION)):
        if not os.path.isdir(path):
            problems.append(f"no {name} checkout at {path}")
    for mod in ("yaml", "polib"):
        try:
            __import__(mod)
        except ImportError:
            problems.append(f"python module {mod} is not installed")

    if not args.skip_capture:
        avd_home = os.path.expanduser("~/.android/avd")
        for factor, avd in AVDS:
            if not os.path.isfile(os.path.join(avd_home, f"{avd}.ini")):
                problems.append(f"no AVD {avd} for the {factor} plates")

    sys.path.insert(0, HERE)
    import play_upload
    if not args.no_publish and not play_upload.token(required=False):
        problems.append(
            "no Application Default Credentials - run:\n      "
            "gcloud auth application-default login "
            "--client-id-file=~/.config/phyphox-store/client.json \\\n        "
            "--scopes=https://www.googleapis.com/auth/androidpublisher,"
            "https://www.googleapis.com/auth/cloud-platform")

    if problems:
        raise SystemExit("cannot start:\n  - " + "\n  - ".join(problems))

    out = subprocess.run(["git", "-C", REPO, "status", "--short"],
                         capture_output=True, text=True).stdout.strip()
    print(f"  phyphox-android at "
          + subprocess.run(["git", "-C", REPO, "describe", "--always", "--dirty"],
                           capture_output=True, text=True).stdout.strip())
    if out:
        print("  !! the working tree is not clean. The screenshots are "
              "composed from the\n     experiment collection as it is HERE, so "
              "they will show whatever this tree\n     contains - check that "
              "it is what you are shipping.")


def have_plates():
    """{form factor kind: number of plates} already captured, per locale count."""
    counts = {}
    if not os.path.isdir(SHOTS):
        return counts
    for locale in os.listdir(SHOTS):
        images = os.path.join(SHOTS, locale, "images")
        if not os.path.isdir(images):
            continue
        for kind in os.listdir(images):
            n = len([f for f in os.listdir(os.path.join(images, kind))
                     if f.endswith(".png")])
            counts[kind] = counts.get(kind, 0) + n
    return counts


def capture(args):
    """One build, three form factors, into the working root's screenshots/."""
    # built by the first run (store_screenshots.py --build), reused by the other two
    apk = os.path.join(REPO, "app", "build", "outputs", "apk", "regular",
                       "release", "screenshots-signed.apk")
    for i, (factor, avd) in enumerate(AVDS):
        print(f"\n  --- {factor} ({avd}) ---")
        cmd = [sys.executable, os.path.join(HERE, "store_screenshots.py"),
               "--avd", avd, "--form-factor", factor, "--out", SHOTS]
        cmd += ["--build"] if i == 0 else ["--apk", apk]
        if args.languages:
            cmd += ["--languages", args.languages]
        if args.scenes:
            cmd += ["--scenes", args.scenes]
        must(*cmd, why="the plates for the other form factors were not taken")


def verify():
    """The mechanical sweep, per form factor. Fails the run; does not judge."""
    bad = []
    for factor, _avd in AVDS:
        print(f"\n  --- {factor} ---")
        if run(sys.executable,
               os.path.join(DOCS, "tools", "screenshots", "verify.py"),
               SHOTS, "--form-factor", factor):
            bad.append(factor)
    if bad:
        raise SystemExit(
            f"\nstopped: verify.py found problems in {', '.join(bad)}.\n"
            f"Re-capture the affected plates with tools/store_screenshots.py "
            f"--form-factor <x>\n--languages <...> --scenes <...>, then run "
            f"this again with --skip-capture.")
    print("\n  No broken or blank plates. It does not judge whether a "
          "screenshot is a GOOD one -\n  a plate on the wrong tab or a graph "
          "with unfortunate data still needs eyes.")


def copy_fdroid():
    """The listing text and the English phone plates into the metadata tree."""
    import play_upload
    play_upload.fdroid_text()

    src = os.path.join(SHOTS, FDROID_LOCALE, "images", FDROID_KIND)
    dst = os.path.join(METADATA, FDROID_DIR, "images", FDROID_KIND)
    if not os.path.isdir(src):
        raise SystemExit(f"no {FDROID_LOCALE} {FDROID_KIND} in {SHOTS}")
    # cleared first: F-Droid shows every plate it finds, dropped scenes included
    shutil.rmtree(dst, ignore_errors=True)
    os.makedirs(dst, exist_ok=True)
    names = sorted(f for f in os.listdir(src) if f.endswith(".png"))
    for name in names:
        shutil.copyfile(os.path.join(src, name), os.path.join(dst, name))
    print(f"  {len(names)} plate(s) into "
          f"{os.path.relpath(dst, os.path.dirname(REPO))}")
    for name in names:
        print(f"    {name}")


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except AttributeError:
        pass

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--skip-capture", action="store_true",
                    help="reuse the plates already in "
                         "../screenshots/android instead of taking new ones")
    ap.add_argument("--capture", action="store_true",
                    help="take new plates without asking, even though some "
                         "are already there")
    ap.add_argument("--languages", help="comma separated app language tags, "
                                        "passed to the capture (default: all)")
    ap.add_argument("--scenes", help="comma separated scene ids, passed to the "
                                     "capture (default: all)")
    ap.add_argument("--no-publish", action="store_true",
                    help="stop after the rehearsal, without asking")
    ap.add_argument("--version-code", type=int,
                    help="the versionCode the release notes are filed under, "
                         "when it is not the one in build.gradle")
    args = ap.parse_args()

    step(1, "Preflight")
    preflight(args)

    step(2, "Release notes")
    sys.path.insert(0, HERE)
    import changelog
    name, code = changelog.android_version(REPO)
    changelog.ensure(args.version_code or code, name, REPO)

    step(3, "Screenshots - one build, three form factors")
    counts = have_plates()
    if args.skip_capture:
        if not counts:
            raise SystemExit(f"--skip-capture, but there are no plates in "
                             f"{SHOTS}")
        print("  reusing " + ", ".join(f"{k}: {n}" for k, n in sorted(counts.items())))
    else:
        if counts and not args.capture:
            print("  plates are already there: "
                  + ", ".join(f"{k}: {n}" for k, n in sorted(counts.items())))
            print("  A full capture is about three hours.")
            if not ask("take new ones?"):
                print("  keeping them")
                counts = None
        if counts is not None:
            capture(args)

    step(4, "Checking every plate")
    verify()

    step(5, "F-Droid: the listing text and the English phone plates")
    copy_fdroid()

    step(6, "Rehearsal - into a Play edit, validated, thrown away")
    if args.no_publish:
        print("  --no-publish: not talking to Play at all")
    else:
        must(sys.executable, os.path.join(HERE, "play_upload.py"), "--text",
             why="nothing was published")

    step(7, "Publishing")
    published = False
    if args.no_publish:
        print("  --no-publish: stopping here. Everything above is on disk.")
    else:
        print("  This uploads the listing text and every image, and for this "
              "app Play then sends\n  the edit for review - "
              "`changesNotSentForReview` is refused, so it cannot be "
              "deferred.\n  Managed publishing still keeps the reviewed "
              "listing away from users until you\n  release it in the Play "
              "Console.")
        if ask("publish the listing to Google Play?"):
            must(sys.executable, os.path.join(HERE, "play_upload.py"),
                 "--text", "--commit")
            published = True
        else:
            print("  nothing published. Everything above is on disk; "
                  "tools/store_release.py\n  --skip-capture picks up from here.")

    step(8, "Release notes for the Play Console")
    cmd = [sys.executable, os.path.join(HERE, "play_upload.py"),
           "--release-notes"]
    if args.version_code:
        cmd += ["--version-code", str(args.version_code)]
    must(*cmd)

    print(f"\n{'=' * 72}\nDone.\n{'=' * 72}")
    if published:
        print("- The listing is on Play and IN REVIEW. Release it in the "
              "Play Console when you\n  want it live.")
    print("- Still waiting for you in git: under fastlane/metadata/android/, "
          "the release notes\n  (<lang>/changelogs/), the listing text and the "
          "English plates (en/images/).\n  F-Droid reads those out of the "
          "repository, so they are not published until you\n  commit and push.")
    print("- The block above goes into the release's notes field in the Play "
          "Console when you\n  roll out the bundle. That is a separate act "
          "from this.")
    out = subprocess.run(["git", "-C", REPO, "status", "--short",
                          "fastlane/metadata/android"],
                         capture_output=True, text=True).stdout.rstrip()
    if out:
        print("\n" + out)


if __name__ == "__main__":
    main()
