#!/usr/bin/env bash
# F-Droid reproducibility probe for the immutable public v1.0.0 source tag.
# --check: verify prerequisites/inputs only. --build: isolated unsigned rebuild
#          and apksigcopier comparison to the four RELEASE APKs on Mint.
# Does NOT touch phone, release keys, released tags, or install applications.
set -euo pipefail
umask 077

die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ "$#" == 1 && ( "$1" == --check || "$1" == --build ) ]] ||
    die "Usage: bash scripts/fdroid-repro-check.sh --check|--build"
MODE="$1"
ROOT="$(git rev-parse --show-toplevel)"
[[ -d "$ROOT/.git" ]] || die "Run from cloned public Sentinel repository"
[[ "$(git -C "$ROOT" rev-parse --verify refs/tags/v1.0.0^{commit})" ]] ||
    die "v1.0.0 public tag not found"
[[ "$(git -C "$ROOT" remote get-url origin)" == *"Nefarious-Sentinel-Releases"* ]] ||
    die "Expected public Sentinel source clone"

PRIVATE_APKS="${SENTINEL_SIGNED_DIR:-$HOME/Projects/oblivion-v2}"
CERT="46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7"
for cmd in git tar sed sha256sum awk find sort mktemp java apksigner aapt; do
  command -v "$cmd" >/dev/null || die "Missing: $cmd"
done
if [[ "$MODE" == --build ]]; then
  command -v apksigcopier >/dev/null ||
    die "apksigcopier not found. Install locally first, then rerun."
fi

# SHA256|package|module|current signed APK filename|Gradle unsigned output prefix
DATA=$(cat <<'MANIFEST'
b2d67d797acab9abae59d9c3351c1ab211986c5f4fc9fe494a2bbd0a62e94629|com.nefarioussecurity.sentinel|app|app/build/outputs/apk/release/app-release.apk
05fec78c4b8da982c2ab47f0a5eeb26304fab599c4995334139139dcb1f5847b|com.nefarioussecurity.sentinel.motion|motion-helper|motion-helper/build/outputs/apk/release/motion-helper-release.apk
3678b9c22e9dd210af89ae3f1f23e117598e220b0a3023ba8dc5a12f531a044c|com.nefarioussecurity.sentinel.sms|sms-helper|sms-helper/build/outputs/apk/release/sms-helper-release.apk
fd7fc3e53676a16387d2e3fa933bd0dc88a767580e65cab368582f9cad20fa80|com.nefarioussecurity.sentinel.controller|controller|controller/build/outputs/apk/release/controller-release.apk
MANIFEST
)
while IFS='|' read -r HASH PKG MODULE FILE; do
  SIGNED="$PRIVATE_APKS/$FILE"
  [[ -f "$SIGNED" ]] || die "Signed reference missing: $SIGNED"
  printf '%s  %s\n' "$HASH" "$SIGNED" | sha256sum -c - ||
    die "Signed reference hash mismatch: $PKG"
  CERT_OUT="$(apksigner verify --print-certs "$SIGNED")" ||
    die "Invalid signature: $PKG"
  ACTUAL="$(printf '%s\n' "$CERT_OUT" |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
    head -n1)"
  [[ "$ACTUAL" == "$CERT" ]] || die "Wrong signature for $PKG"
  echo "PASS: Pinned signed reference: $PKG"
done <<< "$DATA"
[[ "$MODE" == --build ]] || {
  echo "CHECK ONLY: Reference files valid. No build or writes performed."
  exit 0
}

# The private project was already built on this Mint host. Its ignored
# local.properties can identify the SDK without copying any private data.
# Only the sdk.dir value is read; keystore properties are never sourced.
resolve_sdk() {
  local candidate="" path
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    candidate="$ANDROID_HOME"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    candidate="$ANDROID_SDK_ROOT"
  elif [[ -f "$PRIVATE_APKS/local.properties" ]]; then
    candidate="$(awk -F= '
      /^[[:space:]]*sdk[.]dir[[:space:]]*=/ {
        sub(/^[^=]*=/, "")
        sub(/^[[:space:]]+/, "")
        sub(/[[:space:]]+$/, "")
        print
        exit
      }' "$PRIVATE_APKS/local.properties" | tr -d '\r')"
  fi
  if [[ -z "$candidate" ]]; then
    for path in "$HOME/Android/Sdk" "$HOME/android-sdk" \
                "/opt/android-sdk" "/usr/lib/android-sdk"; do
      if [[ -d "$path/platforms/android-34" ]]; then
        candidate="$path"
        break
      fi
    done
  fi
  [[ -n "$candidate" && -d "$candidate/platforms/android-34" &&
     -d "$candidate/build-tools" ]] ||
    die "Android SDK 34 not found. Set ANDROID_HOME to the SDK directory, then retry."
  printf '%s\n' "$candidate"
}
export ANDROID_HOME="$(resolve_sdk)"
if [[ -n "${ANDROID_SDK_ROOT:-}" && "$ANDROID_SDK_ROOT" != "$ANDROID_HOME" ]]; then
  die "ANDROID_SDK_ROOT and ANDROID_HOME disagree; resolve explicitly before building"
fi
echo "PASS: Android SDK identified at $ANDROID_HOME (platform android-34 present)"

WORK="$(mktemp -d)"
# Keep temporary outputs if the comparison fails, so differences are inspectable.
echo "Building in disposable, unprivileged scratch directory: $WORK"
SRC="$WORK/src"
mkdir -p "$SRC"
git -C "$ROOT" archive --format=tar v1.0.0 | tar -xf - -C "$SRC" ||
  die "Could not extract tagged public source"
[[ -x "$SRC/gradlew" ]] || die "Public source missing executable Gradle wrapper"

# No private release-signing credentials ever enter the isolated build tree.
for MODULE in app motion-helper sms-helper controller; do
  GRADLE="$SRC/$MODULE/build.gradle"
  [[ -f "$GRADLE" ]] || die "Missing $GRADLE"
  OCCURRENCES="$(grep -Ec '^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$' "$GRADLE")"
  [[ "$OCCURRENCES" == 1 ]] ||
    die "Unexpected release signing configuration in $MODULE"
  sed -i '/^[[:space:]]*signingConfig signingConfigs.release[[:space:]]*$/d' "$GRADLE"
done
[[ ! -e "$SRC/keystore.properties" && ! -e "$SRC/anonymous_key.keystore" ]] ||
  die "Private signing credentials unexpectedly in public archive"

echo "===== ISOLATED UNSIGNED RELEASE REBUILD ====="
(
 cd "$SRC"
 ./gradlew --no-daemon \
    :app:assembleRelease \
    :motion-helper:assembleRelease \
    :sms-helper:assembleRelease \
    :controller:assembleRelease
) || die "Unsigned Gradle build failed; inspect $WORK"

echo "===== COMPARE APK CONTENT USING RELEASE SIGNATURES ====="
FAILURES=0
while IFS='|' read -r HASH PKG MODULE FILE; do
  SIGNED="$PRIVATE_APKS/$FILE"
  mapfile -t OUTPUTS < <(find "$SRC/$MODULE/build/outputs/apk/release" \
      -maxdepth 1 -type f -name '*-unsigned.apk' | sort)
  if [[ "${#OUTPUTS[@]}" != 1 ]]; then
    echo "FAIL: Expected one unsigned APK for $PKG, got ${#OUTPUTS[@]}"
    FAILURES=1
    continue
  fi
  if apksigcopier compare --unsigned "$SIGNED" "${OUTPUTS[0]}"; then
    echo "PASS: Reproducible exact developer-signed contents: $PKG"
  else
    echo "FAIL: Reproducibility mismatch: $PKG"
    FAILURES=1
  fi
done <<< "$DATA"

if [[ "$FAILURES" == 0 ]]; then
  echo "PASS: All four v1.0.0 APKs reproducibly rebuilt in this Mint environment"
  echo "NEXT: Run fdroiddata CI to verify clean F-Droid buildserver compatibility"
else
  echo "INCOMPLETE: At least one reproducibility comparison failed."
  echo "Keep scratch results for investigation: $WORK"
  exit 2
fi
# User may delete scratch directory after reviewing. No automatic cleanup.
