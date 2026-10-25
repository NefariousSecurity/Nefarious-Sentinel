# Nefarious Sentinel: public listing and visual asset guide

## Naming and identity

- **Product name:** Nefarious Sentinel
- **Short name for phone launcher:** Sentinel
- **Publisher:** NefariousSecurity
- **Licence:** GNU AGPL-3.0
- **Current release:** v1.0.0 **release candidate / prerelease**
- **Canonical artwork:** `controller/src/main/res/drawable-nodpi/sentinel_raven_shield_logo.png`.
- **Source:** https://github.com/NefariousSecurity/Nefarious-Sentinel
- **Release:** https://github.com/NefariousSecurity/Nefarious-Sentinel/releases/tag/v1.0.0
- **Security reports:** https://github.com/NefariousSecurity/Nefarious-Sentinel/security/advisories

Do not describe this prerelease as finally accepted until the remaining release gates are closed. Do not claim live SMS carrier ingress has been verified.

## GitHub profile and social preview assets

Run `python3 scripts/make-brand-assets.py` from this checkout. It reuses the
canonical raven/shield artwork and writes two images under `branding-assets/`:

- `nefarioussecurity-avatar.png` for the GitHub account profile picture.
- `nefarious-sentinel-social-preview.png` for repository Settings > Social preview.

These assets are NOT automatically uploaded by the generator. Account avatar
and GitHub social preview changes require an authorised upload in the web UI.
If Pillow is missing on Linux Mint, install the distribution's `python3-pil`.

Repository description, topics, Issues and private vulnerability reporting can
be configured by an administrator using
`bash scripts/configure-public-repository.sh --check` followed by `--apply`.

## Suggested listing copy

**One-line summary**

Local-first Android device protection with opt-in security safeguards.

**Long description**

Nefarious Sentinel is an open-source Android security application for people who want direct local control over their device protections. It offers configurable USB data-host safeguards, a Dead Man's Switch, Snatch Protection and local tamper controls.

Sentinel has two operating modes. Standard Mode uses Android Device Administrator capabilities and has reduced functionality. Hardened Mode requires explicitly provisioning the persistent Core as Android Device Owner on an eligible device. Installing the APKs does not itself grant Device Owner privileges.

The removable Controller manages the persistent Core through signature-protected communication. Motion and SMS functions use separate helper apps. All four matching APKs share the same developer signing identity.

**Important safety notice:** Certain optional settings can lock, reboot or irreversibly wipe a device. Understand and test protection rules on appropriately backed-up hardware before enabling them. Operation depends on Android version and the permissions granted. Carrier SMS-triggered operation remains unverified for this release candidate.

No remote Sentinel management account is required for the core local protection workflow.

**Upstream attribution:** Based in part on Oblivion V2 by Lethe Labs. Independent project, no upstream endorsement.

## Authentic screenshots to capture (no mock-ups or personal data)

Capture from the production-signed Controller on a disposable Pixel with dummy settings:

1. Dashboard in **STANDARD** mode, with verified Core; protections disabled.
2. Dashboard in **HARDENED** mode, clearly showing Device Owner.
3. USB data-host protection settings and Maintenance Mode (no unsafe triggers armed).
4. Dead Man's Switch screen, showing interval and state, without sensitive PINs.
5. Snatch Protection and its Lock/Reboot choices.
6. About screen with version and licence/source links (updated link in post-v1.0.0 development branch).

Mask serial numbers, phone numbers, network names, names, device IDs, PINs, and unique debug tokens. Use real app screenshots only; no fabricated security-status screenshots.

## F-Droid packaging note

Core, Controller, Motion, and SMS helper are four distinct Android package IDs. Do not submit just the Controller and imply Device Owner can be activated by downloading it. All four same-signer dependencies, reproducible-build policy and store signatures need explicit resolution. The release APKs must never be re-signed casually, or upgrades and inter-app signature permissions may break.

Public listing copy is draft marketing metadata, not acceptance-test evidence.
