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
import subprocess
import sys
import urllib.error
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SHOTS = os.path.normpath(os.path.join(REPO, "..", "screenshots", "android"))
PACKAGE = "de.rwth_aachen.phyphox"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"

# directory name in the capture output -> the imageType the API wants
IMAGE_TYPES = {
    "phoneScreenshots": "phoneScreenshots",
    "sevenInchScreenshots": "sevenInchScreenshots",
    "tenInchScreenshots": "tenInchScreenshots",
}


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


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--screenshots", default=DEFAULT_SHOTS)
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
        if unlisted:
            print(f"  skipping {', '.join(unlisted)}: no listing on the store yet")
            wanted = [l for l in wanted if l in on_store]

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
