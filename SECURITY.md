# Security policy for Nefarious Sentinel

This policy applies to the **Nefarious Sentinel** fork maintained by NefariousSecurity, not to the original Oblivion V2 project by Lethe Labs.

## Reporting a vulnerability privately

Please **never post exploit details, bypass steps, device identifiers, secrets, or destructive proof-of-concept instructions in a public issue**.

Use GitHub's **Report a vulnerability** option for the [Nefarious Sentinel Releases repository](https://github.com/NefariousSecurity/Nefarious-Sentinel/security) when that option is enabled. If it is unavailable, open a **non-sensitive** issue asking the maintainers to provide a private reporting channel, without disclosing vulnerability details.

**Stable-release gate:** Maintainers must verify a working private reporting channel before v1.0.0 publication.

## Include in a private report

- Affected version/tag and Android OS version
- Model and Device Owner / Device Admin status
- A clear summary of impact and reproducibility
- Redacted diagnostic evidence (never keys, PINs or other users' data)
- Whether there is a risk of an unintended wipe or failure to wipe

Destructive issues and remote-trigger authentication bypasses are release blockers. Do not test on another person's device or on a device containing irreplaceable data.

## Support and source

For non-sensitive feature requests and ordinary bugs, use [release repository issues](https://github.com/NefariousSecurity/Nefarious-Sentinel/issues).

Nefarious Sentinel is licensed under AGPL-3.0. Each distributed binary release must have corresponding source available with licence notices and appropriate upstream attribution.
