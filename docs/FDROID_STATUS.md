# Nefarious Sentinel F-Droid inclusion status (2026-10-25)

**Current status:** Preparation in public GitHub; **not submitted to or
approved by the official F-Droid repository**.

## Agreed distribution model

We intend to ship the **existing developer-signed APKs**, provided each one
is independently reproducible from the corresponding public source tag.
This is critical because all four apps use signature-protected permissions,
and current users require compatibility with the already-published APKs.

The pinned signer certificate SHA-256 is:
`46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`.

F-Droid normally manages signing keys independently for each application.
Their documentation supports exclusively publishing verified upstream-signed
APK files via `Binaries` and `AllowedAPKSigningKeys`. That is our preferred
route. We MUST NOT accept four independent F-Droid certificates without first
designing and testing a protocol/signature-permission migration.

Documentation:
- https://f-droid.org/docs/Reproducible_Builds/
- https://f-droid.org/docs/Security_Model/
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/

## Prepared files

- `app/fastlane/metadata/android/en-US/`: Core description and changelog.
- `controller/fastlane/metadata/android/en-US/`: Controller description.
- `motion-helper/fastlane/metadata/android/en-US/`: Motion Service listing.
- `sms-helper/fastlane/metadata/android/en-US/`: SMS Service listing.
- `fdroid-candidates/metadata/com.nefarioussecurity.sentinel*.yml`: Four
  **disabled draft** fdroiddata metadata recipes. Each pins the common signing
  certificate, upstream binary URL and public `v1.0.0` source tag.
- `scripts/fdroid-repro-check.sh`: Builds an **unsigned** copy of source from
  the immutable public `v1.0.0` tag in a scratch directory, then compares it
  with the four verified upstream-signed APKs using `apksigcopier`.

These files are on the public `main` branch **after** the immutable v1.0.0
tag. F-Droid may need the metadata supplied explicitly via fdroiddata for
v1.0.0; future source tags will include the current main metadata.

## Reproducibility trial on Mint

From the public checkout `~/Projects/Nefarious-Sentinel`:

```bash
git pull --ff-only origin main
bash -n scripts/fdroid-repro-check.sh
bash scripts/fdroid-repro-check.sh --check
```

This first step is **read-only**. It checks that the four locally-built
official signed release APKs still exist in
`~/Projects/oblivion-v2/*/build/outputs/apk/release` and match the
published hashes/signing identity.

When ready to rebuild, install `apksigcopier` from a trusted package source
and run:

```bash
bash scripts/fdroid-repro-check.sh --build
```

That command makes a temporary tagged source extraction, deletes the four
Gradle `signingConfig signingConfigs.release` assignments **only inside
the scratch copy**, invokes the four release Gradle tasks, and compares the
unsigned outputs against the signed originals using
`apksigcopier compare --unsigned`. It does not need the private key,
change the public `v1.0.0` source tag, touch Android devices, or overwrite
the signed release APKs.

Important: local reproducibility does not itself prove F-Droid buildserver
reproducibility. A distinct F-Droid build container/CI run is also required.

## Outstanding gates before enabling any draft fdroiddata recipe

1. All four APKs rebuild reproducibly from the immutable public tag.
2. Run `fdroid lint` and `fdroid build` for the candidate recipes in
   the official fdroiddata build environment; resolve scanner/Gradle issues.
3. Confirm all dependencies and graphic assets are FOSS/redistributable.
4. Supply real, redacted screenshots from disposable devices for the
   Controller; no synthetic security-status screenshots.
5. Confirm maintainers accept the four tightly coupled packages and
   understand that Core has no ordinary launcher UI. Developer signed
   `Binaries` with identical certificates is essential for IPC compatibility.
6. Ensure the proposed F-Droid listing correctly describes privileged
   Device Owner provisioning and irreversible wipe risks, and discloses
   that real carrier SMS ingress remains unverified.
7. Only after passing the above, enable the recipes and open a merge request
   to https://gitlab.com/fdroid/fdroiddata.

F-Droid metadata updates do NOT retroactively modify the signed v1.0.0
GitHub release, its APK hashes, or its signing certificate.

## Links

- Public source: https://github.com/NefariousSecurity/Nefarious-Sentinel
- Release: https://github.com/NefariousSecurity/Nefarious-Sentinel/releases/tag/v1.0.0
- fdroiddata: https://gitlab.com/fdroid/fdroiddata
