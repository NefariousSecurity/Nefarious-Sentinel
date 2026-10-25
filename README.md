# Nefarious Sentinel

<p align="center">
  <img src="controller/src/main/res/drawable-nodpi/sentinel_raven_shield_logo.png" alt="Nefarious Sentinel raven shield emblem" width="152" />
</p>

<p align="center">
  <strong>Local-first Android device protection</strong><br />
  USB data-host safeguards · Dead Man's Switch · Snatch Protection · optional Device Owner hardening
</p>

<p align="center">
  <a href="https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/releases/tag/v1.0.0">Download v1.0.0 release candidate</a> ·
  <a href="https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/tree/v1.0.0">Browse corresponding source</a> ·
  <a href="https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/blob/main/SECURITY.md">Security reporting</a>
</p>

> **Current status:** v1.0.0 is published as a **prerelease** while final acceptance items remain open. Some opt-in features can irreversibly wipe the device. Read the [release notes](https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/releases/tag/v1.0.0) before installing.


Nefarious Sentinel is an open-source, local-first Android device protection application derived in part from **Oblivion V2** by **Lethe Labs**. It offers opt-in lock, reboot and whole-device wipe protections for supported Android devices.

**Warning:** Some protections can irreversibly factory-reset a device. Install only after reading the limitations, understanding the selected triggers and maintaining independent backups. A successful factory reset is not guaranteed under every hardware, OS or privilege condition.

## Get started

**Download:** Use the [official GitHub release](https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/releases/tag/v1.0.0). The release contains four mutually compatible APKs, a source archive and `SHA256SUMS.txt`. Confirm checksums and the [documented release signer](https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/releases/tag/v1.0.0) before installing.

**Standard Mode:** Installs as normal Android applications, with optional Device Administrator privileges. This does **not** grant Device Owner authority, and some protections are unavailable.

**Hardened Mode:** Sentinel Core must be explicitly provisioned as Device Owner on an eligible device before ordinary setup/accounts. Simply installing the APKs does not grant this authority. Provisioning may require starting with a freshly reset device. Never run a Device Owner command on a daily-use phone without understanding the consequences.

**Privacy:** Sentinel operates locally; its features do not require a Sentinel-hosted account or remote management server. All destructive protection triggers are opt-in and must be deliberately configured.

## Current release architecture

Sentinel is distributed as **four matching APKs** signed by the same release signing identity:

| Component | Android package |
| --- | --- |
| Persistent Core | `com.nefarioussecurity.sentinel` |
| Removable Controller | `com.nefarioussecurity.sentinel.controller` |
| Motion Service | `com.nefarioussecurity.sentinel.motion` |
| SMS Service | `com.nefarioussecurity.sentinel.sms` |

All four packages must be taken from the **same release**. Do not change Core's application ID after it is provisioned as Device Owner. Do not replace the release signing key for upgrades.

## Protection features

- **USB data-host protection:** detects data-capable USB hosts, with Strict USB policy and a time-limited Maintenance Mode. Normal chargers and charge-only connections are treated separately, subject to device detection limits.
- **Dead Man's Switch:** can act when a configured check-in interval expires, using Direct Boot-aware state and scheduled checks.
- **Snatch Protection:** lock or reboot actions using the separate motion helper; reboot requires Device Owner privileges.
- **SMS wipe:** optional secret/authorised-number trigger handled through the separate SMS helper.
- **Tamper protection:** policies for USB and wireless debugging, subject to Android privileges and maintenance behaviour.
- **Management access:** Sentinel-specific management PIN, rate limiting and authenticated Core/Controller communication.

Features must be intentionally configured and enabled. Review enabled state and authority before relying on a protection. Sentinel is not a substitute for strong Android device encryption and an appropriate screen lock.

## Modes and installation

- **Hardened / Device Owner:** persistent Core runs with Android Device Owner authority. This generally requires provisioning on an eligible freshly reset device. Device Owner removal can require a factory reset.
- **Standard / Device Admin:** reduced capabilities and Android-version-dependent wipe behaviour. Standard mode is not equivalent to Device Owner mode.
- **Controller:** may be removed independently of the device-owning Core where configured. The Core's management authority persists.

The stable Core and Controller retain an explicit recovery path for upgrades from **expired Beta builds**. A user whose Beta was expired must authenticate and re-arm on the newer release; installing a newer APK must not silently reactivate destructive actions.

## F-Droid availability

**Not yet listed in the official F-Droid repository.** We are preparing
[F-Droid metadata and signature-preserving reproducible builds](docs/FDROID_STATUS.md)
for all four component apps. The public v1.0.0 GitHub prerelease remains
the authoritative, developer-signed distribution for this version.

Avoid installing independently re-signed copies of Core, Controller or the
helpers: signature-protected communication depends on their shared signer.
Installing through F-Droid does not provision Android Device Owner authority.

## Release and verification

Published builds, release notes and checksums: [Nefarious Sentinel Releases](https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/releases).

For each release, verify:
1. All four APKs have the expected package IDs and version.
2. All four use the same intended release signing certificate (see release notes and pinned fingerprint).
3. The ZIP and APK SHA-256 hashes match the separately published hash manifest.
4. No package is a debuggable build.
5. The release archive contains the complete corresponding source code and the AGPL licence.

Source snapshots supplied with releases are the intended public source distribution while the development repository remains private. Never include signing keystores, credentials, local test evidence with personal information or `keystore.properties` in a source archive.

## Security limitations

- System privilege and Android version determine what lock, reboot or wipe actions can succeed.
- No app can promise protection from all invasive forensic attacks, compromised OS components or attackers with higher privilege.
- Software triggers can have false positives and false negatives. Test device-specific USB charging/host classification before enabling destructive USB policy.
- Direct Boot differs from after-first-unlock operation. Repeat the relevant checks after restart.
- A factory reset will destroy locally stored data. Device backups and safe recovery procedures are your responsibility.
- SMS support requires a compatible telephony stack, permissions and actual message delivery. Carrier delivery can be delayed or unavailable.

Report defects in the [public release repository](https://github.com/NefariousSecurity/Nefarious-Sentinel-Releases/issues). **Do not disclose vulnerability exploit details in a public issue.** See [SECURITY.md](SECURITY.md).

## Building

The project uses Android Gradle Plugin 8.2.2, Kotlin 1.9.22 and Java 17. Android SDK 34 is the configured compile target.

```sh
./gradlew test
./gradlew :app:assembleRelease :controller:assembleRelease :motion-helper:assembleRelease :sms-helper:assembleRelease
```

Release signing is configured locally using an ignored `keystore.properties` file. Never commit signing material. The release process and stop-ship gates are documented in [docs/RELEASE_1.0.0.md](docs/RELEASE_1.0.0.md).

## Licence and credits

Sentinel is provided under the **GNU Affero General Public License, version 3**. See [LICENSE](LICENSE).

Sentinel is derived in part from **Oblivion V2** by **Lethe Labs**. This project is an independent fork; the original authors are not responsible for Sentinel releases, defects or user support.

No warranty is provided to the extent permitted by law.
