#!/usr/bin/env python3
"""Read-only Kotlin pre-R8 Controller bytecode comparison for v1.0.0.

Compare two independently compiled Controller trees using the same local
javap executable. Shows which classfiles and javap disassemblies differ.
Disassembly equivalence is NOT proof of identical executable semantics.
No build, network, APK or Git operation; never touches devices or keys.
"""
from __future__ import annotations

import difflib
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import sys

HOME = Path.home()
MINT = Path("/tmp/tmp.SpccmwUNiU/src/controller/build/tmp/kotlin-classes/release")
FDROID = (
    HOME / "Projects/fdroid-sentinel-validation/fdroiddata/build/"
    "com.nefarioussecurity.sentinel.controller/controller/build/tmp/"
    "kotlin-classes/release"
)
SPECIAL = (
    "com/nefarioussecurity/sentinel/controller/ui/SentinelControllerAppKt.class",
    "com/nefarioussecurity/sentinel/controller/feature/about/"
    "AboutScreenKt$AboutSection$1.class",
)


def fingerprint(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def disassemble(path: Path) -> str:
    result = subprocess.run(
        ["javap", "-c", "-p", "-s", str(path)],
        check=True, text=True, capture_output=True, timeout=90,
    )
    # The numeric #123 constant-pool references can move even when the
    # referenced constants do not. Preserve symbolic javap comments.
    return result.stdout


def normalize_indexes(value: str) -> str:
    return re.sub(r"#\d+", "#INDEX", value)


def main() -> int:
    print("===== CONTROLLER KOTLIN / COMPOSE PRE-R8 DIAGNOSTIC =====")
    if not shutil.which("javap"):
        print("STOP: javap unavailable; install or select a local JDK")
        return 2
    if not MINT.is_dir() or not FDROID.is_dir():
        print("STOP: One of the two previously built output directories is absent.")
        print(f"Mint:    {MINT}: {MINT.is_dir()}")
        print(f"F-Droid: {FDROID}: {FDROID.is_dir()}")
        return 2
    a = {p.relative_to(MINT): p for p in MINT.rglob("*.class")}
    b = {p.relative_to(FDROID): p for p in FDROID.rglob("*.class")}
    names = sorted(set(a) | set(b))
    absent = [n for n in names if n not in a or n not in b]
    mismatched = [n for n in names if n in a and n in b
                  and fingerprint(a[n]) != fingerprint(b[n])]
    print(f"Mint classes: {len(a)}")
    print(f"F-Droid classes: {len(b)}")
    print(f"Missing on one side: {len(absent)}")
    print(f"Bytewise different: {len(mismatched)}")
    if absent:
        for name in absent[:12]:
            print(f"  MISSING: {name}")
    if not mismatched:
        print("PASS: All pre-R8 Kotlin class files match.")
        return 0

    summary = []
    for rel in mismatched:
        name = rel.as_posix()
        try:
            original = disassemble(a[rel])
            rebuilt = disassemble(b[rel])
        except (subprocess.SubprocessError, OSError) as error:
            print(f"FAIL: javap unable to read {name}: {error}")
            return 2
        raw_match = original == rebuilt
        normalized_match = normalize_indexes(original) == normalize_indexes(rebuilt)
        summary.append((name, raw_match, normalized_match, original, rebuilt))
        if raw_match:
            status = "DISASSEMBLY MATCHES (class metadata / attributes differ)"
        elif normalized_match:
            status = "ONLY CONSTANT-POOL INDEX REFERENCES DIFFER IN javap OUTPUT"
        else:
            status = "DISASSEMBLED INSTRUCTIONS OR SIGNATURES DIFFER"
        print(f"  {status}: {name}")

    print("\n===== FOCUSED JAVAP DIFFERENCES =====")
    primary = next(
        (item for item in summary if item[0] == SPECIAL[0]),
        next((item for item in summary if not item[2]), summary[0]),
    )
    name, exact, norm, orig, rebuilt = primary
    print(f"Class: {name}")
    if exact:
        print("No disassembly differences in this class.")
    else:
        left = normalize_indexes(orig).splitlines()
        right = normalize_indexes(rebuilt).splitlines()
        diff = difflib.unified_diff(
            left, right, fromfile="Mint/javap", tofile="F-Droid/javap",
            lineterm="",
        )
        count = 0
        for line in diff:
            print(line)
            count += 1
            if count >= 90:
                print("... further differences omitted (output capped at 90 lines)")
                break
        if count == 0:
            print("No differences after normalizing constant-pool indices.")

    print("\n===== INTERPRETATION =====")
    print("Only javap disassembly was compared, NOT all JVM class attributes.")
    print("If javap matches, inspect metadata/annotations/SMAP next.")
    print("If instructions differ, investigate Kotlin/Compose compilation inputs.")
    print("READ ONLY: No file, build, APK, device or signing changes made.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
