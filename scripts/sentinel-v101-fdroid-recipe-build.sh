#!/usr/bin/env bash
# Exercise a real fdroidserver build recipe in the F-Droid buildserver image.
# No signing keys, private source, APK installation, or repository publication.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ $# -eq 1 ]] || die 'Usage: bash scripts/sentinel-v101-fdroid-recipe-build.sh --check|--run'
case "$1" in --check|--run) MODE="$1";; *) die 'Unknown mode';; esac

REPO="$HOME/Projects/Nefarious-Sentinel"
BASE="$HOME/Projects/fdroid-sentinel-validation"
LINT="$BASE/v101-fdroid-lint.lDsGcnzF"
STAGE="$BASE/v101-signed-stage.e6m4env9"
IMAGE="registry.gitlab.com/fdroid/fdroidserver:buildserver"
SOURCE="10a5324be20cf1fe8714b9edce030f607749c10c"
PKG="com.nefarioussecurity.sentinel"

[[ -d "$REPO/.git" && -d "$BASE" && -d "$LINT" && -d "$STAGE" ]] ||
  die 'Expected prevalidated directories missing'
[[ "$(git -C "$REPO" branch --show-current)" == 'release/v1.0.1-fdroid-prep' ]] ||
  die 'Unexpected public branch'
[[ -z "$(git -C "$REPO" status --porcelain)" ]] || die 'Dirty public Git working tree'
[[ "$(git -C "$REPO" remote get-url origin)" == 'https://github.com/NefariousSecurity/Nefarious-Sentinel.git' ]] ||
  die 'Unexpected GitHub remote'
[[ "$(git -C "$REPO" rev-parse 'v1.0.1^{}')" == "$SOURCE" ]] ||
  die 'Unexpected public source tag'
[[ -f "$LINT/config.yml" && -s "$LINT/config/categories.yml" ]] ||
  die 'Missing official F-Droid configuration'
[[ -f "$LINT/metadata/$PKG.yml" ]] || die 'Validated scratch metadata missing'
[[ -f "$LINT/fdroidserver/fdroid" ]] || die 'Validated fdroidserver checkout missing'
[[ "$(cat "$LINT/FDROIDSERVER_COMMIT.txt")" == '2d1e622fbbce7eec379453983435e5b14bea22b5' ]] ||
  die 'Unexpected F-Droid tool source'
[[ "$(cat "$LINT/FDROIDDATA_COMMIT.txt")" == '3f55022e917d1c20d5ba3b360ca4a1dfef19efb6' ]] ||
  die 'Unexpected F-Droid metadata source'
[[ -f "$LINT/lint.log" ]] || die 'Lint evidence missing'
grep -Fxq "PASS: $PKG lint" "$LINT/lint.log" ||
  die 'Core lint pass not recorded'
grep -Fq "    commit: $SOURCE" "$LINT/metadata/$PKG.yml" ||
  die 'Scratch source commit does not match reviewed source'
if grep -q '^    disable:' "$LINT/metadata/$PKG.yml"; then
  die 'Scratch recipe unexpectedly disabled'
fi
( cd "$STAGE" && sha256sum -c SHA256SUMS ) ||
  die 'Original signed release hashes do not verify'
for cmd in git docker sha256sum grep cmp; do
  command -v "$cmd" >/dev/null || die "Missing $cmd"
done
docker info >/dev/null 2>&1 || die 'Docker daemon inaccessible'
docker image inspect "$IMAGE" >/dev/null 2>&1 || die 'Official F-Droid image not locally available'
echo 'PASS: Verified lint workspace, reviewed source and released APKs'
if [[ "$MODE" == --check ]]; then
  echo 'READ ONLY: No recipe build, signing, installation or submission'
  exit 0
fi

WORK="$(mktemp -d "$BASE/v101-fdroid-recipe-core.XXXXXXXX")"
cp "$LINT/config.yml" "$WORK/config.yml"
cp -a "$LINT/config" "$WORK/config"
mkdir -p "$WORK/metadata"
cp "$LINT/metadata/$PKG.yml" "$WORK/metadata/$PKG.yml"
printf '%s\n' "$SOURCE" > "$WORK/REVIEWED_SOURCE_COMMIT.txt"
printf '%s\n' "$IMAGE" > "$WORK/IMAGE.txt"
echo "===== OFFICIAL F-DROID RECIPE BUILD: $PKG ====="
echo "Scratch workspace: $WORK"
echo 'Original public metadata remains disabled; builds isolated in Docker.'
docker run --rm \
  --user vagrant --env HOME=/home/vagrant \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --volume "$WORK:/build:rw" \
  --volume "$LINT/fdroidserver:/home/vagrant/fdroidserver:ro" \
  --workdir /build \
  --entrypoint /bin/bash "$IMAGE" -ec '
    . /etc/profile
    export HOME=/home/vagrant
    export PATH="/home/vagrant/fdroidserver:$PATH"
    export PYTHONPATH="/home/vagrant/fdroidserver"
    export ANDROID_HOME=/opt/android-sdk
    [[ -d "$ANDROID_HOME" && -w "$ANDROID_HOME" ]] ||
      { echo "STOP: Container Android SDK unavailable"; exit 1; }
    command -v sdkmanager >/dev/null ||
      { echo "STOP: sdkmanager missing"; exit 1; }
    if [[ ! -f "$ANDROID_HOME/platforms/android-34/android.jar" ||
          ! -d "$ANDROID_HOME/build-tools/34.0.0" ]]; then
      sdkmanager --sdk_root="$ANDROID_HOME" \
        "platforms;android-34" "build-tools;34.0.0" "platform-tools" ||
        { echo "STOP: SDK provisioning failed"; exit 1; }
    fi
    [[ -f "$ANDROID_HOME/platforms/android-34/android.jar" &&
       -d "$ANDROID_HOME/build-tools/34.0.0" ]] ||
      { echo "STOP: Required Android SDK is incomplete"; exit 1; }
    echo "===== F-DROID METADATA ====="
    fdroid readmeta
    fdroid lint com.nefarioussecurity.sentinel
    echo "===== F-DROID LOCAL RECIPE BUILD ====="
    fdroid build --test --stop --no-tarball com.nefarioussecurity.sentinel:5
    echo "PASS: F-Droid Core metadata build command completed"
  ' 2>&1 | tee "$WORK/build.log"

echo "===== F-DROID CORE RECIPE BUILD FINISHED ====="
echo "Build log: $WORK/build.log"
echo 'Inspect output APK hash and compare to upstream binary before declaring verification complete.'
echo 'NO APK SIGNING, INSTALLATION, TAG CHANGE, OR PUBLICATION'
