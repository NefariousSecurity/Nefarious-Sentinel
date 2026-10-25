#!/usr/bin/env bash
# v1.0.1 pre-publication test in the official F-Droid buildserver image.
# --check only verifies prerequisites; --run builds four unsigned APKs
# from an isolated, sanitized source archive and compares against Mint.
# Not an official fdroiddata recipe or signed-Binaries verification.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ "$#" == 1 ]] || die "Usage: bash $0 --check|--run"
case "$1" in --check|--run) MODE="$1";; *) die "Unknown mode: $1";; esac
for cmd in git grep tar sed python3 docker mktemp; do
  command -v "$cmd" >/dev/null || die "Missing command: $cmd"
done
PRIVATE="$HOME/Projects/oblivion-v2"
BASE="$HOME/Projects/fdroid-sentinel-validation"
FIRST="$BASE/v101-clean.Fc2Z6qIm/src"
COMMIT="cd76b4b"
IMAGE="registry.gitlab.com/fdroid/fdroidserver:buildserver"

[[ -d "$PRIVATE/.git" && -d "$FIRST" ]] || die "Expected private source or Mint reference missing"
[[ "$(git -C "$PRIVATE" branch --show-current)" == "release/v1.0.1-rc1" ]] ||
  die "Private clone not on the release candidate branch"
[[ -z "$(git -C "$PRIVATE" status --porcelain)" ]] ||
  die "Private working tree not clean"
EXPECTED="$(git -C "$PRIVATE" rev-parse --verify "$COMMIT^{commit}")" ||
  die "Reviewed release commit unavailable"
[[ "$(git -C "$PRIVATE" rev-parse HEAD)" == "$EXPECTED" ]] ||
  die "Private HEAD differs from reviewed commit"
git -C "$PRIVATE" show "$EXPECTED:build.gradle" |
  grep -q 'sentinelVersionCode = 5' || die "Expected versionCode 5"
git -C "$PRIVATE" show "$EXPECTED:build.gradle" |
  grep -q 'sentinelVersionName = "1.0.1"' || die "Expected versionName 1.0.1"
for MODULE in app controller motion-helper sms-helper; do
  [[ -f "$FIRST/$MODULE/build/outputs/apk/release/$MODULE-release-unsigned.apk" ]] ||
    die "Missing pristine Mint reference: $MODULE"
done
docker info >/dev/null 2>&1 ||
  die "Docker daemon inaccessible; do not modify Docker socket permissions"

if [[ "$MODE" == --check ]]; then
  echo "PASS: Private branch, commit, version and clean tree checked"
  echo "PASS: Four pristine unsigned Mint references available"
  echo "PASS: Docker daemon available"
  echo "Image: $IMAGE"
  echo "READ ONLY: No container started and nothing modified"
  exit 0
fi

WORK="$(mktemp -d "$BASE/v101-fdroid.XXXXXXXX")"
SRC="$WORK/src"
mkdir -p "$SRC"
printf '%s\n' "$EXPECTED" > "$WORK/SOURCE_COMMIT.txt"
echo "Workspace: $WORK"
git -C "$PRIVATE" archive --format=tar "$EXPECTED" | tar -xf - -C "$SRC"
[[ -x "$SRC/gradlew" ]] || die "Gradle wrapper unavailable"
for MODULE in app controller motion-helper sms-helper; do
  FILE="$SRC/$MODULE/build.gradle"
  [[ -f "$FILE" ]] || die "Missing scratch Gradle config: $MODULE"
  COUNT="$(grep -Ec '^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$' "$FILE" || true)"
  [[ "$COUNT" == 1 ]] || die "Unexpected signing configuration: $MODULE"
  sed -i '/^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$/d' "$FILE"
done
if find "$SRC" -type f \( -iname '*.keystore' -o -iname '*.jks' -o \
  -iname '*.p12' -o -iname '*.pfx' -o -name 'keystore.properties' \) \
  -print -quit | grep -q .; then
  die "Potential signing credentials found in scratch source"
fi

echo "===== BUILD IN OFFICIAL F-DROID BUILDSERVER IMAGE ====="
echo "Mounting only sanitized scratch source, no private Git tree or signing keys"
docker run --rm --user vagrant --env HOME=/home/vagrant \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --volume "$SRC:/build:rw" --workdir /build \
  --entrypoint /bin/bash "$IMAGE" -ec '
    . /etc/profile
    export HOME=/home/vagrant
    # The official F-Droid buildserver image can start with an EMPTY
    # Android SDK. Provision API 34 and build-tools 34 INSIDE this
    # ephemeral container; never modify the SDK on the Mint host.
    export ANDROID_HOME=/opt/android-sdk
    [[ -d "$ANDROID_HOME" && -w "$ANDROID_HOME" ]] ||
      { echo "STOP: F-Droid Android SDK is not writable"; exit 1; }
    command -v sdkmanager >/dev/null ||
      { echo "STOP: sdkmanager unavailable in F-Droid container"; exit 1; }
    echo "===== PROVISION SDK IN ISOLATED CONTAINER ====="
    if [[ ! -f "$ANDROID_HOME/platforms/android-34/android.jar" ||
          ! -d "$ANDROID_HOME/build-tools/34.0.0" ]]; then
      # Do not auto-accept SDK licences. sdkmanager must stop if
      # previously accepted licences do not cover these packages.
      sdkmanager --sdk_root="$ANDROID_HOME" \
        "platforms;android-34" "build-tools;34.0.0" "platform-tools" ||
        { echo "STOP: SDK installation failed; inspect network/licences"; exit 1; }
    fi
    [[ -f "$ANDROID_HOME/platforms/android-34/android.jar" &&
      -d "$ANDROID_HOME/build-tools/34.0.0" ]] ||
      { echo "STOP: Required SDK packages unavailable"; exit 1; }
    java -version
    echo "Container Android SDK: $ANDROID_HOME"
    echo "SDK platform: android-34; build-tools: 34.0.0"
    ./gradlew --no-daemon --no-build-cache --rerun-tasks \
      :app:assembleRelease :controller:assembleRelease \
      :motion-helper:assembleRelease :sms-helper:assembleRelease
  ' 2>&1 | tee "$WORK/build.log"

echo "===== COMPARE COMPLETE UNSIGNED APK BYTES ====="
python3 - "$FIRST" "$SRC" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys, zipfile
a, b = map(Path, sys.argv[1:])
failed = False
for module in ("app", "controller", "motion-helper", "sms-helper"):
    relative = Path(module, "build/outputs/apk/release",
                    f"{module}-release-unsigned.apk")
    mint, container = a / relative, b / relative
    if not mint.is_file() or not container.is_file():
        print(f"FAIL: {module} APK missing")
        failed = True
        continue
    x = sha256(mint.read_bytes()).hexdigest()
    y = sha256(container.read_bytes()).hexdigest()
    if x == y:
        print(f"PASS: {module}, SHA256 {x}")
        continue
    failed = True
    print(f"DIFFER: {module}")
    print(f"  Mint SHA256: {x}")
    print(f"  F-Droid container SHA256: {y}")
    with zipfile.ZipFile(mint) as za, zipfile.ZipFile(container) as zb:
        ma = {n: sha256(za.read(n)).digest() for n in za.namelist()}
        mb = {n: sha256(zb.read(n)).digest() for n in zb.namelist()}
    names = [n for n in sorted(ma.keys() | mb.keys())
             if ma.get(n) != mb.get(n)]
    if names:
        print(f"  Different ZIP member contents: {len(names)}")
        for name in names[:15]:
            print(f"    {name}")
    else:
        print("  ZIP contents match; packaging or metadata differs.")
if failed:
    print("INCOMPLETE: Container builds not byte-identical to Mint.")
    sys.exit(1)
print("PASS: All four unsigned v1.0.1 APKs match the F-Droid container.")
print("This is NOT the final signed upstream-Binaries F-Droid verification.")
PY
echo "Preserved workspace: $WORK"
