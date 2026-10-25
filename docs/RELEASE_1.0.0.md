# Nefarious Sentinel v1.0.0 release candidate - acceptance and release gate

Source branch: `release/v1.0.0-rc1` (based on public Beta 1 source commit `f13cb8890c92fa8392431af7bbcb76ed3c825032`).

Public distribution: https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases

## Hard stop conditions

Do not publish `v1.0.0` until each gate below is signed off. No reports is a useful stability signal, not a substitute for executed tests.

- [ ] **Four-week public-beta observation** with no unresolved critical/high-severity reports, plus evidence of real device coverage. Public Beta 1 was published on 1 October 2026.
- [ ] **Source freeze:** confirm no unreviewed release changes; all protections retain expected behaviour.
- [ ] **Clean builds:** all modules compile and unit tests pass on Java 17 with SDK 34.
- [ ] **Same signing key:** APK signer SHA-256 matches the published Beta 1 certificate `46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`. Never upload a signing key.
- [ ] **Consistent identity:** versionCode 4 and versionName 1.0.0 in all four APKs, same package IDs as public beta.
- [ ] **Stable lifecycle:** both Core and Controller `BETA_BUILD=false`; no stable expiry; stable UI shows no expiry.
- [x] **Standard-mode Beta 1 → v1.0.0 upgrade on Pixel 10 Pro Fold (`rango`), 2026-10-25:** installed all four production-signed APKs in place (versionCode 3 → 4) with `adb install -r`; verified correct versions and no Device Owner. User confirmed five post-upgrade checks passed: `STANDARD` and `Signature verified`, existing management PIN, unchanged protection settings, working Android three-button Back navigation, and absent beta-expiry warning. Fold reboot and Controller reconnection subsequently reported PASS by user on 2026-10-25. This does not establish Device Owner in-place upgrade or expired-beta re-arm acceptance.
- [ ] **Expired-beta upgrade:** previously latched destructive suppression remains until fresh authenticated re-arm on versionCode 4. Nonexpired beta upgrade preserves protections and configuration without unexpected activation.
- [ ] **Device Owner integrity:** clean install/upgrade, controller/core/helper signature checks, core remains DO, Controller removable, helpers communicating, API 34+ whole-device wipe path checked.
- [ ] **Non-destructive checks:** USB charge-only vs data-host, AFU/BFU, reboot, deadman schedule/check-in, Maintenance Mode expiration, Snatch Lock/Reboot, tamper debugging controls, PIN rate limiting, IPC, policy state, downgrade rejection.
- [x] **Android three-button Back regression on Pixel 10a:** controller rebuilt, both debug/release JVM unit test tasks passed, updated signed release Controller installed, and user confirmed all requested navigation checks passed on 2026-10-25 (including child pages and Licence returning to About).
- [ ] **Additional Back edge cases:** gesture Back if applicable, dashboard exit behaviour, and active removal flow (do not trigger removal on the Device Owner device solely for this test).
- [ ] **Destructive checks on disposable hardware only:** deliberately authorised USB and Dead Man triggered wipe tests. Never enable a trigger while operating on the sole copy of user data. Record results separately.
- [ ] **SMS ingress:** production-signed real-SMS acceptance closed or explicitly documented as unsupported for stable. Do not claim verified carrier ingress without the test.
- [ ] **Customer handover:** user-selected screen PIN on initial provisioning, no forced temporary PIN and no biometrics, with Sentinel management PIN separate. Verify Android OS limitations before advertising this as automatic.
- [ ] **Licence/source distribution:** include complete corresponding AGPL source, licence and Lethe Labs attribution. Do not publish only proprietary binary artefacts.
- [ ] **Vulnerability reporting:** verify a working confidential security contact path; avoid upstream Lethe Labs contact for this fork.
- [ ] **Release signing:** publish signed four-APK ZIP, `SHA256SUMS.txt`, detached package checksum, version/build manifest, install guide, known limitations, release notes and source archive. Inspect contents for secrets.
- [ ] **Binary identity:** the exact APK bytes exercised during final acceptance must be the bytes published as stable. Rebuilding for publication requires repeat signing/hash and key acceptance checks.

## Mint build and verification (non-destructive)

Run from an up-to-date, clean checkout of this release-candidate branch:

```sh
git status --short
git rev-parse HEAD
./gradlew --version
./gradlew clean test \
  :app:assembleRelease \
  :controller:assembleRelease \
  :motion-helper:assembleRelease \
  :sms-helper:assembleRelease
```

**Warning:** Release builds require the existing local production signing credentials. Do not create a new keystore or commit `keystore.properties`.

Collect paths from the actual outputs, rather than assuming a fixed filename or variant. Verify each generated APK with `apksigner verify --verbose --print-certs PATH_TO_APK`, and check each signer digest against the pinned public Beta 1 signer.

Generate a corresponding source snapshot from the release commit, after checking ignored/untracked files and making sure the archive includes all build scripts:

```sh
git archive --format=zip --output ../Nefarious-Sentinel-1.0.0-Source.zip HEAD
sha256sum ../Nefarious-Sentinel-1.0.0-Source.zip
```

The source archive is made from tracked code only. Manually review package contents and licence obligations; a `git archive` does not include untracked dependencies or external build resources.

Record actual device evidence for:
- GrapheneOS version and Pixel model
- DO/Admin status before/after install
- App versions and certificate digests
- Non-destructive test identifiers and PASS/FAIL/BLOCKED status
- Separately authorised destructive test identifiers and results
- Upgrade from Beta 1 (versionCode 3) and from an *expired* Beta with rearm

## Publication

Only after gates are approved: tag the tested source commit as `v1.0.0`, attach the verified APK package and corresponding source to the **public release-only repository**, include published SHA-256 checksums and installer instructions, and then verify downloads by hashing them again. Preserve old Beta 1 assets for reproducibility.

Do not publish an untested, unsigned, debug, mismatched-certificate or unverified package.
