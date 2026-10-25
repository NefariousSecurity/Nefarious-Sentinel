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
import struct
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

def dex_summary(apk: Path) -> tuple[dict[str, int], list[bytes], bytes]:
    """Read DEX header tables and raw MUTF-8 string bytes, with no writes."""
    with zipfile.ZipFile(apk) as z:
        blob = z.read("classes.dex")
    if not blob.startswith(b"dex\n") or len(blob) < 0x70:
        raise ValueError(f"Unsupported or truncated classes.dex in {apk}")
    fields = {
        "file_size": 0x20,
        "string_ids_size": 0x38,
        "type_ids_size": 0x40,
        "proto_ids_size": 0x48,
        "field_ids_size": 0x50,
        "method_ids_size": 0x58,
        "class_defs_size": 0x60,
    }
    stats = {name: struct.unpack_from("<I", blob, off)[0]
             for name, off in fields.items()}
    count = stats["string_ids_size"]
    ids_off = struct.unpack_from("<I", blob, 0x3c)[0]
    if count > 1000000 or ids_off + 4 * count > len(blob):
        raise ValueError("Malformed DEX string ID table")
    strings = []
    for i in range(count):
        data_off = struct.unpack_from("<I", blob, ids_off + 4 * i)[0]
        if data_off >= len(blob):
            raise ValueError("DEX string offset outside file")
        cursor = data_off
        for _ in range(5):  # ULEB128 encoded UTF-16 length
            v = blob[cursor]
            cursor += 1
            if v < 0x80:
                break
        else:
            raise ValueError("Malformed DEX string length")
        end = blob.find(b"\x00", cursor)
        if end == -1:
            raise ValueError("Unterminated DEX string")
        strings.append(blob[cursor:end])
    return stats, strings, blob


def compare_dex(reference: Path, built: Path) -> None:
    print("\n===== DEX STRUCTURE COMPARISON (READ ONLY) =====")
    try:
        old_stats, old_strings, old_bytes = dex_summary(reference)
        new_stats, new_strings, new_bytes = dex_summary(built)
    except (OSError, ValueError, zipfile.BadZipFile, KeyError, IndexError) as error:
        print(f"Cannot inspect DEX tables: {error}")
        return
    for key in old_stats:
        old, new = old_stats[key], new_stats[key]
        print(f"{key}: published={old:,}, F-Droid={new:,}"
              + (" [DIFFERENT]" if old != new else ""))
    old_set, new_set = set(old_strings), set(new_strings)
    removed, added = sorted(old_set - new_set), sorted(new_set - old_set)
    print(f"DEX string pool: published={len(old_strings):,}, "
          f"F-Droid={len(new_strings):,}; "
          f"removed={len(removed)}, added={len(added)}")
    for label, entries in (("ONLY PUBLISHED", removed),
                           ("ONLY F-DROID", added)):
        for item in entries[:12]:
            print(f"  {label}: {item[:140]!r}")
        if len(entries) > 12:
            print(f"  {label}: ... and {len(entries)-12} more strings")
    if not added and not removed and old_strings != new_strings:
        first = next(i for i, (a, b) in enumerate(zip(old_strings, new_strings))
                     if a != b)
        print(f"String pools contain the same values, but order differs "
              f"(first different index: {first})")
    if not added and not removed and old_strings == new_strings:
        print("String pools contain the same values in the same order.")
    n = min(len(old_bytes), len(new_bytes))
    first_differences = [i for i in range(0x70, n)
                         if old_bytes[i] != new_bytes[i]][:10]
    print(f"First DEX offsets differing after the header: "
          f"{[hex(x) for x in first_differences]}")
    print("Matching DEX tables or strings do not prove identical method code.")


def output(label: str, path: Path, base: dict | None) -> None:
    if not path.is_file():
        print(f"NOT FOUND: {label}: {path}")
        return
    try:
        members, size, whole_hash = load_apk(path)
    except (OSError, zipfile.BadZipFile) as err:
        print(f"ERROR: {label}: {path}: {err}")
        return
    print(f"\n===== {label} =====")
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
            compare_dex(SIGNED, path)
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
    print("\nThis diagnostic did not modify files or contact devices.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
