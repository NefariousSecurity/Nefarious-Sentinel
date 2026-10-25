# Contributing to Nefarious Sentinel

Thanks for helping improve Sentinel. It is security-sensitive Android software
with opt-in actions that can lock, reboot or irreversibly wipe a device.

## Reporting safely

- Public [GitHub Issues](https://github.com/NefariousSecurity/Nefarious-Sentinel/issues)
  are for ordinary bugs, documentation corrections and compatibility reports.
- Suspected security vulnerabilities must be disclosed **privately** via
  [Security Advisories](https://github.com/NefariousSecurity/Nefarious-Sentinel/security/advisories).
  See [SECURITY.md](SECURITY.md).
- Never disclose signing keys, account identifiers, phone numbers, PINs,
  customer logs or destructive proof-of-concept steps in a public issue.
- Test destructive protections only on expendable, independently backed-up
  hardware. Never test wipe behaviour on a daily-use phone.

## Code and release integrity

- Source code is AGPL-3.0 with attribution to Oblivion V2 by Lethe Labs.
- The Core, Controller, Motion and SMS packages are intentionally signed by the
  same production identity. Never include release signing credentials in a PR.
- Device Owner is a privileged Android provision step, not an automatic
  consequence of installing a package.
- Do not re-sign or overwrite published release binaries. Changes to application
  code belong in a subsequent version, with matching source and test evidence.
- Include a minimal reproducible test and note Android version/device model
  when proposing a behavioural change, without posting uniquely identifying data.

Public source is available under the [v1.0.0 tag](https://github.com/NefariousSecurity/Nefarious-Sentinel/tree/v1.0.0).
