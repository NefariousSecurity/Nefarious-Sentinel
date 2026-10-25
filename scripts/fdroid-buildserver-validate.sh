#!/usr/bin/env bash
# F-Droid buildserver compatibility test, no release credentials or device access.
# --check verifies prerequisites; --prepare creates isolated fdroiddata clones;
# --run invokes the official F-Droid buildserver container for all four modules.
# No operation submits to official F-Droid.
set -euo pipefail
umask 077
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }

[[ "$#" == 1 ]] || die "Usage: bash $0 --check|--prepare|--run"
case "$1" in
  --check|--prepare|--run) MODE="$1" ;;
  *) die "Unknown operation: $1" ;;
esac
for cmd in git grep sed cp; do
  command -v "$cmd" >/dev/null || die "Missing command: $cmd"
done
ROOT="$(git rev-parse --show-toplevel)"
[[ -f "$ROOT/scripts/fdroid-repro-check.sh" ]] ||
  die "Run inside the Nefarious-Sentinel public source checkout"
[[ "$(git -C "$ROOT" remote get-url origin)" == \
  "https://github.com/NefariousSecurity/Nefarious-Sentinel.git" ]] ||
  die "Unexpected Git remote"

WORK="$HOME/Projects/fdroid-sentinel-validation"
FDD="$WORK/fdroiddata"
FDS="$WORK/fdroidserver"
IMAGE="registry.gitlab.com/fdroid/fdroidserver:buildserver"
PKGS="com.nefarioussecurity.sentinel com.nefarioussecurity.sentinel.controller com.nefarioussecurity.sentinel.motion com.nefarioussecurity.sentinel.sms"
CERT="46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7"

for pkg in $PKGS; do
  candidate="$ROOT/fdroid-candidates/metadata/$pkg.yml"
  [[ -f "$candidate" ]] || die "Missing candidate: $pkg"
  grep -q "$CERT" "$candidate" || die "Signing certificate pin missing: $pkg"
  grep -q "^Binaries:" "$candidate" || die "Developer-signed binary URL missing: $pkg"
  grep -q "^    disable:" "$candidate" ||
    die "Source recipe must stay disabled until the external tests pass: $pkg"
done

if [[ "$MODE" == --check ]]; then
  echo "PASS: Four disabled, signer-pinned recipes are present."
  echo "Test workspace: $WORK"
  if command -v docker >/dev/null; then
    docker --version
    if docker info >/dev/null 2>&1; then
      echo "Docker daemon access: available"
    else
      echo "Docker daemon access: NOT available to this shell"
    fi
  else
    echo "Docker: not installed"
  fi
  echo "CHECK ONLY: nothing downloaded or modified."
  exit 0
fi

if [[ "$MODE" == --prepare ]]; then
  [[ ! -e "$WORK" ]] ||
    die "Workspace already exists. Inspect it before creating another: $WORK"
  mkdir -p "$WORK"
  git clone --depth=1 https://gitlab.com/fdroid/fdroiddata.git "$FDD" ||
    die "fdroiddata clone failed; inspect $WORK"
  git clone --depth=1 https://gitlab.com/fdroid/fdroidserver.git "$FDS" ||
    die "fdroidserver clone failed; inspect $WORK"
  for pkg in $PKGS; do
    target="$FDD/metadata/$pkg.yml"
    [[ ! -e "$target" ]] || die "fdroiddata already has this package: $pkg"
    cp "$ROOT/fdroid-candidates/metadata/$pkg.yml" "$target"
    # Remove disabled flag only from separate scratch copy, never upstream.
    sed -i '/^    disable: /d' "$target"
    ! grep -q "^    disable:" "$target" ||
      die "Candidate could not be enabled in scratch copy: $pkg"
    echo "STAGED: $pkg"
  done
  echo "PASS: Staged four test recipes in $FDD"
  echo "NEXT: Review the scratch files, check Docker access, then run --run"
  exit 0
fi

[[ -d "$FDD/.git" && -d "$FDS/.git" ]] ||
  die "Test worktree not prepared. Run --prepare first."
command -v docker >/dev/null || die "Docker unavailable."
docker info >/dev/null 2>&1 ||
  die "Docker daemon inaccessible. Do not chmod Docker socket."
for pkg in $PKGS; do
  [[ -f "$FDD/metadata/$pkg.yml" ]] || die "Staged metadata missing: $pkg"
  ! grep -q "^    disable:" "$FDD/metadata/$pkg.yml" ||
    die "Staged recipe still disabled: $pkg"
done

echo "Running official buildserver: $IMAGE"
echo "Only isolated F-Droid workspace is mounted writable."
echo "No signing credentials, host Docker socket, or devices are mounted."
docker run --rm --user vagrant --env HOME=/home/vagrant \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --volume "$FDD:/build" \
  --volume "$FDS:/home/vagrant/fdroidserver:ro" \
  --workdir /build --entrypoint /bin/bash "$IMAGE" -ec '
    . /etc/profile
    export HOME=/home/vagrant
    export PATH="/home/vagrant/fdroidserver:$PATH"
    export PYTHONPATH="/home/vagrant/fdroidserver"
    export JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 |
      sed -n "s/^[[:space:]]*java.home[[:space:]]*=[[:space:]]*//p" |
      head -n1)"
    test -d "$JAVA_HOME" || { echo "STOP: JAVA_HOME missing"; exit 1; }
    fdroid readmeta
    for pkg in \
      com.nefarioussecurity.sentinel \
      com.nefarioussecurity.sentinel.controller \
      com.nefarioussecurity.sentinel.motion \
      com.nefarioussecurity.sentinel.sms; do
      echo "===== F-DROID LINT: $pkg ====="
      fdroid lint "$pkg" || exit 1
    done
    for pkg in \
      com.nefarioussecurity.sentinel \
      com.nefarioussecurity.sentinel.controller \
      com.nefarioussecurity.sentinel.motion \
      com.nefarioussecurity.sentinel.sms; do
      echo "===== F-DROID BUILD: $pkg ====="
      # fdroid build can report "N build failed" yet return status 0
      # unless --stop is explicitly selected. Without --stop our initial
      # four failures were incorrectly labelled PASS.
      if ! fdroid build --stop "$pkg"; then
        echo "FAIL: F-Droid build failed for $pkg."
        echo "===== GRADLE STACKTRACE DIAGNOSTIC (NO APK INSTALL) ====="
        gradle_bin=/home/vagrant/.cache/gradlew-fdroid/versions/8.2/bin/gradle
        if test -x "$gradle_bin" && test -d "/build/build/$pkg"; then
          (
            cd "/build/build/$pkg"
            "$gradle_bin" --no-daemon --offline --stacktrace help
          ) || true
        else
          echo "Gradle 8.2 diagnostic executable or app workspace unavailable."
        fi
        exit 1
      fi
      if ! test -f "/build/unsigned/${pkg}_4.apk"; then
        echo "FAIL: Build did not produce expected unsigned APK for $pkg"
        exit 1
      fi
      echo "PASS: F-Droid produced unsigned APK for $pkg"
    done
    echo "PASS: All four F-Droid unsigned APKs were built successfully."
    echo "NEXT: Confirm independently verified binary comparison before submitting."
  '
