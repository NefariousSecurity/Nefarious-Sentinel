# Nefarious Sentinel v1.0.0 (DRAFT, NOT PUBLISHED)

## Release status

This is a release-note draft for the v1.0.0 candidate. It is **not evidence of a signed or published release**. Publication requires every gate in [RELEASE_1.0.0.md](RELEASE_1.0.0.md).

## What's changing since v0.2.0-beta1

- Promote the release family to version `1.0.0`, versionCode `4`, channel `STABLE`.
- Remove time-limited Beta expiry from stable Core and Controller.
- Preserve safety behaviour for previously expired Beta installations. Destructive features remain suppressed until the device owner authenticates and re-arms them in the newer installation.
- Hide Beta/expiry labels in stable user interface.
- Refresh documentation and security reporting guidance for the Sentinel fork.

The existing protections and four-package architecture are retained: USB data-host protection, Dead Man's Switch, Snatch Protection, debugging tamper protection, SMS helper and Device Owner Core.

## Upgrade instructions

Users of `0.2.0-beta1` should install the four matching `1.0.0` APKs from the signed release package **without uninstalling the existing Core**, since removing a Device Owner may require a reset. Verify the release signing certificate before installation.

The upgrade should preserve the Core application identity, Device Owner authority and protected configuration. If the previous Beta had already expired, open the Controller and use the authenticated re-arm flow after updating. Do not assume that installing a new APK automatically restores any previously suppressed destructive actions.

## Safety and limitations

Sentinel can irreversibly factory-reset a device. Review each trigger before enabling it. Real carrier SMS delivery acceptance was blocked during the public Beta 1 release tests; do not describe carrier ingress as fully verified unless the final acceptance pass closes that test.

Publish independently verifiable SHA-256 checksums, APK signer identity, matching source archive, licence and upstream acknowledgements alongside the signed release ZIP.

## Test evidence to include when finalising

- Exact source SHA/tag and APK SHA-256 hashes
- Build/test results for all modules
- Package IDs, versionCode/versionName, signer certificate and debuggable status
- Non-destructive GrapheneOS test summary and separately approved destructive test summary
- Upgrade acceptance from unexpired Beta and expired Beta (PIN-authenticated rearm)
- SMS carrier ingress status, security review status and known limitations
