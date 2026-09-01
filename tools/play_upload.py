#!/usr/bin/env python3
"""Upload store listing images to Google Play.

The other half of the release step described in ../STORE-RELEASE-PLAN.md: the
capture script writes every locale and form factor into the working root's
`screenshots/android/`, and this puts them on the store. Only `en-US` is ever
committed to this repository, and that is for F-Droid, which reads the metadata
tree out of git and needs nothing uploaded.

    tools/play_upload.py                       # validate, change nothing
    tools/play_upload.py --commit              # actually publish the listing

**Nothing is published without --commit.** Without it the script creates an
edit, uploads into it, asks Play to validate it, and then deletes the edit -
which is as close to a rehearsal as the API offers.

Why not `supply`: it would add a Ruby toolchain, and its client does not expose
a quota project, which user credentials need for this API. The edits API is a
handful of REST calls and the auth already works, so this uses neither fastlane
nor a Google client library - only what is in the standard library.

Authentication is the maintainer's own account through Application Default
Credentials, obtained with an OAuth client of ours:

    gcloud auth application-default login \
        --client-id-file=~/.config/phyphox-store/client.json \
        --scopes=https://www.googleapis.com/auth/androidpublisher,\
https://www.googleapis.com/auth/cloud-platform

That is deliberately a login rather than a stored key (plan §8.1), so revoking
it is `gcloud auth application-default revoke`.
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
    """Title, short and full description for one locale, from the PO files.

    **phyphox-translation is read, never written.** The formatting - dash lists
    to bullets, bare URLs to anchors, the escaping artefacts an old import left
    behind - is not reimplemented here either: `updateMetadata.py` in that repo
    is the one definition of it, and this imports its two functions rather than
    keeping a second copy that could drift.

    That module does its work at import time from sys.argv, so it is imported
    with an argv pointing nowhere: it then prints two lines about an invalid
    destination and writes nothing, which is exactly what is wanted from it.
    """
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
        # phyphox-translation must come out of this byte-for-byte unchanged, and
        # importing a module from it would otherwise leave a __pycache__ behind
        sys.dont_write_bytecode = True
        with contextlib.redirect_stdout(io.StringIO()):
            spec.loader.exec_module(mod)      # says "invalid destination", writes nothing
    finally:
        sys.argv = argv
        sys.dont_write_bytecode = bytecode
        os.chdir(cwd)
    _FORMATTER = mod
    return mod


# Play's own limits. Exceeding one is rejected at commit, so it is worth saying
# which string and by how much rather than reading it out of an API error.
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000}


def trim_attribution(short):
    """Drop the trailing bracketed attribution when the line will not fit.

    Every locale's short description ends with a parenthesised "by RWTH Aachen
    University" in some wording, and nine of them run past Play's 80 characters
    because of it. The university is the account holder the store already shows,
    so the bracket is redundant and can go without asking each translator
    (maintainer, 2026-09-01).

    Only when it is needed, and only from the end: a listing that already fits
    keeps its attribution, and a bracket anywhere but the end is left alone
    because it would be part of the sentence rather than a credit.
    """
    # CJK locales write the credit in full-width brackets, so those count too -
    # none of them is over the limit today, but the rule should not quietly
    # stop applying to a language because of how it punctuates.
    trimmed = re.sub(r"\s*[(\[（【〔][^()\[\]（）【】〔〕]*[)\]）】〕]\s*$",
                     "", short).rstrip()
    return trimmed or short


def too_long(text):
    return [(k, len(text[k]), LIMITS[k]) for k in LIMITS
            if k in text and len(text[k]) > LIMITS[k]]


def token():
    try:
        out = subprocess.run(
            ["gcloud", "auth", "application-default", "print-access-token"],
            capture_output=True, text=True, check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        sys.exit("no Application Default Credentials - run:\n"
                 "  gcloud auth application-default login "
                 "--client-id-file=~/.config/phyphox-store/client.json \\\n"
                 "      --scopes=https://www.googleapis.com/auth/androidpublisher,"
                 "https://www.googleapis.com/auth/cloud-platform")
    if not out:
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


def upload_text(tok, edit, locales_wanted):
    """Push title, short and full description from the PO files.

    Every string is measured against Play's limits BEFORE anything is sent. An
    over-long one is refused by name and by how much, because the alternative -
    letting the commit fail, or silently truncating - either wastes a run or
    mangles somebody's translation. Shortening it is the translator's job, in
    Weblate; nothing here edits phyphox-translation.
    """
    import yaml
    docs = os.path.normpath(os.path.join(REPO, "..", "phyphox-docs"))
    with open(os.path.join(docs, "screenshots", "locales.yml")) as f:
        rows = yaml.safe_load(f)["locales"]
    po_for = {}
    for row in rows:
        a = row["android"]
        for name in (a if isinstance(a, list) else [a]):
            po_for.setdefault(name, row["app"].replace("-", "_"))

    texts, problems = {}, []
    for locale in locales_wanted:
        po = po_for.get(locale)
        text = store_text(po) if po else None
        if not text:
            problems.append(f"{locale}: no store text for {po!r}")
            continue
        if any(k == "shortDescription" for k, _n, _l in too_long(text)):
            short = trim_attribution(text["shortDescription"])
            if len(short) <= LIMITS["shortDescription"]:
                print(f"  {locale:6s} short description trimmed to fit: "
                      f"{len(text['shortDescription'])} -> {len(short)} "
                      f"characters (attribution dropped)")
                text["shortDescription"] = short
        over = too_long(text)
        if over:
            problems.append("; ".join(
                f"{locale}: {k} is {n} characters, Play allows {lim}"
                for k, n, lim in over))
            continue
        texts[locale] = text
    if problems:
        raise SystemExit(
            "refusing to upload text:\n  " + "\n  ".join(problems)
            + "\nShorten these in Weblate - truncating a translation here "
              "would be worse than not uploading it.")
    for locale, text in sorted(texts.items()):
        call("PUT", f"{API}/applications/{PACKAGE}/edits/{edit}/listings/{locale}",
             tok, body={"language": locale, **text})
        print(f"  {locale:6s} text: short {len(text['shortDescription'])}, "
              f"full {len(text['fullDescription'])}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--screenshots", default=DEFAULT_SHOTS)
    ap.add_argument("--text", action="store_true",
                    help="also upload the listing text, read from "
                         "phyphox-translation's store PO files")
    ap.add_argument("--create-listings", action="store_true",
                    help="with --text, also create listings for locales the "
                         "store does not have yet")
    ap.add_argument("--languages", help="comma separated Play locales; default: "
                                        "every one that has both images and a listing")
    ap.add_argument("--commit", action="store_true",
                    help="publish. Without this the edit is validated and thrown away.")
    args = ap.parse_args()

    tok = token()
    sets = local_sets(args.screenshots)
    edit = call("POST", f"{API}/applications/{PACKAGE}/edits", tok, body={})["id"]
    print(f"edit {edit}")
    try:
        on_store = listing_languages(tok, edit)
        wanted = args.languages.split(",") if args.languages else sorted(sets)

        missing = [l for l in wanted if l not in sets]
        if missing:
            raise SystemExit(f"no images for {', '.join(missing)} in "
                             f"{args.screenshots}")
        # A locale with no listing yet would need its store text created first,
        # which is a decision rather than a detail - so it is reported, not
        # invented.
        unlisted = [l for l in wanted if l not in on_store]
        if unlisted and not (args.text and args.create_listings):
            print(f"  skipping {', '.join(unlisted)}: no listing on the store "
                  f"yet (--text --create-listings would make one)")
            wanted = [l for l in wanted if l in on_store]

        if args.text:
            upload_text(tok, edit, wanted)

        total = 0
        for locale in wanted:
            for kind, paths in sorted(sets[locale].items()):
                # the images live under listings/, not images/ - the resource
                # is "an image of a listing", and the path says so
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
            call("POST", f"{API}/applications/{PACKAGE}/edits/{edit}:commit", tok)
            print(f"committed: {total} image(s) are live")
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
