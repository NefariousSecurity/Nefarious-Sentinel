#!/usr/bin/env bash
# Publish exact verified Sentinel v1.0.0 APKs with corresponding public source.
# --check does not publish. --publish creates a PRERELEASE until gates close.
set -euo pipefail
umask 077

[[ $# == 1 ]] || { echo "Usage: bash $0 --check|--publish"; exit 2; }
MODE="$1"
[[ "$MODE" == --check || "$MODE" == --publish ]] || exit 2
die() { echo "STOP: $*" >&2; exit 1; }

[[ "$(git branch --show-current)" == release/v1.0.0-rc1 ]] ||
  die "Wrong source branch"
[[ -z "$(git status --porcelain -uno)" ]] ||
  die "Tracked source contains local modifications"
[[ -f LICENSE && -f gradlew && -f settings.gradle ]] ||
  die "Complete source/build files unavailable"
if git ls-files | grep -Ei '(^|/)(keystore\.properties|local\.properties|.*\.(keystore|jks|p12|p8|pem|key)|\.env([.]|$))'; then
  die "Possible tracked private key or credential. Audit source before release."
fi
for cmd in git sha256sum apksigner aapt awk sed grep tr tar zip mktemp; do
  command -v "$cmd" >/dev/null || die "Required tool absent: $cmd"
done
CERT=46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7
REPO=NefariousSecurity/Nefarious-Sentinel
TAG=v1.0.0

# SHA-256 | Package ID | Mint build output | public release asset filename
MANIFEST=$(cat <<'END_MANIFEST'
b2d67d797acab9abae59d9c3351c1ab211986c5f4fc9fe494a2bbd0a62e94629|com.nefarioussecurity.sentinel|app/build/outputs/apk/release/app-release.apk|Nefarious-Sentinel-Core-v1.0.0.apk
05fec78c4b8da982c2ab47f0a5eeb26304fab599c4995334139139dcb1f5847b|com.nefarioussecurity.sentinel.motion|motion-helper/build/outputs/apk/release/motion-helper-release.apk|Nefarious-Sentinel-Motion-v1.0.0.apk
3678b9c22e9dd210af89ae3f1f23e117598e220b0a3023ba8dc5a12f531a044c|com.nefarioussecurity.sentinel.sms|sms-helper/build/outputs/apk/release/sms-helper-release.apk|Nefarious-Sentinel-SMS-v1.0.0.apk
fd7fc3e53676a16387d2e3fa933bd0dc88a767580e65cab368582f9cad20fa80|com.nefarioussecurity.sentinel.controller|controller/build/outputs/apk/release/controller-release.apk|Nefarious-Sentinel-Controller-v1.0.0.apk
END_MANIFEST
)
verify() {
  local HASH="$1" PKG="$2" FILE="$3" OUT SIG BADGE RECORD
  [[ -f "$FILE" ]] || die "Missing: $FILE"
  printf '%s  %s\n' "$HASH" "$FILE" | sha256sum -c - ||
    die "Wrong APK hash: $PKG"
  OUT="$(apksigner verify --verbose --print-certs "$FILE")" ||
    die "APK signature invalid: $PKG"
  SIG="$(printf '%s\n' "$OUT" | sed -n \
    's/^Signer #1 certificate SHA-256 digest: //p' |
    head -n1 | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  [[ "$SIG" == "$CERT" ]] || die "Signer mismatch: $PKG"
  BADGE="$(aapt dump badging "$FILE")" || die "Bad APK metadata"
  RECORD="$(printf '%s\n' "$BADGE" | grep -m1 '^package:')" ||
    die "No package ID"
  [[ "$RECORD" == *"name='$PKG'"* &&
     "$RECORD" == *"versionCode='4'"* &&
     "$RECORD" == *"versionName='1.0.0'"* ]] ||
    die "Package/version mismatch: $PKG"
}
while IFS='|' read -r HASH PKG FILE ASSET; do
  [[ -n "$HASH" ]] || continue
  verify "$HASH" "$PKG" "$FILE"
done <<< "$MANIFEST"
echo "PASS: All four exact signed APKs and version metadata verified"

if [[ "$MODE" == --check ]]; then
  echo "CHECK ONLY: nothing published; use --publish after reviewing release gates."
  exit 0
fi

command -v gh >/dev/null || die "Install GitHub CLI (gh) before publishing"
gh auth status >/dev/null || die "GitHub authentication unavailable"
[[ "$(gh repo view "$REPO" --json visibility --jq '.visibility')" == PUBLIC ]] ||
  die "Release destination isn't public"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  die "Release already exists; refusing to overwrite"
fi
PUBLIC="https://github.com/$REPO.git"
if git ls-remote --tags "$PUBLIC" "refs/tags/$TAG" | grep -q .; then
  die "Public tag already exists"
fi
if git ls-remote --heads "$PUBLIC" refs/heads/source-v1.0.0 | grep -q .; then
  die "Public source branch already exists"
fi

WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
SRC="$WORK/source"
ASSETS="$WORK/assets"
mkdir -p "$SRC" "$ASSETS"
git archive --format=tar HEAD | tar -xf - -C "$SRC"
[[ -f "$SRC/LICENSE" && -f "$SRC/gradlew" ]] ||
  die "Source archive incomplete"
if find "$SRC" -type f \( -iname '*.keystore' -o -iname '*.jks' \
    -o -name 'keystore.properties' -o -name 'local.properties' \) | grep -q .; then
  die "Private build credentials found in source archive"
fi

while IFS='|' read -r HASH PKG FILE ASSET; do
  [[ -n "$HASH" ]] || continue
  cp "$FILE" "$ASSETS/$ASSET"
  verify "$HASH" "$PKG" "$ASSETS/$ASSET"
done <<< "$MANIFEST"
(cd "$ASSETS" && zip -q -j Nefarious-Sentinel-v1.0.0-four-apks.zip ./*.apk)

git -C "$SRC" init -q -b source-v1.0.0
git -C "$SRC" add -A
git -C "$SRC" -c user.name=NefariousSecurity \
  -c user.email=releases@users.noreply.github.com commit -qm \
  "Public corresponding AGPL-3.0 source for Sentinel v1.0.0"
git -C "$SRC" tag -a "$TAG" -m "Nefarious Sentinel v1.0.0 source"
git -C "$SRC" archive --format=zip \
  --output "$ASSETS/Nefarious-Sentinel-v1.0.0-source.zip" "$TAG"
echo "Source private commit: $(git rev-parse HEAD)" > "$ASSETS/BUILD_INFO.txt"
echo "Version: 1.0.0 (code 4), prerelease pending final acceptance" >> "$ASSETS/BUILD_INFO.txt"
echo "Signing certificate SHA-256: $CERT" >> "$ASSETS/BUILD_INFO.txt"
(cd "$ASSETS" && sha256sum ./*.apk ./*.zip > SHA256SUMS.txt)

cat > "$ASSETS/RELEASE_NOTES.txt" <<'RELEASE_NOTES'
Nefarious Sentinel v1.0.0 RELEASE CANDIDATE (PRERELEASE)
Four matching production-signed APKs, AGPL-3.0 source, and checksums.
Core security implementation unchanged from public Beta 1; stable channel
removes the beta expiry. Controller fixes Android system Back navigation.

Tested: Pixel 10a Device Owner fresh install; Pixel 10 Pro Fold Standard
Mode upgrade from Beta 1, including PIN and settings retention and reboot.
UNVERIFIED: expired-beta recovery, live carrier SMS ingress and other final
release gates. Destructive test triggers can irreversibly wipe a device.
Do not enable on devices containing irreplaceable data.
Device Owner requires separate Android provisioning, not merely APK install.
RELEASE_NOTES

echo "Publishing public AGPL source branch, tag and GitHub PRERELEASE."
git -C "$SRC" remote add origin "$PUBLIC"
git -C "$SRC" push origin source-v1.0.0
git -C "$SRC" push origin "$TAG"
gh release create "$TAG" --repo "$REPO" --verify-tag --prerelease \
  --title "Nefarious Sentinel v1.0.0 Release Candidate" \
  --notes-file "$ASSETS/RELEASE_NOTES.txt" \
  "$ASSETS/"*.apk "$ASSETS/"*.zip \
  "$ASSETS/SHA256SUMS.txt" "$ASSETS/BUILD_INFO.txt" ||
  die "Source pushed but release upload failed: investigate; do not rerun blindly"
echo "PASS: https://github.com/$REPO/releases/tag/$TAG"
