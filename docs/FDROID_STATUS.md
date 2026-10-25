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

## Host versus container toolchain check, 25 October 2026

Mint host and F-Droid container each expose **24 CPUs** on **x86_64**.
Both report OpenJDK **21.0.12.1** dated 2026-08-18. Mint uses the Ubuntu
24.04 build `21.0.12.1+1-1-24.04.4-Ubuntu`; the container uses Debian 13
`21.0.12.1+1-1-deb13u1-Debian`. These are matching *version numbers*,
not necessarily byte-identical Java packages. No JDK change is justified
without further evidence.

The read-only `scripts/fdroid-controller-diff.py` now also reports DEX
header table counts, string-table additions/removals, and the first differing
non-header byte offsets between the original signed Controller and F-Droid's
unsigned Controller. This can distinguish changed string pools or table
sizes from differences in method code or ordering. It does **not** prove
semantically equivalent bytecode.

## Controller DEX table findings, 25 October 2026

Read-only Controller APK diagnostics compared the developer-signed APK with
F-Droid's rebuilt Controller:

- Both DEX files have 9,413 strings, 3,345 types, 3,572 prototypes,
  9,120 fields, 13,290 methods and 2,726 class definitions.
- The sole string-set substitution found was an embedded R8 8.2.4 metadata
  record: `pg-map-id` was `9f7a658` in the released APK and `e168c74`
  in the F-Droid rebuild.
- The released DEX is 2,053,600 bytes; the F-Droid DEX is 2,053,516
  bytes. Therefore the differing map-id string alone **does not yet explain**
  the 84-byte difference. `baseline.prof` differs by two bytes.
- The earlier Mint rebuild matched **all 150 archive members** of the
  published Controller APK.

Next inspect the R8 `mapping.txt` output from Mint and F-Droid using a
read-only text comparison. If their contents differ, distinguish
reordering-only changes from different obfuscation assignments before
trying compiler or baseline-profile workarounds.

F-Droid discusses R8 optimizer and profile nondeterminism:
https://f-droid.org/docs/Reproducible_Builds/

## Controller R8 mapping comparison, 25 October 2026

A read-only `mapping.txt` comparison between the earlier Mint build and the
F-Droid build established:

- Mint mapping: 190,853 lines, SHA-256
  `0fefdb4b5b88a5cff775452a3f4242bd4f45755bf695af438b212df37e24a5c7`.
- F-Droid mapping: 190,850 lines, SHA-256
  `00629d2cc790521e0e4443cf440dac3dd4b5c5916873740a204cb1b8fc04454c`.
- Sorting both maps does not eliminate the discrepancy. Both include the
  same R8 8.2.4 metadata format, but their `pg-map-id` and
  `pg-map_hash` fields differ.
- The visible diff concentrates on line mappings for
  `SentinelControllerApp(...)` and related Compose operations in
  `SentinelControllerAppKt`. This is **not evidence that application
  source changed**, since both builds use the same immutable tag. It is
  evidence of different build-generated mapping output.

Next isolate whether the *pre-R8 compiled Kotlin* `.class` files differ
between the Mint build and the F-Droid container. If they match, focus on
R8 inputs/dependencies and nondeterminism; if not, inspect the Kotlin
compile pipeline before changing R8. Do not overwrite published APKs,
immutable tags, or signing material.

## Controller pre-R8 Kotlin class comparison, 25 October 2026

A read-only comparison of the **453** Controller Kotlin `.class` files from
the earlier Mint build and current F-Droid build found **443 identical** and
**10 different**. Both builds contain exactly 453 class files. The differences
cluster in Compose UI classes under
`controller/ui/SentinelControllerAppKt` and
`controller/feature/about/AboutScreenKt`.

**Implication:** The input bytecode to R8 is already nonidentical.
The different R8 mapping and `classes.dex` can be downstream effects.
R8 cannot be identified as the sole origin of the difference.

The new read-only script
`python3 scripts/fdroid-controller-class-diff.py` uses the local
`javap -c -p -s` command on each of the ten different class files, reports
whether their disassembled instructions differ, and displays a capped diff
for the main UI Kotlin class. This is a diagnostic, not semantic
equivalence verification.

Keep the four public F-Droid candidates disabled pending all four
developer-signed binary verification passes.

## JVM disassembly of the pre-R8 Controller mismatch, 25 October 2026

The ten differing Kotlin class files were inspected using the same
host `javap -c -p -s` tool. **All ten have different disassembly**, including
the main `SentinelControllerAppKt.class`, where Compose-generated parameter
change checks and other compiler instructions differ between the Mint
reference and F-Droid output.

This rules out an explanation involving **only** R8 mapping metadata,
DEX header bytes or baseline profile metadata. It does not establish
whether resulting app behaviour differs, and does not by itself show
different source code.

The former Mint reproducibility test reported **89 Gradle tasks from
cache**. Since F-Droid performed its own compilation, the next targeted
experiment is to compile the immutable `v1.0.0` Controller's Kotlin
classes in a completely new Mint scratch tree with
`--no-build-cache --rerun-tasks`, using
`scripts/fdroid-controller-clean-kotlin-test.sh`. The resulting class
hashes will be compared to the prior Mint and F-Droid outputs.
This test does not alter the signed release or the source tag.

## Fresh Mint Kotlin compilation confirms cached-build provenance concern, 25 October 2026

A second source checkout was produced from the immutable public source commit
`4a7c3a55623203116dda4d2aa293baf8c0d608b5` and the Controller Kotlin
compile task was run with `--no-build-cache --rerun-tasks`.
Gradle reported 29 tasks, **all 29 executed**.

All three class trees contained exactly 453 Kotlin `.class` files.
Comparisons reported:
- Original Mint (previously used to reproduce the published APK) vs F-Droid:
  **10 differing**.
- Original Mint vs freshly compiled Mint: **10 differing**.
- Freshly compiled Mint vs F-Droid: **0 differing**.

The mismatch in the original developer-signed Controller corresponds to
compile-generated Compose classes that do not match the output of two separate
fresh build environments. This **strongly suggests** stale/incrementally or
cached pre-R8 class output in the earlier Mint build, but does not yet
prove which process supplied it or whether the original source had been
dirty at build time.

Do NOT alter the immutable v1.0.0 tag or overwrite signed APK assets to
make F-Droid pass. Next independently repeat the no-build-cache
Kotlin compilation from a second fresh tagged source extraction and
confirm that it again matches F-Droid. If a pristine build continues to
diverge from the published Controller, plan a new coherently versioned,
developer-signed release built from clean source and verify it before
publication. A version replacement would need a deliberate release process
and versionCode increase, and must retain compatible signing identities.

## Original private build artifact provenance, 25 October 2026

A read-only comparison of Controller Kotlin `.class` artifacts from:
1. `~/Projects/oblivion-v2` (private original build),
2. the earlier Mint tagged-source rebuild,
3. a new no-Gradle-cache Mint tagged-source compilation, and
4. the F-Droid container tagged-source compilation

found exactly **453 classes in each output tree**. The original private
project matched the earlier Mint rebuild in all 453 classes. It differed
from both fresh Mint and F-Droid in the **same 10 classes**. Fresh Mint
and F-Droid matched in all 453 classes.

This confirms that the original published Controller's bytecode corresponds
to the private project's retained output and that the earlier matching
tagged-source rebuild does **not** establish independent clean-build
reproducibility. A cache or incremental compilation issue, build inputs not
captured in the tag, or compiler nondeterminism are hypotheses. The exact
historical cause is not established.

**Next:** compare the two affected *current private working-tree source*
files and Gradle configuration files with the immutable public tag,
including staged/unstaged changes. Also inspect relevant Git history and
build logs if available. Do not assume that an identical current working
tree proves the source was identical when the original APK was built.
Do not change v1.0.0 tag or signed release assets to force a match.

## Original compiled BackHandler verified, 25 October 2026

A targeted read-only `javap -c -p` inspection found the
`androidx.activity.compose.BackHandlerKt.BackHandler(...)` invocation
in **all three Controller pre-R8 builds**:
- Original private release: invocation at JVM bytecode offset 704.
- Fresh no-build-cache Mint: invocation at offset 643.
- F-Droid container: invocation at offset 643.

Therefore, the original Controller **does include the system Back
navigation handler**, and the hypothesis that its compiled output
predates commit `cdf4898` *with that handler absent* is contradicted by
direct evidence. Differences remain in other compiled Compose instructions,
so the full Controller release still cannot pass independent F-Droid
binary verification. No conclusion about runtime equivalence follows
from these invocation offsets alone.

Next: compare individual JVM methods/instruction streams from the
original and fresh Kotlin class outputs, rather than infer a missing
feature from class-file hashes.

## v1.0.1 candidate: TWO independent pristine Mint builds match, 25 October 2026

The private development branch `release/v1.0.1-rc1` was committed as
`cd76b4b`, changing shared `versionCode` 4 -> 5 and `versionName`
1.0.0 -> 1.0.1; stable channel and beta-disabled settings remain.
**No public v1.0.1 tag or signed APKs have been published.**

Two independent source archive extractions from that commit were built with
`./gradlew --no-daemon --no-build-cache --rerun-tasks`. Each performed
**251 tasks executed** and produced four unsigned APKs. Whole-file SHA-256
comparisons matched between the builds for **all four APKs**:

| App module | Matching SHA-256 |
| --- | --- |
| `app` (Core) | `feab9330016b9b05e095c3ed8fe6159106831f072021ce6d971c40b2fef2121e` |
| `controller` | `30d1a4a4ac2ae980ae6d22cc8a99d9b3567aa7e1b1cab887e08c15e8ebb75ba9` |
| `motion-helper` | `a01c0518d7d1e4199ef2c5f8d27c7bc02d81eba44698e64037f41736ca5dff09` |
| `sms-helper` | `1593dacdd99bcd679240947eb483fc9c92b3fd0b726939d6c5e03fc0af0d508e` |

Workspaces on Mint:
`~/Projects/fdroid-sentinel-validation/v101-clean.Fc2Z6qIm` and
`~/Projects/fdroid-sentinel-validation/v101-verify.LyJF6fd6`.

This proves *repeatable pristine Mint builds*, not independent
F-Droid-buildserver reproducibility. The existing fdroiddata test recipes
and validation runner still refer to `v1.0.0`, `versionCode 4` and
published developer-signed v1.0.0 binaries. **Do not run those scripts
unchanged as a supposed v1.0.1 validation.** Before publishing anything,
rebuild the unpublished candidate source in the F-Droid container from an
isolated sanitised copy without private keys, then compare artifacts.
Final `Binaries` + `AllowedAPKSigningKeys` verification needs an actual
new public source revision and new verified developer-signed APKs.

## v1.0.1 F-Droid container SDK preflight diagnosis, 25 October 2026

The first run of `scripts/fdroid-v101-container-check.sh --run` passed
source/commit/clean-tree, reference APK, and Docker checks, but stopped
**before Gradle** with `STOP: Android SDK 34 unavailable inside container`.

A read-only inspection of `registry.gitlab.com/fdroid/fdroidserver:buildserver`
as user `vagrant` confirmed that `ANDROID_HOME=/opt/android-sdk`,
that this directory was writable but contained **no platforms or
build-tools**, and that `/usr/bin/sdkmanager` was available.

The v1.0.1 test runner now provisions explicit packages
`platforms;android-34`, `build-tools;34.0.0` and `platform-tools`
with `sdkmanager --sdk_root=/opt/android-sdk` inside the **ephemeral
container**, prior to invoking Gradle. It checks that Android 34 and
build tools 34 exist before proceeding. It does **not** change the
host SDK or automatically accept Android SDK licences; failures in
network/provisioning/licences must be resolved explicitly.

This SDK preflight failure is **not an APK build or binary comparison
failure**. Independent F-Droid container compilation remains
unverified until a new test completes.

## v1.0.1 unsigned F-Droid buildserver-image verification PASS, 25 October 2026

The updated `scripts/fdroid-v101-container-check.sh` provisioned SDK
platform 34, build-tools 34.0.0 and platform-tools inside the official
`registry.gitlab.com/fdroid/fdroidserver:buildserver` image. The build
completed **251 executed Gradle tasks**, using
`--no-daemon --no-build-cache --rerun-tasks`.

**PASS: all four whole-file unsigned APK SHA-256 hashes are byte-identical
between the independent F-Droid-container build and the two previously
verified clean Mint builds.**

| Module | Verified SHA-256 |
| --- | --- |
| Core (`app`) | `feab9330016b9b05e095c3ed8fe6159106831f072021ce6d971c40b2fef2121e` |
| Controller | `30d1a4a4ac2ae980ae6d22cc8a99d9b3567aa7e1b1cab887e08c15e8ebb75ba9` |
| Motion | `a01c0518d7d1e4199ef2c5f8d27c7bc02d81eba44698e64037f41736ca5dff09` |
| SMS | `1593dacdd99bcd679240947eb483fc9c92b3fd0b726939d6c5e03fc0af0d508e` |

Container scratch build (preserved):
`~/Projects/fdroid-sentinel-validation/v101-fdroid.mQnwuj7g`.

This proves cross-environment reproducible **unsigned** candidate APKs,
NOT yet the final F-Droid `fdroid build` + published
developer-signed `Binaries` verification. The public v1.0.1 source
tag and corresponding developer-signed APKs have **not** been published.
Remaining gates: regression tests, controlled release signing with the
existing signer certificate, source/release publication after review,
and signature-preserving binary verification via correctly versioned
F-Droid metadata. Original v1.0.0 assets/tag remain immutable.

## v1.0.1 release unit tests: version assertion fixed, awaiting rerun

The initial candidate scratch `./gradlew :app:testReleaseUnitTest
:controller:testReleaseUnitTest` run completed Controller tests and
reported 55 Core tests, **one failed** at
`StableReleaseBuildConfigTest.kt:32`. This assertion checked
`BuildConfig.VERSION_NAME.startsWith("1.0.0")`, and a later assertion
still expected `BuildConfig.VERSION_CODE == 4`, despite deliberate
candidate version 1.0.1/code 5. Assertions for channel `STABLE`,
`BETA_BUILD == false` and zero beta expiry executed before the failed
version assertion.

Private release branch now has a **test-only** fix in commit
`ccd6c1419440acc49d8909af4049c6482f77442e`: version-name
expectation 1.0.1 and version code expectation 5. No production source
or Gradle release configuration changed. Test rerun still pending;
do not claim release regression testing has passed until verified.

The container diagnostic runner's reviewed private source commit was
advanced to `ccd6c1419440acc49d8909af4049c6482f77442e` to avoid
misleading runs against previous commit `cd76b4b`.
Previously verified unsigned APK bytes were generated from `cd76b4b`.
Because this change only affects tests, the release APK content is
expected to remain the same, but **that expectation has not been
independently reverified on the updated source revision**. Keep the
release blocked pending unit tests, reproducibility recheck and
signing/release gates.

## v1.0.1 release JVM unit-test rerun PASS, 25 October 2026

The release test suite was rerun on Mint from a fresh extracted copy of
private commit `ccd6c1419440acc49d8909af4049c6482f77442e`.
Verification confirmed that the *only* change since the prior v1.0.1
candidate (`cd76b4b`) was in
`app/src/test/java/com/localruntime/core/beta/StableReleaseBuildConfigTest.kt`:
the test's expected version name and version code were updated to
`1.0.1` / `5`, respectively.

The two release test tasks
`:app:testReleaseUnitTest` and `:controller:testReleaseUnitTest`
**PASS**, with Gradle reporting **91 actionable tasks, all 91 executed**,
`BUILD SUCCESSFUL` in 22 seconds, using
`--no-build-cache --rerun-tasks`. No device-level instrumentation or
destructive tests were run.

Test workspace:
`~/Projects/fdroid-sentinel-validation/v101-tests.HRcBpLIK`.

Remaining verification: the existing byte-identical Mint and F-Droid
container APKs were built from `cd76b4b`, while the candidate branch
now points to test-only commit `ccd6c14`. Rebuild from the *final*
commit to confirm signed-release payload bytes remain unchanged before
signing and publication. This JVM test success alone is not device
acceptance testing.

## Final v1.0.1 source commit: F-Droid container comparison PASS, 25 October 2026

A fresh F-Droid buildserver-image run compiled the private release-candidate
commit `ccd6c1419440acc49d8909af4049c6482f77442e`, which adds only
the corrected `StableReleaseBuildConfigTest` expectations.
Build logs show **251 actionable Gradle tasks, all 251 executed** and
`BUILD SUCCESSFUL`. All four **whole-file SHA-256 hashes** matched the
preserved first pristine Mint build from `cd76b4b`, demonstrating that
the intervening test-only commit did not alter any of the candidate
APK bytes:

- Core: `feab9330016b9b05e095c3ed8fe6159106831f072021ce6d971c40b2fef2121e`
- Controller: `30d1a4a4ac2ae980ae6d22cc8a99d9b3567aa7e1b1cab887e08c15e8ebb75ba9`
- Motion: `a01c0518d7d1e4199ef2c5f8d27c7bc02d81eba44698e64037f41736ca5dff09`
- SMS: `1593dacdd99bcd679240947eb483fc9c92b3fd0b726939d6c5e03fc0af0d508e`

New validated scratch output:
`~/Projects/fdroid-sentinel-validation/v101-fdroid.6ZBw7rrs`.

**Important distinction:** This confirms the unsigned APK build against
the F-Droid `buildserver` container image, not the final
`fdroiddata` metadata / `Binaries` signed-APK check. Before publishing,
verify version/package identities and the v1.0.0 signing-certificate
fingerprint, then sign **new artifacts** under controlled conditions
with the same release key; keep original v1.0.0 Git tag and APK assets
unaltered. Regression tests, signature compatibility and signing-key
security remain separate required gates.

## v1.0.1 pre-signing identity and original signer verification PASS, 25 October 2026

Read-only pre-signing check passed for all four APK modules in both the
clean Mint and final F-Droid container build directories:
- `aapt dump badging` package IDs unchanged across releases.
- Version name `1.0.1`, version code `5` for Core, Controller,
  Motion and SMS.
- All eight inspected unsigned APK paths matched the previously recorded
  and independently reproducible SHA-256 hashes.
- The container's `SOURCE_COMMIT.txt` identified final private source
  `ccd6c1419440acc49d8909af4049c6482f77442e`.
- All four original v1.0.0 developer-signed reference APKs passed
  pinned-file SHA-256 and `apksigner verify --print-certs`, retaining
  the expected certificate identity
  `46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`.

**Not done yet:** Verify secure access to the existing private signing
key without disclosing credentials; sign new v1.0.1 release artifacts
without overwriting any v1.0.0 reference; verify signed binary
reproducibility and cert continuity; conduct safe non-destructive
on-device upgrade regression tests; publish the matching public source
tag and new release only after all acceptance gates pass.

## v1.0.1 signing configuration pre-flight PASS, 25 October 2026

A local, read-only Python check of the private release checkout
`~/Projects/oblivion-v2` verified that `keystore.properties`
exists and is Git-ignored, all expected signing properties are present,
and `storeFile` resolves for Core, Controller, Motion and SMS to the
**same existing keystore**. Both the keystore and properties file are
owned by the local user and their permission bits do not grant
group/other access.

**No private key material or passwords were printed**, and no APK was
signed. The certificate for the configured keystore alias has **not yet
been compared** with the original v1.0.0 APK signer. That cryptographic
certificate identity check is the immediate remaining gate before
release-signing preparation. This pre-flight does not independently
verify that the key-entry password will work during signing.

## Signing alias certificate continuity confirmed, 25 October 2026

A read-only local `keytool -list -v` inspection confirmed that the alias
specified in the Git-ignored `keystore.properties` is a **PrivateKeyEntry**.
A subsequent `keytool -exportcert` produced a certificate whose SHA-256
is **exactly**
`46520e33367a8f8c65980e98a06df9091e6dfa65b157ddf78d4fdbd9d97beeb7`,
matching the original four signed v1.0.0 APKs. Credentials and the
private key were not displayed or copied and no APK was signed during
this check.

**Next gate:** Carefully controlled signing of NEW v1.0.1 artifacts
using the existing keystore alias, writing to a fresh 0700 staging
directory only, then validate their signatures, package identity,
digest and `apksigcopier compare --unsigned` against independently
rebuilt unsigned binaries. This proves certificate compatibility,
not yet installed-device upgrade acceptance or final official
F-Droid `fdroiddata`/published-`Binaries` verification.

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
