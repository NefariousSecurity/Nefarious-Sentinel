#!/usr/bin/env python3
"""Sign verified Sentinel v1.0.1 APKs in a separate private staging folder.

--check: read-only, validates sources, release key certificate, and tools.
--sign: signs 4 matching unsigned APKs, verifies identity and contents.
Never writes into either Git checkout or previously released APK paths.
"""
import argparse
import hashlib
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

COMMIT = "ccd6c1419440acc49d8909af4049c6482f77442e"
CERT = "46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7"
ROOT = Path.home() / "Projects/oblivion-v2"
BASE = Path.home() / "Projects/fdroid-sentinel-validation"
MINT = BASE / "v101-clean.Fc2Z6qIm/src"
FDROID = BASE / "v101-fdroid.6ZBw7rrs/src"
FDROID_COMMIT = BASE / "v101-fdroid.6ZBw7rrs/SOURCE_COMMIT.txt"

# Module, output filename stem, package ID, unsigned SHA256, v1.0.0 signed SHA256
APPS = (
    ("app", "Core", "com.nefarioussecurity.sentinel",
     "feab9330016b9b05e095c3ed8fe6159106831f072021ce6d971c40b2fef2121e",
     "b2d67d797acab9abae59d9c3351c1ab211986c5f4fc9fe494a2bbd0a62e94629"),
    ("controller", "Controller", "com.nefarioussecurity.sentinel.controller",
     "30d1a4a4ac2ae980ae6d22cc8a99d9b3567aa7e1b1cab887e08c15e8ebb75ba9",
     "fd7fc3e53676a16387d2e3fa933bd0dc88a767580e65cab368582f9cad20fa80"),
    ("motion-helper", "Motion", "com.nefarioussecurity.sentinel.motion",
     "a01c0518d7d1e4199ef2c5f8d27c7bc02d81eba44698e64037f41736ca5dff09",
     "05fec78c4b8da982c2ab47f0a5eeb26304fab599c4995334139139dcb1f5847b"),
    ("sms-helper", "SMS", "com.nefarioussecurity.sentinel.sms",
     "1593dacdd99bcd679240947eb483fc9c92b3fd0b726939d6c5e03fc0af0d508e",
     "3678b9c22e9dd210af89ae3f1f23e117598e220b0a3023ba8dc5a12f531a044c"),
)


def stop(message):
    raise RuntimeError(message)


def run(argv, *, password=None, timeout=120):
    # Never include stderr/stdout from commands that may mention secrets.
    result = subprocess.run(argv, input=password, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout, check=False)
    if result.returncode:
        stop("Command failed: " + str(argv[0]) + " (details deliberately hidden)")
    return result.stdout


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as infile:
        for block in iter(lambda: infile.read(1048576), b""):
            h.update(block)
    return h.hexdigest()


def apk_path(folder, module, *, unsigned=True):
    suffix = "-unsigned.apk" if unsigned else ".apk"
    return folder / module / "build/outputs/apk/release" / (
        module + "-release" + suffix
    )


def cert_from_apk(path):
    result = run(["apksigner", "verify", "--verbose", "--print-certs", str(path)])
    text = result.decode("utf-8", errors="replace")
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", text)
    if not match:
        stop("APK signer certificate missing")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in text:
        stop("APK signature scheme v2 verification failed")
    return match.group(1).lower()


def identity(path, package, version_code):
    text = run(["aapt", "dump", "badging", str(path)]).decode("utf-8", errors="replace")
    match = re.search(
        r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
        text, re.MULTILINE
    )
    expected_name = "1.0.0" if version_code == 4 else "1.0.1"
    if match is None or match.groups() != (package, str(version_code), expected_name):
        stop("APK package or version mismatch: " + package)


def props():
    file = ROOT / "keystore.properties"
    if not file.is_file() or file.is_symlink():
        stop("Missing or symlinked keystore.properties")
    ignored = subprocess.run(
        ["git", "-C", str(ROOT), "check-ignore", "-q", "keystore.properties"],
        check=False
    )
    if ignored.returncode:
        stop("keystore.properties is not ignored by Git")
    values = {}
    for line in file.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" in line:
            key, val = line.split("=", 1)
            values[key.strip()] = val.strip()
    for key in ("storeFile", "storePassword", "keyAlias", "keyPassword"):
        if not values.get(key):
            stop("Missing signing configuration property: " + key)
    targets = set()
    for module, *_ in APPS:
        raw = Path(values["storeFile"])
        targets.add((raw if raw.is_absolute() else ROOT / module / raw).resolve())
    if len(targets) != 1:
        stop("Release modules do not resolve to the same keystore")
    keystore = targets.pop()
    for label, candidate in (("Signing properties", file), ("Keystore", keystore)):
        if not candidate.is_file() or candidate.stat().st_uid != os.getuid():
            stop(label + " file missing or not owned by current user")
        if stat.S_IMODE(candidate.stat().st_mode) & 0o077:
            stop(label + " permissions too broad")
    # Reconfirm the alias certificate without exposing the key or password.
    exported = run(
        ["keytool", "-exportcert", "-keystore", str(keystore),
         "-alias", values["keyAlias"]],
        password=(values["storePassword"] + "\n").encode(),
        timeout=30
    )
    if hashlib.sha256(exported).hexdigest() != CERT:
        stop("Configured signing alias certificate differs from v1.0.0")
    return values, keystore


def preflight():
    print("===== SIGNING SAFETY CHECKS =====", flush=True)
    os.umask(0o077)
    for executable in ("git", "keytool", "apksigner", "apksigcopier", "aapt"):
        if shutil.which(executable) is None:
            stop("Required tool unavailable: " + executable)
    if run(["git", "-C", str(ROOT), "branch", "--show-current"]).decode().strip() != "release/v1.0.1-rc1":
        stop("Wrong private release branch")
    if run(["git", "-C", str(ROOT), "rev-parse", "HEAD"]).decode().strip() != COMMIT:
        stop("Unexpected private source commit")
    if run(["git", "-C", str(ROOT), "status", "--porcelain"]).strip():
        stop("Private working tree is not clean")
    if not FDROID_COMMIT.is_file() or FDROID_COMMIT.read_text().strip() != COMMIT:
        stop("Final container build commit does not match reviewed source")
    if not BASE.is_dir():
        stop("F-Droid staging parent missing")
    for module, _, package, expected_unsigned, expected_old in APPS:
        for source in (MINT, FDROID):
            apk = apk_path(source, module)
            if not apk.is_file() or sha(apk) != expected_unsigned:
                stop("Unsigned reproducible APK hash mismatch: " + module)
            identity(apk, package, 5)
        original = apk_path(ROOT, module, unsigned=False)
        if not original.is_file() or sha(original) != expected_old:
            stop("Original v1.0.0 reference changed: " + module)
        identity(original, package, 4)
        if cert_from_apk(original) != CERT:
            stop("Original v1.0.0 certificate mismatch: " + module)
        print("PASS: Original signer and both unsigned references:", module, flush=True)
    values, keystore = props()
    print("PASS: Existing signing alias certificate and restricted keystore", flush=True)
    return values, keystore


def sign(values, keystore):
    stage = Path(tempfile.mkdtemp(prefix="v101-signed-stage.", dir=BASE))
    stage.chmod(0o700)
    marker = stage / "INCOMPLETE.txt"
    marker.write_text("Signing or verification incomplete; do not publish.\n")
    print("===== SIGN FOUR APKs INTO ISOLATED STAGING =====", flush=True)
    print("Staging directory:", stage, flush=True)
    hashes = []
    try:
        for module, label, package, _, _ in APPS:
            original = apk_path(FDROID, module)
            output = stage / ("Nefarious-Sentinel-" + label + "-v1.0.1.apk")
            secret_input = (
                values["storePassword"] + "\n" +
                values["keyPassword"] + "\n"
            ).encode()
            run([
                "apksigner", "sign",
                "--ks", str(keystore),
                "--ks-key-alias", values["keyAlias"],
                "--ks-pass", "stdin",
                "--key-pass", "stdin",
                "--out", str(output),
                str(original),
            ], password=secret_input)
            if not output.is_file() or cert_from_apk(output) != CERT:
                stop("Signed output certificate validation failed: " + module)
            identity(output, package, 5)
            for ref in (apk_path(MINT, module), original):
                run(["apksigcopier", "compare", "--unsigned",
                     str(output), str(ref)])
            output.chmod(0o600)
            hashes.append(sha(output) + "  " + output.name)
            print("PASS: Signed certificate, v1.0.1 identity and APK contents:",
                  module, flush=True)
        (stage / "SHA256SUMS").write_text("\n".join(hashes) + "\n")
        (stage / "SOURCE_COMMIT.txt").write_text(COMMIT + "\n")
        marker.unlink()
        print("PASS: Four signed APKs staged and verified", flush=True)
        print("Protected staging:", stage, flush=True)
        print("NOT PUBLISHED: No APKs installed; no release or tag changed.")
    except Exception:
        print("STOP: Signing or verification failed. Incomplete staging:", stage,
              file=sys.stderr, flush=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true")
    group.add_argument("--sign", action="store_true")
    args = parser.parse_args()
    values, keystore = preflight()
    if args.check:
        print("PASS: Read-only preflight; no APKs signed, files changed or published.")
    else:
        sign(values, keystore)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        print("STOP:", str(exc), file=sys.stderr)
        sys.exit(1)
