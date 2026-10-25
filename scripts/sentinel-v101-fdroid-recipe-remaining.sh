#!/usr/bin/env bash
# Validate the three remaining official fdroidserver v1.0.1 recipes.
# No signing keys, private source, APK installation, or repository publication.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ $# -eq 1 ]] || die 'Usage: bash scripts/sentinel-v101-fdroid-recipe-remaining.sh --check|--run'
case "$1" in --check|--run) MODE="$1";; *) die 'Unknown mode';; esac

REPO="$HOME/Projects/Nefarious-Sentinel"
BASE="$HOME/Projects/fdroid-sentinel-validation"
LINT="$BASE/v101-fdroid-lint.lDsGcnzF"
STAGE="$BASE/v101-signed-stage.e6m4env9"
IMAGE="registry.gitlab.com/fdroid/fdroidserver:buildserver"
SOURCE="10a5324be20cf1fe8714b9edce030f607749c10c"
PKGS=(com.nefarioussecurity.sentinel.controller com.nefarioussecurity.sentinel.motion com.nefarioussecurity.sentinel.sms)

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
[[ -f "$LINT/fdroidserver/fdroid" ]] || die "F-Droid tooling missing"
[[ "$(cat "$LINT/FDROIDSERVER_COMMIT.txt")" == "2d1e622fbbce7eec379453983435e5b14bea22b5" ]] || die "Unexpected fdroidserver revision"
[[ "$(cat "$LINT/FDROIDDATA_COMMIT.txt")" == "3f55022e917d1c20d5ba3b360ca4a1dfef19efb6" ]] || die "Unexpected fdroiddata revision"
[[ -f "$LINT/lint.log" ]] || die "F-Droid lint evidence unavailable"
for pkg in "${PKGS[@]}"; do
  draft="$REPO/fdroid-candidates/metadata/$pkg.yml"
  scratch="$LINT/metadata/$pkg.yml"
  [[ -f "$draft" && -f "$scratch" ]] || die "Missing draft/scratch metadata: $pkg"
  grep -Fxq "PASS: $pkg lint" "$LINT/lint.log" || die "Official lint result missing: $pkg"
  grep -Fq "    commit: $SOURCE" "$draft" || die "Wrong source commit: $pkg"
  grep -Fq "    versionCode: 5" "$draft" || die "Wrong versionCode: $pkg"
  grep -Fq "    disable: Unpublished v1.0.1 release and final F-Droid recipe verification pending" "$draft" || die "Public recipe is not disabled: $pkg"
  grep -Fq "46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7" "$draft" || die "Signing key pin differs: $pkg"
  if grep -q "^    disable:" "$scratch"; then die "Scratch candidate unexpectedly disabled: $pkg"; fi
  diff -q <(sed "/^    disable: Unpublished v1\.0\.1 release and final F-Droid recipe verification pending$/d" "$draft") "$scratch" >/dev/null || die "Scratch recipe differs from public draft: $pkg"
done
( cd "$STAGE" && sha256sum -c SHA256SUMS ) ||
  die 'Original signed release hashes do not verify'
for cmd in git docker sha256sum grep cmp sed diff; do
  command -v "$cmd" >/dev/null || die "Missing $cmd"
done
docker info >/dev/null 2>&1 || die 'Docker daemon inaccessible'
docker image inspect "$IMAGE" >/dev/null 2>&1 || die 'Official F-Droid image not locally available'
echo 'PASS: Verified lint workspace, reviewed source and released APKs'
if [[ "$MODE" == --check ]]; then
  echo 'READ ONLY: No recipe build, signing, installation or submission'
  exit 0
fi

WORK="$(mktemp -d "$BASE/v101-fdroid-recipe-remaining.XXXXXXXX")"
cp "$LINT/config.yml" "$WORK/config.yml"
cp -a "$LINT/config" "$WORK/config"
mkdir -p "$WORK/metadata"
for pkg in "${PKGS[@]}"; do
  cp "$LINT/metadata/$pkg.yml" "$WORK/metadata/$pkg.yml"
done
printf '%s\n' "$SOURCE" > "$WORK/REVIEWED_SOURCE_COMMIT.txt"
printf '%s\n' "$IMAGE" > "$WORK/IMAGE.txt"
echo "===== OFFICIAL F-DROID RECIPE BUILD: CONTROLLER / MOTION / SMS ====="
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
    fdroid lint com.nefarioussecurity.sentinel.controller
    fdroid lint com.nefarioussecurity.sentinel.motion
    fdroid lint com.nefarioussecurity.sentinel.sms
    echo "===== F-DROID LOCAL RECIPE BUILD ====="
    fdroid build --test --stop --no-tarball \
      com.nefarioussecurity.sentinel.controller:5 \
      com.nefarioussecurity.sentinel.motion:5 \
      com.nefarioussecurity.sentinel.sms:5
    echo "PASS: Three F-Droid recipe build commands completed"
  ' 2>&1 | tee "$WORK/build.log"

echo "===== CHECK INDEPENDENT F-DROID VERIFICATION EVIDENCE ====="
for pkg in "${PKGS[@]}"; do
  grep -Fq "success: $pkg" "$WORK/build.log" || die "Missing F-Droid success: $pkg"
  echo "PASS: Official recipe verification $pkg"
done
for pattern in \
  "compared built binary to supplied reference binary successfully" \
  "supplied reference binary has allowed signer 46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7"
do
  count="$(grep -Fc "$pattern" "$WORK/build.log" || true)"
  [[ "$count" -eq 3 ]] || die "Expected three F-Droid verification records; found $count"
done
echo "PASS: Three F-Droid built binaries match published signed v1.0.1 APKs"
echo "Evidence: $WORK/build.log"
echo "NO APK SIGNING, INSTALLATION, TAG CHANGE, OR PUBLICATION"
