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

## Verified local reproducibility and next buildserver test

On 25 October 2026 Mint successfully rebuilt **all four unsigned APKs**
from the public `v1.0.0` tag and `apksigcopier compare --unsigned` matched
them with the original developer-signed Core, Controller, Motion and SMS APKs.
The Gradle output reported 251 tasks, of which 89 came from cache. This is
a **local** reproducibility success, **not** an independent F-Droid buildserver
success.

The public `scripts/fdroid-buildserver-validate.sh` provides a fail-closed
staging/test workflow:

```bash
cd ~/Projects/Nefarious-Sentinel
git pull --ff-only origin main
bash -n scripts/fdroid-buildserver-validate.sh
bash scripts/fdroid-buildserver-validate.sh --check
```

After confirming Docker daemon access and free disk capacity (F-Droid's guide
anticipates several GB for the build image and workspace), run:

```bash
bash scripts/fdroid-buildserver-validate.sh --prepare
bash scripts/fdroid-buildserver-validate.sh --run
```

`--prepare` shallow-clones official upstream `fdroiddata` and `fdroidserver`
repositories into a NEW `~/Projects/fdroid-sentinel-validation` folder,
stages four metadata files, and removes their `disable` flags **only inside
that disposable validation workspace**. It never publishes to GitLab.

`--run` uses the published official F-Droid buildserver Docker image to
run `fdroid readmeta`, `fdroid lint`, and `fdroid build` on all four staged
applications. It mounts the separate fdroiddata workspace writable, mounts
fdroidserver read-only, and mounts **no** phones, APK signing keys or host
Docker socket into the container. The image/build itself can access the
internet and should be treated as execution of a third-party build stack.

Do not chmod the Docker socket or casually put the local user in the
`docker` group; Docker control is effectively root-equivalent on the host.

**The test may fail** due to environment-specific dependencies, source
scanner policy, incompatibility with installed F-Droid build tools, or
byte-level differences. Inspect exact output before updating recipes.
A green Gradle build alone is not sufficient evidence of binary equivalence
on the F-Droid buildserver.

Official docs:
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Reproducible_Builds/

## First container test result, 25 October 2026

The official F-Droid `buildserver` image was downloaded successfully and
`fdroid readmeta` and `fdroid lint` passed for all four draft packages.

**Builds did not pass**: all four `fdroid build` attempts failed during
Gradle 8.2 initialisation at `FileAccessTimeJournal` with
`For input string: ""`. Each package fetched the intended public
`v1.0.0` source revision `4a7c3a55623203116dda4d2aa293baf8c0d608b5`.
Gradle did not reach Android compilation.

The first iteration of our script incorrectly reported `PASS` because
`fdroid build` can return status zero despite reporting failed builds.
This is a **false positive, not a passed F-Droid build**. The script now
uses `fdroid build --stop` and requires an expected unsigned APK output
for each package, and attempts a Gradle stacktrace on failures.

Do not submit or enable public recipe drafts yet. The initial error
`serverwebroot: has blank value` was also reported by the test
configuration but was not the immediate cause of the Gradle failure.
Collect the stacktrace before changing build tools, dependency versions,
or the immutable v1.0.0 tag.

## Second container test, 25 October 2026

The fail-closed build runner now stops correctly on failed binary verification.

- **Core:** F-Droid build PASS and verified reproducible against the exact
  developer-signed GitHub APK. Allowed signer pin confirmed.
- **Controller:** F-Droid Gradle compilation PASS, but binary verification
  FAIL. Differences: `classes.dex` and `assets/dexopt/baseline.prof`.
  The signature-copy step also reported
  `APK Signing Block offset < central directory offset`.
  Those output differences must be investigated before submission.
- **Motion and SMS:** Not attempted in this test because the runner stopped
  at Controller. Their earlier *local Mint* reproducibility tests passed.

The original container's `serverwebroot` warning persists, but Core's
successful full build and verification shows it is not an unconditional
build blocker.

A new read-only diagnostic,
`python3 scripts/fdroid-controller-diff.py`, compares SHA-256 hashes
and file sizes of ZIP entries from the released Controller, the container's
unsigned Controller and, when still present, the Mint scratch build.
No source, APK, key or device changes are made.

F-Droid documents potential non-determinism in baseline profiles,
compiler/dex output, build toolchain versions, and parallelism. Do not
modify the immutable v1.0.0 source tag or republish APKs solely to force
a match. Investigate the actual differences first.

References:
- https://f-droid.org/docs/Reproducible_Builds/
- https://github.com/obfusk/apksigcopier

## Third container test, 25 October 2026

With `scripts/fdroid-buildserver-validate.sh --run`, using Debian OpenJDK
**21.0.12.1**, x86_64 and 24 visible CPUs, all four metadata lint checks
completed without reported errors.

- **Core:** PASS, previously successfully rebuilt and verified against the
  original developer-signed APK with certificate pinned.
- **Motion:** PASS, newly rebuilt and verified against the original
  developer-signed APK with certificate pinned.
- **SMS:** PASS, newly rebuilt and verified against the original
  developer-signed APK with certificate pinned.
- **Controller:** Gradle build PASS but signature-preserving binary comparison
  FAIL. Confirmed same two changed archive members as prior run:
  `classes.dex` and `assets/dexopt/baseline.prof`. Earlier Mint unsigned
  Controller build matched all 150 ZIP member contents in the published APK.
- **Result:** `PASS: 3 of 4, FAIL: 1 of 4` and runner exits with failure.

The current container reports 24 CPUs; the original Mint build's Java
runtime and available CPUs have not yet been recorded, so a toolchain/CPU
mismatch is an **unconfirmed hypothesis**. F-Droid's reproducibility
documentation specifically calls out baseline.prof nondeterminism, CPU-count
dependent DEX generation and Java/R8 version differences. First compare
the original host Java/CPU environment to the container; then test
reproducibility with a targeted, isolated change. Keep the public v1.0.0
tag, signing identities and published APKs immutable.

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
