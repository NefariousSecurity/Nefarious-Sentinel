#!/usr/bin/env bash
# Verify public release-preparation source builds the exact already signed
# Sentinel v1.0.1 APK contents. Never signs, installs, tags or publishes.
# --check is read-only; --run extracts public commit into an isolated
# workspace and performs a clean no-build-cache Gradle release build.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ $# -eq 1 ]] || die 'Usage: bash scripts/sentinel-v101-public-source-verify.sh --check|--run'
case "$1" in --check|--run) MODE="$1";; *) die "Unsupported mode";; esac

ROOT="$(git rev-parse --show-toplevel)"
[[ "$ROOT" == "$HOME/Projects/Nefarious-Sentinel" ]] ||
  die "Expected public source checkout at ~/Projects/Nefarious-Sentinel"
[[ "$(git -C "$ROOT" branch --show-current)" == "release/v1.0.1-fdroid-prep" ]] ||
  die "Wrong public source branch"
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || die "Public working tree not clean"
REMOTE="$(git -C "$ROOT" remote get-url origin)"
[[ "$REMOTE" == "https://github.com/NefariousSecurity/Nefarious-Sentinel.git" ]] ||
  die "Unexpected Git remote"
BASE="$HOME/Projects/fdroid-sentinel-validation"
STAGE="$BASE/v101-signed-stage.e6m4env9"
[[ -d "$BASE" && -d "$STAGE" && ! -L "$STAGE" ]] || die "Signed release staging missing"
[[ ! -e "$STAGE/INCOMPLETE.txt" ]] || die "Signing stage incomplete"
[[ "$(cat "$STAGE/SOURCE_COMMIT.txt")" == "ccd6c1419440acc49d8909af4049c6482f77442e" ]] ||
  die "Wrong signed release candidate commit"
[[ "$(stat -c '%a' "$STAGE")" == 700 ]] || die "Signing staging permissions invalid"
grep -q 'sentinelVersionCode = 5' "$ROOT/build.gradle" || die "Version code mismatch"
grep -q 'sentinelVersionName = "1.0.1"' "$ROOT/build.gradle" || die "Version name mismatch"
for cmd in git tar sed sha256sum python3 apksigcopier apksigner aapt; do
  command -v "$cmd" >/dev/null || die "Missing $cmd"
done
(
 cd "$STAGE"
 sha256sum -c SHA256SUMS
)
for module in app controller motion-helper sms-helper; do
  [[ -f "$ROOT/$module/build.gradle" ]] || die "Missing $module Gradle file"
done

echo "PASS: Public v1.0.1 source branch clean, signed staging intact"
echo "Public commit: $(git -C "$ROOT" rev-parse HEAD)"
if [[ "$MODE" == --check ]]; then
  echo "READ ONLY: no build, signing, install, tag or publication"
  exit 0
fi

[[ -d /usr/lib/android-sdk/platforms/android-34 ]] || die "Mint SDK 34 missing"
export ANDROID_HOME=/usr/lib/android-sdk
WORK="$(mktemp -d "$BASE/v101-public-verify.XXXXXXXX")"
SRC="$WORK/src"
mkdir -p "$SRC"
git -C "$ROOT" rev-parse HEAD > "$WORK/PUBLIC_SOURCE_COMMIT.txt"
git -C "$ROOT" archive --format=tar HEAD | tar -xf - -C "$SRC"
for module in app controller motion-helper sms-helper; do
  gradle="$SRC/$module/build.gradle"
  count="$(grep -Ec '^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$' "$gradle" || true)"
  [[ "$count" == 1 ]] || die "Unexpected signing config in $module"
  sed -i '/^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$/d' "$gradle"
done
[[ ! -e "$SRC/keystore.properties" && ! -e "$SRC/anonymous_key.keystore" ]] ||
  die "Unexpected signing credentials in public source archive"
if find "$SRC" -type f \( -iname '*.jks' -o -iname '*.keystore' -o \
  -iname '*.p12' -o -iname '*.pfx' \) -print -quit | grep -q .; then
  die "Unexpected keystore-like file in public source archive"
fi
echo "===== BUILD COMMITTED PUBLIC SOURCE WITHOUT SIGNING ====="
(
 cd "$SRC"
 ./gradlew --no-daemon --no-build-cache --rerun-tasks \
   :app:assembleRelease :controller:assembleRelease \
   :motion-helper:assembleRelease :sms-helper:assembleRelease
) 2>&1 | tee "$WORK/build.log"

echo "===== VERIFY AGAINST SIGNED v1.0.1 BINARIES ====="
python3 - "$SRC" "$STAGE" <<'PY'
from pathlib import Path
from hashlib import sha256
import subprocess, sys
src, stage = map(Path, sys.argv[1:])
apps = [
 ("app","Core","com.nefarioussecurity.sentinel","feab9330016b9b05e095c3ed8fe6159106831f072021ce6d971c40b2fef2121e"),
 ("controller","Controller","com.nefarioussecurity.sentinel.controller","30d1a4a4ac2ae980ae6d22cc8a99d9b3567aa7e1b1cab887e08c15e8ebb75ba9"),
 ("motion-helper","Motion","com.nefarioussecurity.sentinel.motion","a01c0518d7d1e4199ef2c5f8d27c7bc02d81eba44698e64037f41736ca5dff09"),
 ("sms-helper","SMS","com.nefarioussecurity.sentinel.sms","1593dacdd99bcd679240947eb483fc9c92b3fd0b726939d6c5e03fc0af0d508e"),
]
for module, label, pkg, digest in apps:
    apk=src / module / "build/outputs/apk/release" / f"{module}-release-unsigned.apk"
    signed=stage / f"Nefarious-Sentinel-{label}-v1.0.1.apk"
    if not apk.is_file() or not signed.is_file():
        sys.exit(f"STOP: APK missing for {module}")
    if sha256(apk.read_bytes()).hexdigest() != digest:
        sys.exit(f"STOP: Public unsigned build SHA-256 differs: {module}")
    result=subprocess.run(
        ["apksigcopier","compare","--unsigned",str(signed),str(apk)],
        stdout=subprocess.PIPE,stderr=subprocess.PIPE
    )
    if result.returncode:
        sys.exit(f"STOP: Public source APK differs from staged signed APK: {module}")
    print(f"PASS: {pkg} signed v1.0.1 content equals public source rebuild",flush=True)
print("PASS: Public candidate source produces all four signed release contents")
print("No signing, installation, tag or publication occurred.")
PY
echo "Preserved public source verification workspace: $WORK"
