#!/usr/bin/env python3
"""Upload store listing images to Google Play.

The other half of the release step described in ../STORE-RELEASE-PLAN.md: the
capture script writes every locale and form factor into the working root's
`screenshots/android/`, and this puts them on the store. Only `en-US` is ever
committed to this repository, and that is for F-Droid, which reads the metadata
tree out of git and needs nothing uploaded.

    tools/play_upload.py                       # validate, change nothing
    tools/play_upload.py --commit              # actually publish the listing
    tools/play_upload.py --release-notes       # the release notes, to paste into a release

**Nothing is published without --commit.** Without it the script creates an
edit, uploads into it, asks Play to validate it, and then deletes the edit -
which is as close to a rehearsal as the API offers.

**`--commit` does submit for review**, and cannot avoid it: Play answers
`changesNotSentForReview` with "Changes are sent for review automatically. The
query parameter must not be set." Managed publishing is what keeps the reviewed
result away from users until somebody releases it.

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

RELEASE NOTES ARE NOT PART OF THE LISTING
-----------------------------------------
`--release-notes` is a separate mode and does not touch the listing at all. On
Play a release is created in the console when a bundle is rolled out, which is
a different act from updating the store entry - and the edits API cannot attach
notes to a release this project does not create through it. So that mode reads
the release notes out of the F-Droid changelogs (asking for them if this version
has none yet, see tools/changelog.py) and prints the `<locale>` block the
console's release-notes field takes. Copying it in is the manual step.
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


def token(required=True):
    """An access token, or - with required=False - None if there are none.

    --release-notes is useful on a machine that has never been logged in, so
    that mode asks for a token and carries on without one rather than stopping.
    """
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


def play_locales():
    """Every Play listing locale this project has text for, from locales.yml.

    In file order, deduplicated: Serbian is two app languages and one Play
    listing, and Portuguese is one app language and two listings.
    """
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
    """The languages the listing actually has, or None without credentials.

    Play refuses a release-notes block naming a language the listing does not
    have, and three of the locales in locales.yml have no listing yet. Asking
    the store is one throwaway edit; a machine that has never been logged in
    still gets the full block and a warning, because copying the notes into the
    console is not something to have to be at this desk for.
    """
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
    # 138 images take minutes to push into an edit; with stdout redirected to a
    # log, block buffering would show nothing at all until the end - and nothing
    # whatsoever if the run is interrupted.
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
                         "phyphox-translation's store PO files")
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
        # A mode of its own, not something to combine: it prints text to paste
        # into a release and never opens an edit, so --commit would silently
        # publish nothing at all.
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
        # Narrowing matters, not just for speed: uploading a type replaces every
        # image of it, so re-sending images that are already on the store churns
        # a listing that may be mid-review for no gain.
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
            # A plain commit, because Play refuses the alternative for this app:
            # "Changes are sent for review automatically. The query parameter
            # changesNotSentForReview must not be set." So there is no way to
            # apply an edit here without submitting it for review, and the
            # maintainer decided on 2026-09-01 to accept that. Managed
            # publishing is what still keeps the result away from users until
            # someone releases it.
            call("POST", f"{API}/applications/{PACKAGE}/edits/{edit}:commit", tok)
            what = f"{total} image(s)"
            if args.text:
                what += " and the listing text"
            print(f"committed: {what} on the store and IN REVIEW. Managed "
                  f"publishing holds them until you release them in the "
                  f"Play Console.")
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
