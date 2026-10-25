#!/usr/bin/env bash
# Isolated lint of official F-Droid v1.0.1 metadata candidates.
# No app install, signing, Git tag, APK publication or submission.
set -euo pipefail
umask 077
die() { echo "STOP: $*" >&2; exit 1; }
[[ $# -eq 1 ]] || die "Usage: bash $0 --check|--run"
case "$1" in --check|--run) MODE="$1";; *) die "Unknown mode";; esac
ROOT="$HOME/Projects/Nefarious-Sentinel"
BASE="$HOME/Projects/fdroid-sentinel-validation"
COMMIT="10a5324be20cf1fe8714b9edce030f607749c10c"
IMAGE="registry.gitlab.com/fdroid/fdroidserver:buildserver"
CERT="46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7"
[[ -d "$ROOT/.git" && -d "$BASE" ]] || die "Missing checkout or validation workspace"
[[ "$(git -C "$ROOT" branch --show-current)" == "release/v1.0.1-fdroid-prep" ]] ||
  die "Wrong source branch"
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || die "Uncommitted changes"
[[ "$(git -C "$ROOT" remote get-url origin)" == "https://github.com/NefariousSecurity/Nefarious-Sentinel.git" ]] ||
  die "Unexpected Git remote"
[[ "$(git -C "$ROOT" rev-parse 'v1.0.1^{}')" == "$COMMIT" ]] || die "Tag mismatch"
for cmd in git grep sed docker sha256sum; do
  command -v "$cmd" >/dev/null || die "Missing command: $cmd"
done
for pkg in \
  com.nefarioussecurity.sentinel \
  com.nefarioussecurity.sentinel.controller \
  com.nefarioussecurity.sentinel.motion \
  com.nefarioussecurity.sentinel.sms
do
  file="$ROOT/fdroid-candidates/metadata/$pkg.yml"
  [[ -f "$file" ]] || die "Missing metadata for $pkg"
  grep -Fq "    commit: $COMMIT" "$file" || die "Wrong source commit in $pkg"
  grep -Fq '    versionCode: 5' "$file" || die "Wrong versionCode in $pkg"
  grep -Fq '    disable: Unpublished v1.0.1 release and final F-Droid recipe verification pending' "$file" ||
    die "Missing draft disable guard: $pkg"
  grep -Fq "$CERT" "$file" || die "Wrong certificate pin for $pkg"
done
docker info >/dev/null 2>&1 || die "Docker unavailable"
docker image inspect "$IMAGE" >/dev/null 2>&1 || die "F-Droid image missing"
echo "PASS: Four disabled metadata candidates and official F-Droid image present"
if [[ "$MODE" == --check ]]; then
  echo "READ ONLY: No lint, publication, device actions or source changes"
  exit 0
fi
WORK="$(mktemp -d "$BASE/v101-fdroid-lint.XXXXXXXX")"
mkdir -p "$WORK/metadata"
for pkg in \
  com.nefarioussecurity.sentinel \
  com.nefarioussecurity.sentinel.controller \
  com.nefarioussecurity.sentinel.motion \
  com.nefarioussecurity.sentinel.sms
do
  cp -- "$ROOT/fdroid-candidates/metadata/$pkg.yml" "$WORK/metadata/$pkg.yml"
  # Only temporary copies have the disable flag removed.
  sed -i '/^    disable: Unpublished v1\.0\.1 release and final F-Droid recipe verification pending$/d' \
    "$WORK/metadata/$pkg.yml"
  if grep -q '^    disable:' "$WORK/metadata/$pkg.yml"; then
    die "Unexpected disabled build in scratch copy of $pkg"
  fi
done
echo "===== F-DROID READMETA / LINT IN ISOLATED CONTAINER ====="
echo "Workspace: $WORK"
docker run --rm \
  --user vagrant --env HOME=/home/vagrant \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --volume "$WORK:/build:rw" --workdir /build \
  --entrypoint /bin/bash "$IMAGE" -ec '
    . /etc/profile
    export PATH="/home/vagrant/fdroidserver:$PATH"
    export PYTHONPATH="/home/vagrant/fdroidserver"
    command -v fdroid >/dev/null || {
      echo "STOP: fdroid CLI unavailable in image"
      exit 1
    }
    fdroid readmeta
    for pkg in \
      com.nefarioussecurity.sentinel \
      com.nefarioussecurity.sentinel.controller \
      com.nefarioussecurity.sentinel.motion \
      com.nefarioussecurity.sentinel.sms
    do
      echo "===== LINT $pkg ====="
      fdroid lint "$pkg"
      echo "PASS: $pkg lint"
    done
  ' 2>&1 | tee "$WORK/lint.log"
echo "PASS: All four F-Droid metadata candidates linted"
echo "Log: $WORK/lint.log"
echo "NO PUBLISHING, INSTALLATION OR SUBMISSION"
