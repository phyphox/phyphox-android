#!/usr/bin/env python3
"""The release notes for one version, written once and read by every store.

F-Droid's `fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt` is the
single version-controlled copy; Play and the App Store take their text from it,
and a missing file means the notes have not been written yet. Only English and
German are written by hand; every other locale gets the English text. The iOS
uploader imports this module by path across the working root, so it must work
with `__file__` alone.
"""

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FDROID = os.path.join(REPO, "fastlane", "metadata", "android")

# F-Droid's directory names; phyphox-docs/screenshots/locales.yml maps them to the stores'
LANGUAGES = [("en", "English"), ("de", "German")]

LIMIT = 500  # Play's limit; F-Droid truncates its display there too


def android_version(repo=REPO):
    """(versionName, versionCode) out of app/build.gradle."""
    path = os.path.join(repo, "app", "build.gradle")
    with open(path, encoding="utf-8") as f:
        src = f.read()
    name = re.search(r'^\s*versionName\s+"([^"]+)"', src, re.M)
    code = re.search(r"^\s*versionCode\s+(\d+)", src, re.M)
    if not name or not code:
        raise SystemExit(f"could not read versionName/versionCode from {path}")
    return name.group(1), int(code.group(1))


def code_for(version_name, repo=REPO):
    """The versionCode the changelog files are named after, for a marketing version."""
    name, code = android_version(repo)
    if name != version_name:
        raise SystemExit(
            f"this release is {version_name}, and phyphox-android is at "
            f"{name} (versionCode {code}).\nThe release notes are stored under "
            f"Android's versionCode because that is how F-Droid names them, so "
            f"the\ntwo versions have to agree. Bump the other platform first, "
            f"or pass --version-code to say which file to use.")
    return code


def path_for(lang, code, repo=REPO):
    return os.path.join(repo, "fastlane", "metadata", "android", lang,
                        "changelogs", f"{code}.txt")


def read(code, repo=REPO):
    """{lang: text} for the changelogs that exist and are not empty."""
    out = {}
    for lang, _label in LANGUAGES:
        p = path_for(lang, code, repo)
        if not os.path.isfile(p):
            continue
        with open(p, encoding="utf-8") as f:
            text = f.read().strip()
        if text:
            out[lang] = text
    return out


def _ask(lang, label, version_name, code, repo):
    where = os.path.relpath(path_for(lang, code, repo), os.path.dirname(repo))
    print(f"\n{label} release notes for {version_name} "
          f"(versionCode {code}), to be written to")
    print(f"  {where}")
    print(f"Type them, then a line with a single '.' to finish. "
          f"At most {LIMIT} characters.")
    while True:
        lines, eof = [], False
        while True:
            try:
                line = input()
            except EOFError:
                # Ctrl-D ends the text like "."; on an empty text it aborts rather than spin on a closed stdin
                eof = True
                break
            if line.strip() == ".":
                break
            lines.append(line.rstrip())
        text = "\n".join(lines).strip()
        if not text:
            if eof:
                raise SystemExit(f"no {label} release notes - nothing written")
            print(f"  empty - {label} release notes are required. "
                  f"Ctrl-D or Ctrl-C to abort the whole run.")
            continue
        if len(text) > LIMIT:
            print(f"  {len(text)} characters, and Play allows {LIMIT}. "
                  f"Shorten it by {len(text) - LIMIT} and type it again.")
            continue
        return text


def ensure(code, version_name, repo=REPO, interactive=None):
    """{lang: text} for every language in LANGUAGES, asking for and writing whatever is missing."""
    notes = read(code, repo)
    missing = [(l, label) for l, label in LANGUAGES if l not in notes]
    if not missing:
        return _summarise(notes, version_name, code)

    if interactive is None:
        interactive = sys.stdin.isatty()
    if not interactive:
        raise SystemExit(
            "no release notes for versionCode {} in {}\n  {}\nThis needs a "
            "terminal to ask for them, or write the file(s) by hand and run "
            "this again.".format(
                code, ", ".join(l for l, _ in missing),
                "\n  ".join(path_for(l, code, repo) for l, _ in missing)))

    print(f"\nNo release notes have been written for {version_name} "
          f"(versionCode {code}) yet.")
    print("F-Droid's changelogs are the reference for both stores, so they are "
          "asked for here\nand written into this repository. English is what "
          "every language other than German\nwill show.")
    for lang, label in missing:
        notes[lang] = _ask(lang, label, version_name, code, repo)

    print("\nabout to write:")
    for lang, _label in missing:
        print(f"\n--- {path_for(lang, code, repo)}")
        print(notes[lang])
    try:
        answer = input("\nwrite these? [y/N] ").strip().lower()
    except EOFError:
        answer = ""
    if answer not in ("y", "yes"):
        raise SystemExit("nothing written")

    for lang, _label in missing:
        p = path_for(lang, code, repo)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(notes[lang] + "\n")
        print(f"  wrote {p}")
    print("These are committed to phyphox-android - F-Droid reads them out of "
          "git, so they\nare not published until that commit is pushed.")
    return _summarise(notes, version_name, code)


def _summarise(notes, version_name, code):
    print(f"\nrelease notes for {version_name} (versionCode {code}): "
          + ", ".join(f"{l} {len(notes[l])} chars" for l, _ in LANGUAGES))
    # A file written by hand was never measured
    over = [l for l, _ in LANGUAGES if len(notes[l]) > LIMIT]
    if over:
        print(f"  !! {' and '.join(over)} over {LIMIT} characters - Play "
              f"refuses a longer one, and\n     F-Droid cuts its display "
              f"there. Shorten the file(s) and run this again.")
    return notes


def text_for(locale, notes):
    """The notes one store locale gets: German for German, English for the rest."""
    return notes["de"] if locale.split("-")[0] == "de" else notes["en"]


def play_xml(notes, locales):
    """Play's release-notes block to paste into the console: one `<locale>` element per language."""
    return "\n".join(f"<{loc}>\n{text_for(loc, notes)}\n</{loc}>"
                     for loc in locales)


def suspicious(notes):
    """Characters whose handling in the console's `<locale>` field is undocumented; reported, not escaped."""
    return sorted({f"{lang}: {c!r}" for lang, text in notes.items()
                   for c in "<>&" if c in text})
