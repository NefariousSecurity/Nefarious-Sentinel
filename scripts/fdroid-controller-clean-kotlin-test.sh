#!/usr/bin/env bash
# Recompile Controller Kotlin from the immutable v1.0.0 public source,
# with the Gradle build cache disabled, in a NEW scratch directory.
# Compare pre-R8 .class hashes with preserved Mint and F-Droid output.
# No APK signing, phone access, release edits or overwrites.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
for cmd in git tar sed awk python3 java sha256sum mktemp; do
  command -v "$cmd" >/dev/null || die "Missing command: $cmd"
done
ROOT="$(git rev-parse --show-toplevel)"
[[ -d "$ROOT/.git" ]] || die "Run inside the public Sentinel Git clone"
[[ "$(git -C "$ROOT" remote get-url origin)" == \
  "https://github.com/NefariousSecurity/Nefarious-Sentinel.git" ]] ||
  die "Unexpected Git origin"
TAG_SHA="$(git -C "$ROOT" rev-parse --verify 'refs/tags/v1.0.0^{commit}')" ||
  die "v1.0.0 tag not found"
[[ "$TAG_SHA" == 4a7c3a55623203116dda4d2aa293baf8c0d608b5 ]] ||
  die "Unexpected v1.0.0 source commit: $TAG_SHA"

MINT="/tmp/tmp.SpccmwUNiU/src/controller/build/tmp/kotlin-classes/release"
FDROID="$HOME/Projects/fdroid-sentinel-validation/fdroiddata/build/com.nefarioussecurity.sentinel.controller/controller/build/tmp/kotlin-classes/release"
[[ -d "$MINT" && -d "$FDROID" ]] ||
  die "Original Mint and F-Droid class outputs are required for comparison"

SDK="$(printenv ANDROID_HOME || true)"
[[ -n "$SDK" ]] || SDK="$(printenv ANDROID_SDK_ROOT || true)"
if [[ -z "$SDK" && -f "$HOME/Projects/oblivion-v2/local.properties" ]]; then
  SDK="$(awk -F= '/^[[:space:]]*sdk[.]dir[[:space:]]*=/ {
    sub(/^[^=]*=/, "")
    sub(/^[[:space:]]+/, "")
    sub(/[[:space:]]+$/, "")
    print
    exit
  }' "$HOME/Projects/oblivion-v2/local.properties" | tr -d '\r')"
fi
if [[ -z "$SDK" ]]; then
  for candidate in "$HOME/Android/Sdk" /usr/lib/android-sdk /opt/android-sdk; do
    if [[ -d "$candidate/platforms/android-34" ]]; then
      SDK="$candidate"
      break
    fi
  done
fi
[[ -n "$SDK" && -d "$SDK/platforms/android-34" ]] ||
  die "Android SDK platform android-34 not located"
export ANDROID_HOME="$SDK"
if [[ -n "$(printenv ANDROID_SDK_ROOT || true)" &&
  "$ANDROID_SDK_ROOT" != "$ANDROID_HOME" ]]; then
  die "ANDROID_SDK_ROOT and ANDROID_HOME disagree"
fi

WORK="$(mktemp -d /tmp/sentinel-kotlin-clean-XXXXXXXX)"
SRC="$WORK/src"
mkdir -p "$SRC"
echo "Public immutable source commit: $TAG_SHA"
echo "Android SDK: $ANDROID_HOME"
echo "SCRATCH DIRECTORY, PRESERVED: $WORK"
java -version
git -C "$ROOT" archive --format=tar v1.0.0 | tar -xf - -C "$SRC"
[[ -x "$SRC/gradlew" ]] || die "Gradle wrapper missing from tagged source"
for module in app controller motion-helper sms-helper; do
  gradle="$SRC/$module/build.gradle"
  [[ -f "$gradle" ]] || die "Module Gradle file missing: $module"
  occurrences="$(grep -Ec '^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$' "$gradle" || true)"
  [[ "$occurrences" == 1 ]] || die "Unexpected signing config count for $module"
  sed -i '/^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$/d' "$gradle"
done
[[ ! -e "$SRC/keystore.properties" && ! -e "$SRC/anonymous_key.keystore" ]] ||
  die "Unexpected signing material in source archive"

echo "===== FRESH CONTROLLER KOTLIN COMPILATION, NO GRADLE TASK CACHE ====="
(
  cd "$SRC"
  ./gradlew --no-daemon --no-build-cache --rerun-tasks \
    :controller:compileReleaseKotlin
) || die "Fresh Kotlin compilation failed. Preserve $WORK for investigation"

FRESH="$SRC/controller/build/tmp/kotlin-classes/release"
[[ -d "$FRESH" ]] || die "Fresh class output missing: $FRESH"
python3 - "$MINT" "$FDROID" "$FRESH" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

mint, fdroid, fresh = map(Path, sys.argv[1:])
def files(folder):
    return {p.relative_to(folder).as_posix(): sha256(p.read_bytes()).hexdigest()
            for p in folder.rglob("*.class")}
a, b, c = files(mint), files(fdroid), files(fresh)
print("===== THREE-WAY PRE-R8 KOTLIN COMPARISON =====")
for label, d in (("Mint original", a), ("F-Droid", b), ("Mint freshly compiled", c)):
    print(f"{label}: {len(d)} class files")
def compare(label, x, y):
    keys = sorted(set(x) | set(y))
    changed = [key for key in keys if x.get(key) != y.get(key)]
    print(f"{label}: {len(changed)} differing or missing of {len(keys)}")
    for key in changed[:12]:
        print(f"  DIFF: {key}")
    return changed
compare("Original Mint vs F-Droid", a, b)
ac = compare("Original Mint vs fresh Mint", a, c)
bc = compare("F-Droid vs fresh Mint", b, c)
if not ac:
    print("RESULT: Fresh compilation MATCHES original Mint class files")
elif not bc:
    print("RESULT: Fresh compilation MATCHES F-Droid class files")
else:
    print("RESULT: Fresh compilation differs from BOTH. Inspect toolchain inputs")
print("NOTE: Class-file match alone does not verify an entire APK")
print("Existing source, released APKs, keys, and device state remain unchanged")
PY
echo "Preserved diagnostic scratch directory: $WORK"
