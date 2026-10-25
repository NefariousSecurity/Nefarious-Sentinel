#!/usr/bin/env python3
"""Read-only comparison of the original signed Controller APK and F-Droid build.

Reports ZIP-member hashes (especially classes.dex and baseline.prof), without
modifying APKs, source, signing keys, devices, F-Droid metadata or temp files.

Run from any directory:
    python3 ~/Projects/Nefarious-Sentinel/scripts/fdroid-controller-diff.py
"""
from __future__ import annotations

import glob
import hashlib
import os
from pathlib import Path
import sys
import zipfile

HOME = Path.home()
WORK = Path(os.environ.get(
    "FDROID_TEST_WORKSPACE",
    str(HOME / "Projects/fdroid-sentinel-validation"),
))
SIGNED = HOME / "Projects/oblivion-v2/controller/build/outputs/apk/release/controller-release.apk"
FDROID_PKG = "com.nefarioussecurity.sentinel.controller"
EXPECTED_SIGNED_SHA256 = "fd7fc3e53676a16387d2e3fa933bd0dc88a767580e65cab368582f9cad20fa80"

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def load_apk(path: Path) -> tuple[dict[str, tuple[str, int]], int, str]:
    with zipfile.ZipFile(path) as z:
        members = {
            info.filename: (sha(z.read(info)), info.file_size)
            for info in z.infolist()
            if not info.is_dir()
        }
    return members, path.stat().st_size, sha(path.read_bytes())

def output(label: str, path: Path, base: dict | None) -> None:
    if not path.is_file():
        print(f"NOT FOUND: {label}: {path}")
        return
    try:
        members, size, whole_hash = load_apk(path)
    except (OSError, zipfile.BadZipFile) as err:
        print(f"ERROR: {label}: {path}: {err}")
        return
    print(f"\\n===== {label} =====")
    print(f"Path: {path}")
    print(f"APK bytes: {size:,}; file SHA-256: {whole_hash}")
    relevant = sorted(
        name for name in members
        if name.endswith(".dex") or name.startswith("assets/dexopt/")
    )
    for name in relevant:
        digest, size = members[name]
        print(f"  {name}: {size:,} bytes SHA256 {digest}")
    if base is None:
        return
    changed = [name for name in sorted(set(base) | set(members))
               if base.get(name) != members.get(name)]
    print(f"ZIP contents differ in {len(changed)} member(s), out of "
          f"{len(set(base) | set(members))}")
    for name in changed[:60]:
        old = base.get(name)
        new = members.get(name)
        if old is None:
            detail = "only in this build"
        elif new is None:
            detail = "missing from this build"
        else:
            detail = f"{old[1]:,} -> {new[1]:,} bytes"
        print(f"  DIFF: {name}: {detail}")
    if len(changed) > 60:
        print(f"  ... and {len(changed)-60} additional differences")
    if not changed:
        print("PASS: All uncompressed APK ZIP members have matching contents.")
        print("NOTE: APK ZIP metadata, entry ordering and signatures may still differ.")

def main() -> int:
    print("Sentinel Controller F-Droid build diagnostics (READ ONLY)")
    if not SIGNED.is_file():
        print(f"STOP: Original signed Controller APK not found: {SIGNED}")
        return 2
    ref, size, checksum = load_apk(SIGNED)
    if checksum != EXPECTED_SIGNED_SHA256:
        print("STOP: Reference Controller does not match published SHA256")
        print(f"Actual: {checksum}")
        return 2
    output("PUBLISHED DEVELOPER-SIGNED REFERENCE", SIGNED, None)

    unsigned_paths = [
        WORK / "fdroiddata/unsigned" / f"{FDROID_PKG}_4.apk",
        WORK / "fdroiddata/build" / FDROID_PKG /
            "controller/build/outputs/apk/release/controller-release-unsigned.apk",
    ]
    found = False
    for path in unsigned_paths:
        if path.is_file():
            found = True
            output("F-DROID UNSIGNED BUILD", path, ref)
            break
    if not found:
        print("NOT FOUND: F-Droid Controller unsigned build in expected locations.")
        print("Searching only Controller-specific Gradle output directory:")
        folder = WORK / "fdroiddata/build" / FDROID_PKG / "controller/build/outputs/apk/release"
        if folder.is_dir():
            for apk in sorted(folder.glob("*.apk")):
                output("F-DROID GRADLE APK", apk, ref)

    scratch_pattern = "/tmp/tmp.*/src/controller/build/outputs/apk/release/*-unsigned.apk"
    scratch = [Path(p) for p in glob.glob(scratch_pattern) if Path(p).is_file()]
    if scratch:
        for path in sorted(scratch, key=lambda p: p.stat().st_mtime, reverse=True)[:2]:
            output("EARLIER MINT UNSIGNED BUILD", path, ref)
    else:
        print("Earlier Mint scratch APK not found (optional).")
    print("\\nThis diagnostic did not modify files or contact devices.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
