#!/usr/bin/env python3
"""Upload the store listing images, and with --text the listing text, to Google Play.

    tools/play_upload.py                       # validate, change nothing
    tools/play_upload.py --commit              # publish the listing (Play sends it for review)
    tools/play_upload.py --release-notes       # the release notes, to paste into a release

Without --commit the edit is uploaded, validated and deleted. --text also writes
the same prepared text into fastlane/metadata/android/ for F-Droid. Talks to the
edits API with the standard library and Application Default Credentials only.
"""

import argparse
import json
import mimetypes
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SHOTS = os.path.normpath(os.path.join(REPO, "..", "screenshots", "android"))
PACKAGE = "de.rwth_aachen.phyphox"
TRANSLATION = os.path.normpath(os.path.join(REPO, "..", "phyphox-translation"))
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"

# directory name in the capture output -> the imageType the API wants
IMAGE_TYPES = {
    "phoneScreenshots": "phoneScreenshots",
    "sevenInchScreenshots": "sevenInchScreenshots",
    "tenInchScreenshots": "tenInchScreenshots",
}


def store_text(po_locale):
    """Title, short and full description for one locale, formatted by phyphox-translation's updateMetadata.py."""
    import polib

    mod = _formatter()
    po = os.path.join(TRANSLATION, "store", f"{po_locale}.po")
    if not os.path.isfile(po):
        return None
    out = {"title": "phyphox"}
    for entry in polib.pofile(po):
        if not entry.msgstr:
            continue
        if entry.msgid == "store_short_description":
            out["shortDescription"] = mod.unescape(entry.msgstr)
        elif entry.msgid == "store_long_description":
            out["fullDescription"] = mod.formatDescription(entry.msgstr)
    if "shortDescription" not in out or "fullDescription" not in out:
        return None
    return out


_FORMATTER = None


def _formatter():
    global _FORMATTER
    if _FORMATTER is not None:
        return _FORMATTER
    import contextlib
    import importlib.util
    import io
    path = os.path.join(TRANSLATION, "python", "updateMetadata.py")
    spec = importlib.util.spec_from_file_location("phyphox_updateMetadata", path)
    mod = importlib.util.module_from_spec(spec)
    argv, cwd, bytecode = sys.argv, os.getcwd(), sys.dont_write_bytecode
    try:
        sys.argv = ["updateMetadata.py", os.path.join(os.sep, "nonexistent")]
        os.chdir(os.path.join(TRANSLATION, "python"))
        sys.dont_write_bytecode = True    # no __pycache__ in phyphox-translation
        with contextlib.redirect_stdout(io.StringIO()):
            spec.loader.exec_module(mod)      # says "invalid destination", writes nothing
    finally:
        sys.argv = argv
        sys.dont_write_bytecode = bytecode
        os.chdir(cwd)
    _FORMATTER = mod
    return mod


# Play's limits; F-Droid's lint uses the same numbers
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000}


def trim_attribution(short):
    """Drop the trailing bracketed attribution (the store shows it anyway) when the short description will not fit."""
    # CJK locales write the credit in full-width brackets
    trimmed = re.sub(r"\s*[(\[（【〔][^()\[\]（）【】〔〕]*[)\]）】〕]\s*$",
                     "", short).rstrip()
    return trimmed or short


def too_long(text):
    return [(k, len(text[k]), LIMITS[k]) for k in LIMITS
            if k in text and len(text[k]) > LIMITS[k]]


def token(required=True):
    """An access token, or None with required=False (--release-notes works logged out)."""
    try:
        out = subprocess.run(
            ["gcloud", "auth", "application-default", "print-access-token"],
            capture_output=True, text=True, check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        if not required:
            return None
        sys.exit("no Application Default Credentials - run:\n"
                 "  gcloud auth application-default login "
                 "--client-id-file=~/.config/phyphox-store/client.json \\\n"
                 "      --scopes=https://www.googleapis.com/auth/androidpublisher,"
                 "https://www.googleapis.com/auth/cloud-platform")
    if not out:
        if not required:
            return None
        sys.exit("gcloud returned no access token")
    return out


def call(method, url, tok, body=None, data=None, content_type=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", f"Bearer {tok}")
    payload = data
    if body is not None:
        payload = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, payload, timeout=120) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:600]
        raise SystemExit(f"{method} {url.split('/v3')[-1]}\n  HTTP {e.code}: {detail}")


def listing_languages(tok, edit):
    d = call("GET", f"{API}/applications/{PACKAGE}/edits/{edit}/listings", tok)
    return sorted(l["language"] for l in d.get("listings", []))


def local_sets(root):
    """{locale: {imageType: [paths in display order]}} from the capture output."""
    out = {}
    if not os.path.isdir(root):
        sys.exit(f"no screenshots at {root}")
    for locale in sorted(os.listdir(root)):
        images = os.path.join(root, locale, "images")
        if not os.path.isdir(images):
            continue
        for folder, kind in IMAGE_TYPES.items():
            d = os.path.join(images, folder)
            if not os.path.isdir(d):
                continue
            shots = sorted(f for f in os.listdir(d) if f.endswith((".png", ".jpg")))
            if shots:
                out.setdefault(locale, {})[kind] = [os.path.join(d, f) for f in shots]
    return out


def locale_rows():
    import yaml
    docs = os.path.normpath(os.path.join(REPO, "..", "phyphox-docs"))
    with open(os.path.join(docs, "screenshots", "locales.yml")) as f:
        return yaml.safe_load(f)["locales"]


def prepare_text(po_locale, label):
    """(text, None) for one language, or (None, why) when a string is over a limit."""
    text = store_text(po_locale) if po_locale else None
    if not text:
        return None, f"{label}: no store text for {po_locale!r}"
    if any(k == "shortDescription" for k, _n, _l in too_long(text)):
        short = trim_attribution(text["shortDescription"])
        if len(short) <= LIMITS["shortDescription"]:
            print(f"  {label:6s} short description trimmed to fit: "
                  f"{len(text['shortDescription'])} -> {len(short)} "
                  f"characters (attribution dropped)")
            text["shortDescription"] = short
    over = too_long(text)
    if over:
        return None, "; ".join(
            f"{label}: {k} is {n} characters, the stores allow {lim}"
            for k, n, lim in over)
    return text, None


def refuse(problems, doing):
    raise SystemExit(
        f"refusing to {doing}:\n  " + "\n  ".join(problems)
        + "\nShorten these in Weblate - truncating a translation here "
          "would be worse than not using it.")


# app language -> F-Droid directory where they differ; Serbian gets the Cyrillic one (as in locales.yml)
FDROID_DIRS = {"zh-Hans": "zh-CN", "zh-Hant": "zh-TW", "sr-Latn": "sr"}
FDROID_FILES = (("title.txt", "title"),
                ("short_description.txt", "shortDescription"),
                ("full_description.txt", "fullDescription"))


def fdroid_text():
    """Write the listing text into fastlane/metadata/android (no trailing newline, as updateMetadata.py wrote it)."""
    root = os.path.join(REPO, "fastlane", "metadata", "android")
    wanted = {}
    for row in locale_rows():
        app = row["app"]
        wanted.setdefault(FDROID_DIRS.get(app, app), app.replace("-", "_"))

    changed, unchanged, missing, problems = [], [], [], []
    for d, po in sorted(wanted.items()):
        target = os.path.join(root, d)
        # en-US is a symlink to en; writing through it would write en twice
        if not os.path.isdir(target) or os.path.islink(target):
            missing.append(d)
            continue
        text, why = prepare_text(po, d)
        if why:
            problems.append(why)
            continue
        for fn, key in FDROID_FILES:
            path = os.path.join(target, fn)
            old = None
            if os.path.isfile(path):
                with open(path, encoding="utf-8") as f:
                    old = f.read()
            if old == text[key]:
                unchanged.append(f"{d}/{fn}")
                continue
            with open(path, "w", encoding="utf-8") as f:
                f.write(text[key])
            changed.append(f"{d}/{fn}")
    if problems:
        refuse(problems, "write the F-Droid text")
    rel = os.path.relpath(root, REPO)
    if changed:
        print(f"  F-Droid: {len(changed)} file(s) changed in {rel}/ "
              f"({len(unchanged)} unchanged):")
        for c in changed:
            print(f"    {c}")
    else:
        print(f"  F-Droid: {rel}/ already matches the translations "
              f"({len(unchanged)} files)")
    if missing:
        print(f"  F-Droid: no directory for {', '.join(missing)} - each has a "
              f"translation and gets\n    English on F-Droid until one is "
              f"created under {rel}/ (a decision, so not\n    done here)")
    return changed


def upload_text(tok, edit, locales_wanted):
    """Push title, short and full description; anything over a limit stops before the first PUT."""
    po_for = {}
    for row in locale_rows():
        a = row["android"]
        for name in (a if isinstance(a, list) else [a]):
            po_for.setdefault(name, row["app"].replace("-", "_"))

    texts, problems = {}, []
    for locale in locales_wanted:
        text, why = prepare_text(po_for.get(locale), locale)
        if why:
            problems.append(why)
        else:
            texts[locale] = text
    if problems:
        refuse(problems, "upload text")
    for locale, text in sorted(texts.items()):
        call("PUT", f"{API}/applications/{PACKAGE}/edits/{edit}/listings/{locale}",
             tok, body={"language": locale, **text})
        print(f"  {locale:6s} text: short {len(text['shortDescription'])}, "
              f"full {len(text['fullDescription'])}")


def play_locales():
    """Every Play listing locale in locales.yml, in file order, deduplicated."""
    import yaml
    docs = os.path.normpath(os.path.join(REPO, "..", "phyphox-docs"))
    with open(os.path.join(docs, "screenshots", "locales.yml")) as f:
        rows = yaml.safe_load(f)["locales"]
    out = []
    for row in rows:
        a = row["android"]
        for name in (a if isinstance(a, list) else [a]):
            if name not in out:
                out.append(name)
    return out


def on_store_locales():
    """The languages the listing actually has, or None without credentials."""
    tok = token(required=False)
    if not tok:
        return None
    edit = call("POST", f"{API}/applications/{PACKAGE}/edits", tok, body={})["id"]
    try:
        return listing_languages(tok, edit)
    finally:
        call("DELETE", f"{API}/applications/{PACKAGE}/edits/{edit}", tok)


def release_notes(version_code=None):
    """Print the release-notes block for the Play Console. Uploads nothing."""
    import changelog

    name, code = changelog.android_version(REPO)
    if version_code is not None:
        code = version_code
    notes = changelog.ensure(code, name, REPO)

    locales = play_locales()
    on_store = on_store_locales()
    if on_store is None:
        print("\n  (not logged in, so the block below lists every locale in "
              "locales.yml. Play\n   rejects one whose listing does not exist "
              "yet - gu, ko-KR and ta-IN as of\n   2026-09-01 - so drop those "
              "lines if the console complains.)")
    else:
        unlisted = [l for l in locales if l not in on_store]
        if unlisted:
            print(f"\n  leaving out {', '.join(unlisted)}: no listing on the "
                  f"store, and Play refuses\n  release notes for a language the "
                  f"listing does not have")
        locales = [l for l in locales if l in on_store]

    odd = changelog.suspicious(notes)
    if odd:
        print("\n  !! the notes contain " + ", ".join(odd) + ". The console "
              "splits this block by\n     its tags and it is not documented "
              "whether it also unescapes entities, so\n     check how those "
              "characters come out in the release before rolling out.")

    german = [l for l in locales if l.split("-")[0] == "de"]
    print(f"\nrelease notes for {len(locales)} language(s): German for "
          f"{', '.join(german) or 'none'}, English for the rest.")
    print("Paste this into the release's notes field in the Play Console "
          "(Release > Production >\nEdit release > Release notes, "
          "\"Copy from XML\"):\n")
    print(changelog.play_xml(notes, locales))


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except AttributeError:
        pass

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--screenshots", default=DEFAULT_SHOTS)
    ap.add_argument("--image-types", help="comma separated, from "
                                          + ",".join(sorted(IMAGE_TYPES))
                                          + "; default: all that are present")
    ap.add_argument("--text", action="store_true",
                    help="also upload the listing text, read from "
                         "phyphox-translation's store PO files - and write "
                         "the same text into fastlane/metadata/android for "
                         "F-Droid")
    ap.add_argument("--create-listings", action="store_true",
                    help="with --text, also create listings for locales the "
                         "store does not have yet")
    ap.add_argument("--languages", help="comma separated Play locales; default: "
                                        "every one that has both images and a listing")
    ap.add_argument("--commit", action="store_true",
                    help="apply the edit, which for this app also sends it for "
                         "review. Without this it is validated and thrown away.")
    ap.add_argument("--release-notes", action="store_true",
                    help="print the release notes for the current version as a "
                         "block to paste into a Play Console release, asking "
                         "for them if this version has none yet. Touches "
                         "neither the listing nor a release.")
    ap.add_argument("--version-code", type=int,
                    help="with --release-notes: the versionCode to write the "
                         "notes under, when it is not the one in build.gradle")
    args = ap.parse_args()

    if args.release_notes:
        clash = [f for f, on in (("--commit", args.commit), ("--text", args.text))
                 if on]
        if clash:
            sys.exit(f"--release-notes does not go with {', '.join(clash)}: it "
                     f"prints the notes for a\nrelease and touches the listing "
                     f"not at all. Run it on its own.")
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        release_notes(args.version_code)
        return

    tok = token()
    sets = local_sets(args.screenshots)
    if args.image_types:
        keep = set(args.image_types.split(","))
        unknown = keep - set(IMAGE_TYPES)
        if unknown:
            sys.exit(f"unknown image type(s): {', '.join(sorted(unknown))}")
        # uploading a type replaces every image of it, so narrowing spares a listing mid-review
        sets = {loc: {k: v for k, v in kinds.items() if k in keep}
                for loc, kinds in sets.items()}
        sets = {loc: kinds for loc, kinds in sets.items() if kinds}
    edit = call("POST", f"{API}/applications/{PACKAGE}/edits", tok, body={})["id"]
    print(f"edit {edit}")
    try:
        on_store = listing_languages(tok, edit)
        wanted = args.languages.split(",") if args.languages else sorted(sets)

        missing = [l for l in wanted if l not in sets]
        if missing:
            raise SystemExit(f"no images for {', '.join(missing)} in "
                             f"{args.screenshots}")
        # a locale without a listing needs its text created first - a decision, so only reported
        unlisted = [l for l in wanted if l not in on_store]
        if unlisted and not (args.text and args.create_listings):
            print(f"  skipping {', '.join(unlisted)}: no listing on the store "
                  f"yet (--text --create-listings would make one)")
            wanted = [l for l in wanted if l in on_store]

        if args.text:
            # F-Droid first: same prepared text, so a limit stops the run before anything is in the edit
            fdroid_text()
            upload_text(tok, edit, wanted)

        total = 0
        for locale in wanted:
            for kind, paths in sorted(sets[locale].items()):
                where = f"applications/{PACKAGE}/edits/{edit}/listings/{locale}/{kind}"
                call("DELETE", f"{API}/{where}", tok)
                for p in paths:
                    ctype = mimetypes.guess_type(p)[0] or "image/png"
                    with open(p, "rb") as f:
                        call("POST", f"{UPLOAD}/{where}?uploadType=media", tok,
                             data=f.read(), content_type=ctype)
                    total += 1
                print(f"  {locale:6s} {kind:22s} {len(paths)} image(s)")

        if args.commit:
            # Play refuses changesNotSentForReview for this app, so a commit always submits for review
            call("POST", f"{API}/applications/{PACKAGE}/edits/{edit}:commit", tok)
            what = f"{total} image(s)"
            if args.text:
                what += " and the listing text"
            print(f"committed: {what} on the store and IN REVIEW. Managed "
                  f"publishing holds them until you release them in the "
                  f"Play Console.")
            if args.text:
                print("F-Droid has the same text once fastlane/metadata/android "
                      "is committed and pushed.")
            edit = None
        else:
            call("POST", f"{API}/applications/{PACKAGE}/edits/{edit}:validate", tok)
            print(f"validated: {total} image(s) would be published. "
                  f"Nothing changed - pass --commit to publish.")
    finally:
        if edit:
            call("DELETE", f"{API}/applications/{PACKAGE}/edits/{edit}", tok)
            print("edit discarded")


if __name__ == "__main__":
    main()
