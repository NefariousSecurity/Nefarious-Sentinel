# F-Droid submission plan for Nefarious Sentinel

Status: **preparation only**. No claim of official F-Droid acceptance or listing.

## Public source prerequisite

The development GitHub repository is private. Official F-Droid must be able
to clone and build public, licence-compliant corresponding source. The
separate public repository is:

`https://github.com/NefariousSecurity/Nefarious-Sentinel`

The publication script exports a sanitised snapshot as Git branch
`source-v1.0.0` and annotated tag `v1.0.0` (the tag is not cryptographically
signed). Confirm those exist and the public release includes all four APKs,
their SHA-256 sums, full AGPL-3.0 source and Lethe Labs attribution.

## APK identities (four individual Android apps)

| Module | Android package |
| --- | --- |
| Core | `com.nefarioussecurity.sentinel` |
| Controller | `com.nefarioussecurity.sentinel.controller` |
| Motion | `com.nefarioussecurity.sentinel.motion` |
| SMS | `com.nefarioussecurity.sentinel.sms` |

Each has versionCode 4 and versionName 1.0.0. They use signature-based
Android permissions, so mixing independently signed Core/Controller/helpers
may break authorised IPC. The existing developer signing certificate SHA-256
is `46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`.

F-Droid's default app-specific key scheme and our shared-signer
architecture need explicit attention. Preferred path: reproducibly built
upstream developer-signed APKs, with `AllowedAPKSigningKeys` and
`Binaries` metadata after the reproducibility comparison succeeds.
Do not claim those checks pass until independently reproduced.

## Acceptance prerequisites before fdroiddata merge request

1. Confirm the public tag is cloneable and contains full source, Gradle
   wrapper, licence, and all modules.
2. Ensure F-Droid can compile each APK without the local private release
   keystore. Current release Gradle configuration references
   `keystore.properties` / local keystore for signed outputs. Work with
   F-Droid maintainers on the unsigned/reproducible build recipe or introduce
   an explicitly unsigned F-Droid build variant in a future reviewed release.
   Never publish keys.
3. Establish deterministic unsigned builds and compare with the upstream
   signed reference APKs using F-Droid reproducible signing procedures.
4. Verify all dependencies are permitted free software. There must be no
   proprietary Play Services dependency.
5. Prepare module-specific metadata descriptions, icon/screenshots, and
   v1.0.0 changelog without promising unverified carrier SMS support.
6. Submit separate `fdroiddata` metadata merge requests for the four
   package IDs if all four pass the same-signature/integration plan.
7. Test installed and updated packages together on spare GrapheneOS devices.
   Never use a production Device Owner handset for destructive acceptance.

## Device Owner clarification

Installing Core from F-Droid is NOT Android Device Owner provisioning.
Device Owner assignment is a separate, explicit Android management operation
for authorised eligible devices. An F-Droid UI cannot silently grant it.

## References

- https://fdroid.gitlab.io/jekyll-fdroid/docs/Inclusion_Policy/
- https://fdroid.gitlab.io/jekyll-fdroid/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://fdroid.gitlab.io/jekyll-fdroid/en/docs/Reproducible_Builds/
- https://fdroid.gitlab.io/jekyll-fdroid/docs/Security_Model/
