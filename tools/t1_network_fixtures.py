#!/usr/bin/env python3
"""T1 network fixture driver (test-matrix rows network-http, network-mqtt).

Runs phyphox-docs' network fixtures against a phyphox build on an emulator
or device: start the deterministic fixture server on this host, open each
fixture experiment in the app, let its connection poll for a few seconds and
assert the buffer contents the fixtures promise - through the remote API,
which is the bus for all of this.

    python3 tools/t1_network_fixtures.py [--serial S] [--docs PATH]
        [--seconds 4] [--port 8080] [--fixture-port 8113] [--file-port 8114]
        [--host 10.0.2.2] [--skip-mqtt] [--out results.json]

Preconditions:
  - adb in PATH and the device/emulator connected; the driver sets up the
    port forward itself.
  - The app must serve the remote API for launched experiments and must not
    stop at the network privacy notice. The driver flips both switches
    itself (debug.phyphox.remote, debug.phyphox.autoConfirm - see
    DebugSwitches in the app sources) and clears them when it is done.
  - mosquitto in PATH for the mqtt fixture, which is skipped with a notice
    when it is missing (--skip-mqtt skips it unconditionally).

The fixture files carry FIXTURE-HOST and FIXTURE-PORT placeholders. The
driver substitutes them in the raw bytes - 10.0.2.2 is the host as seen from
an Android emulator - serves the result over a throwaway HTTP server and
opens it with "am start" on a phyphox:// URL, so the file arrives through
the app's normal remote-loading path.

Results: one JSON object per fixture (started, buffers read, findings),
written to --out and summarized on stdout. Exit 1 if any fixture failed its
assertions or the app stopped answering.
"""

import argparse
import functools
import http.server
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
DEFAULT_DOCS = os.path.normpath(os.path.join(ROOT, "..", "phyphox-docs"))
BUNDLE = "de.rwth_aachen.phyphox"

#Every fixture, the buffers to read and how long to let it run. The assertions
#themselves are one function per fixture below; they are the README of
#fixtures/network turned into code.
FIXTURES = [
    ("http-get-receive", ["seq", "value"]),
    ("http-get-send-roundtrip", ["back", "seq"]),
    ("http-post-roundtrip", ["back", "seq"]),
    ("http-error-malformed", ["never"]),
    ("http-error-down", ["never"]),
    ("mqtt-json-roundtrip", ["back"]),
]

MQTT_FIXTURES = {"mqtt-json-roundtrip"}


def close(a, b, tol=1e-6):
    return abs(a - b) <= tol + tol * abs(b)


def consecutive_from_one(values):
    return values == [float(i + 1) for i in range(len(values))]


def check_http_get_receive(buffers):
    findings = []
    seq, value = buffers["seq"], buffers["value"]
    if not seq:
        findings.append("seq stayed empty - no poll completed")
    elif not consecutive_from_one(seq):
        findings.append("seq is not the consecutive sequence 1..k: %s" % seq[:12])
    if len(value) != len(seq):
        findings.append("value has %d entries for %d polls" % (len(value), len(seq)))
    for n, v in zip(seq, value):
        if not close(v, n / 2):
            findings.append("value %s does not match seq %s / 2" % (v, n))
            break
    return findings


def check_http_get_send_roundtrip(buffers):
    findings = []
    back, seq = buffers["back"], buffers["seq"]
    if not back:
        findings.append("back stayed empty - the send never came back")
    for v in back:
        if not close(v, 42.5):
            findings.append("back holds %s, expected the sent 42.5" % v)
            break
    if not seq:
        findings.append("seq stayed empty - no round trip completed")
    elif not consecutive_from_one(seq):
        findings.append("seq is not the consecutive sequence 1..k: %s" % seq[:12])
    return findings


def check_http_post_roundtrip(buffers):
    findings = []
    back = buffers["back"]
    if not back:
        findings.append("back stayed empty - the array send never came back")
    pattern = [1.0, 2.5, 3.0]
    for i, v in enumerate(back):
        if not close(v, pattern[i % len(pattern)]):
            findings.append("back[%d] is %s, expected the repeating pattern %s"
                            % (i, v, pattern))
            break
    if back and len(back) % len(pattern) != 0:
        findings.append("back holds %d values, not whole repetitions of the sent array"
                        % len(back))
    return findings


def check_stays_alive(buffers):
    #The assertion IS that nothing crashed or hung: the run below already
    #required the remote API to answer after the fixture ran.
    if buffers["never"]:
        return ["never holds %s - the error response was accepted as data"
                % buffers["never"][:6]]
    return []


def check_mqtt_json_roundtrip(buffers):
    back = buffers["back"]
    if not back:
        return ["back stayed empty - publish and subscribe did not come around"]
    for v in back:
        if not close(v, 7.25):
            return ["back holds %s, expected the published 7.25" % v]
    return []


CHECKS = {
    "http-get-receive": check_http_get_receive,
    "http-get-send-roundtrip": check_http_get_send_roundtrip,
    "http-post-roundtrip": check_http_post_roundtrip,
    "http-error-malformed": check_stays_alive,
    "http-error-down": check_stays_alive,
    "mqtt-json-roundtrip": check_mqtt_json_roundtrip,
}


class Driver:
    def __init__(self, args):
        self.args = args
        self.base = "http://127.0.0.1:%d" % args.port
        self.fixture_base = "http://127.0.0.1:%d" % args.fixture_port

    # ------------------------------------------------------------- adb glue

    def adb(self, *command, check=True):
        prefix = ["adb"] + (["-s", self.args.serial] if self.args.serial else [])
        return subprocess.run(prefix + list(command), check=check,
                              capture_output=True, text=True).stdout.strip()

    def setprop(self, name, value):
        self.adb("shell", "setprop", name, value)

    def launch(self, url):
        self.adb("shell", "am", "force-stop", BUNDLE)
        time.sleep(0.5)
        self.adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", url)

    # ---------------------------------------------------------- remote API

    def get_json(self, url, timeout=5):
        with urllib.request.urlopen(url, timeout=timeout) as response:
            return json.loads(response.read().decode())

    def wait_for_remote(self, timeout=30):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                self.get_json(self.base + "/config", timeout=2)
                return True
            except Exception:
                time.sleep(0.5)
        return False

    def control(self, cmd):
        return self.get_json("%s/control?cmd=%s" % (self.base, cmd))

    def read_buffers(self, names):
        query = "&".join("%s=full" % name for name in names)
        data = self.get_json("%s/get?%s" % (self.base, query))
        return {name: [v for v in data["buffer"].get(name, {}).get("buffer", [])
                       if v is not None]
                for name in names}

    # ------------------------------------------------------------- the run

    def run_fixture(self, name, buffers, url):
        result = {"fixture": name, "findings": [], "buffers": {}}

        try:
            self.get_json(self.fixture_base + "/reset")
        except Exception as e:
            #The mqtt fixture does not talk to the HTTP fixture at all.
            if name not in MQTT_FIXTURES:
                result["findings"].append("could not reset the fixture server: %s" % e)
                return result

        self.launch(url)
        self.adb("forward", "tcp:%d" % self.args.port, "tcp:%d" % self.args.port)
        if not self.wait_for_remote():
            result["findings"].append(
                "the remote API never came up - the experiment did not open, or the "
                "app is a build without debug.phyphox.remote")
            return result

        self.control("start")
        time.sleep(self.args.seconds)
        self.control("stop")

        try:
            result["buffers"] = self.read_buffers(buffers)
        except Exception as e:
            result["findings"].append("could not read the buffers: %s" % e)
            return result
        result["started"] = True
        result["findings"] = CHECKS[name](result["buffers"])
        return result

    def run(self, fixtures, files_dir):
        results = []
        self.setprop("debug.phyphox.remote", "1")
        self.setprop("debug.phyphox.remotePort", str(self.args.port))
        self.setprop("debug.phyphox.autoConfirm", "1")
        try:
            for name, buffers in fixtures:
                url = "phyphox://%s:%d/%s.phyphox" % (
                    self.args.host, self.args.file_port, name)
                print("--- %s" % name, flush=True)
                result = self.run_fixture(name, buffers, url)
                for finding in result["findings"]:
                    print("    FINDING: %s" % finding, flush=True)
                if not result["findings"]:
                    print("    ok: %s" % ", ".join(
                        "%s=%d values" % (b, len(v))
                        for b, v in result["buffers"].items()), flush=True)
                results.append(result)
        finally:
            self.adb("shell", "am", "force-stop", BUNDLE, check=False)
            for name in ("debug.phyphox.remote", "debug.phyphox.remotePort",
                         "debug.phyphox.autoConfirm"):
                self.setprop(name, '""')
        return results


def serve_fixture_files(docs, host, fixture_port, file_port):
    """Substitute the placeholders and serve the files over HTTP."""
    directory = tempfile.mkdtemp(prefix="phyphox-fixtures-")
    source = os.path.join(docs, "fixtures", "network")
    for entry in os.listdir(source):
        if not entry.endswith(".phyphox"):
            continue
        with open(os.path.join(source, entry), "rb") as f:
            content = f.read()
        content = content.replace(b"FIXTURE-HOST", host.encode())
        content = content.replace(b"FIXTURE-PORT", str(fixture_port).encode())
        with open(os.path.join(directory, entry), "wb") as f:
            f.write(content)

    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        #Quiet on purpose: the app tries https before http on a phyphox:// URL (see
        #PhyphoxFile.openXMLInputStream), so every load starts with a TLS handshake into
        #this plain server, which would otherwise log a wall of "bad request" noise.
        def log_message(self, *a):
            pass

    handler = functools.partial(QuietHandler, directory=directory)
    server = http.server.ThreadingHTTPServer(("0.0.0.0", file_port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, directory


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", help="adb serial, if more than one device is attached")
    parser.add_argument("--docs", default=DEFAULT_DOCS, help="phyphox-docs checkout")
    parser.add_argument("--seconds", type=float, default=4.0,
                        help="how long each fixture runs (default 4)")
    parser.add_argument("--port", type=int, default=8080, help="remote API port")
    parser.add_argument("--fixture-port", type=int, default=8113)
    parser.add_argument("--file-port", type=int, default=8114)
    parser.add_argument("--host", default="10.0.2.2",
                        help="this host as the device sees it (default: the emulator's alias)")
    parser.add_argument("--skip-mqtt", action="store_true")
    parser.add_argument("--out", help="write the results as JSON here")
    args = parser.parse_args()

    fixtures_dir = os.path.join(args.docs, "fixtures", "network")
    if not os.path.isdir(fixtures_dir):
        sys.exit("No network fixtures at %s - is phyphox-docs checked out next to "
                 "this repository?" % fixtures_dir)

    fixture_tool = os.path.join(args.docs, "tools", "network_fixture.py")
    fixture_server = subprocess.Popen([sys.executable, fixture_tool, str(args.fixture_port)])
    file_server, directory = serve_fixture_files(
        args.docs, args.host, args.fixture_port, args.file_port)

    broker = None
    fixtures = list(FIXTURES)
    if args.skip_mqtt or not shutil.which("mosquitto"):
        if not args.skip_mqtt:
            print("NOTICE: mosquitto is not installed - skipping the mqtt fixtures "
                  "(network-mqtt).", flush=True)
        fixtures = [f for f in fixtures if f[0] not in MQTT_FIXTURES]
    else:
        broker = subprocess.Popen(["mosquitto", "-c", os.path.join(fixtures_dir, "mosquitto.conf")],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1)

    try:
        results = Driver(args).run(fixtures, directory)
    finally:
        file_server.shutdown()
        fixture_server.terminate()
        if broker is not None:
            broker.terminate()
        shutil.rmtree(directory, ignore_errors=True)

    if args.out:
        with open(args.out, "w") as f:
            json.dump(results, f, indent=1)

    failed = [r for r in results if r["findings"]]
    print("\n%d fixtures run, %d with findings" % (len(results), len(failed)), flush=True)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
