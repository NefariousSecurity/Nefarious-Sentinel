# Nefarious Sentinel v1.0.1: verified F-Droid inclusion candidates

Four independent Android applications ship as a coordinated family:

| Package ID | Module | Signed upstream APK |
| --- | --- | --- |
| `com.nefarioussecurity.sentinel` | `app` | `Nefarious-Sentinel-Core-v1.0.1.apk` |
| `com.nefarioussecurity.sentinel.controller` | `controller` | `Nefarious-Sentinel-Controller-v1.0.1.apk` |
| `com.nefarioussecurity.sentinel.motion` | `motion-helper` | `Nefarious-Sentinel-Motion-v1.0.1.apk` |
| `com.nefarioussecurity.sentinel.sms` | `sms-helper` | `Nefarious-Sentinel-SMS-v1.0.1.apk` |

**Please create one fdroiddata merge request per application**, each adding
only `metadata/<applicationId>.yml`. The four metadata files in `metadata/`
are submission candidates made from the final locally linted files; no
GitLab merge requests or official F-Droid CI successes are claimed.

**Upstream source/release:**

- Source: https://github.com/NefariousSecurity/Nefarious-Sentinel
- Release: https://github.com/NefariousSecurity/Nefarious-Sentinel/releases/tag/v1.0.1
- Version: `1.0.1`, version code `5` (all four packages).
- Pinned source commit: `10a5324be20cf1fe8714b9edce030f607749c10c`.
- Release signer SHA-256: `46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`.
- License: `AGPL-3.0-only`.
- Original Fastlane English metadata under `<module>/fastlane/metadata/android/en-US/`.

**Verified build evidence (developer's local tests):**

- Official fdroidserver metadata `readmeta` and `lint`: 4/4 PASS.
- All four F-Droid `build --test --stop --no-tarball` recipe builds succeeded
  inside the official F-Droid buildserver image.
- F-Droid itself downloaded and verified each corresponding signed upstream
  GitHub release APK, successfully compared the rebuilt binary with the
  developer-provided signed binary, and accepted the pinned signer.
- Public HTTPS-download verification: 4/4 exact byte matches vs protected
  signed originals; all manifest hashes and APK signatures checked.
- Detailed status and operator evidence: [`docs/FDROID_STATUS.md`](../../docs/FDROID_STATUS.md).

**Packaging notes:**

- Core, Controller, Motion and SMS are separate APKs; not every installation
  needs both optional helpers. Their versioning and release signing are
  coordinated. The provided recipes intentionally retain the same developer
  signing key with F-Droid's reproducible `Binaries` comparison.
- `AutoUpdateMode: None` and `UpdateCheckMode: None` are temporarily deliberate
  because coordinated cross-package release and signature verification
  has not yet been automated. Maintainers are welcome to review an
  appropriate automated checking strategy before acceptance.
- Core may acquire Android Device Owner only through a separate, explicitly
  authorised device provisioning operation. Ordinary APK installation
  does not grant Device Owner status.
- Optional destructive protections can permanently erase data; disabled by
  default. These security-sensitive behaviors are documented in upstream
  Fastlane descriptions and the release notes.
- Local build verification is not an independent security audit or a
  guarantee of inclusion. Please run official fdroiddata GitLab CI and
  policy review for each proposed MR.

**Request to helpers:** An upstream developer has been unable to register
a GitLab account without providing financial identity information. Please
help submit these already validated recipes through four individual
fdroiddata merge requests, share their links on the F-Droid Forum topic,
and flag any necessary modifications via GitHub issues.

Please do not request or distribute the release signing key.
