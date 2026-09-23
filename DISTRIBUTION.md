# Distribution Plan / 分發方案

Goal: the Android app should be installable without requiring Google Play, Google Play Services, or a single download host.

## Recommended public channels

1. **Official direct APK** — publish the same signed APK on a simple HTTPS website.
2. **Mainland-China-friendly mirror** — mirror the exact same signed APK and SHA-256 on a repository/release host reachable by the target audience (for example a Gitee Release or another compliant domestic file host).
3. **GitHub Releases** — useful secondary international mirror, but never the only download source.
4. Optional Android stores can be added later after their developer-account and review requirements are met.

Do not promise that one particular website is permanently reachable from every region. Use at least two independent mirrors.

## Release file set

Every release should publish:

- `VirtualCompanion-vX.Y.Z.apk` — stable, signed APK
- `SHA256SUMS.txt` — checksum of the exact APK
- `update-manifest.json` — machine-readable version + mirror URLs
- release notes

All mirrors must serve **the same APK bytes**, not separately rebuilt APKs, so users can compare SHA-256 values.

## No Google runtime dependency

The current core app uses Android framework / AndroidX APIs and does not require Google Play Services at runtime. Cloud AI providers are optional. Strict Offline Mode removes the need for cloud AI entirely.

## Release signing

Public APKs must be signed with one long-lived release key. Keep that key private and backed up. Never publish the keystore or passwords. Updates must be signed by the same key.

## Website template

`distribution-site/` is intentionally plain HTML/CSS with no Google Fonts, CDN JavaScript, analytics, or external assets. Copy it to any static HTTPS host and replace the example URLs.

## Mainland China

For users in mainland China, do not make Google Play, Firebase, GitHub, Gemini, or any other single foreign service a prerequisite for installation or basic use. Provide a direct APK mirror and keep the offline engines usable. Exact service availability can change, so maintain multiple mirrors and test them periodically from the intended region.
