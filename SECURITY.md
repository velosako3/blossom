# Security

## Reporting a vulnerability

Please report security problems privately, through GitHub: **Security → Report a vulnerability** on this repository
(<https://github.com/velosako3/blossom/security/advisories/new>). Don't open a public issue for them.

Include what you found, how to reproduce it, and which version or commit you tested. You'll get an answer as soon as
possible. Once a fix ships, the advisory is published with credit to you, unless you'd rather stay anonymous.

## Supported versions

Only the latest release gets security fixes. The app's updater offers new releases automatically.

## What blossom does to stay safe

- Every installer is built on GitHub Actions from a tagged commit. Each release has `SHA256SUMS` files to check
  downloads against.
- The Android app is signed with one release key, so Android refuses an update signed by anyone else.
- Bundled libVLC is downloaded over HTTPS, and every file is checked against a published SHA256 before it goes into an
  installer.
- Sign-in tokens are kept in the system's secret store: Android Keystore encryption, or the desktop keyring. Without a
  keyring, blossom uses a file only your user can read (0600). Tokens go only to the service they belong to. Android
  backups are off.
- No telemetry. The app talks only to the services you use.

Note that the API keys built into the app (Last.fm, Google, Dropbox, Discord) can be extracted from any installer, as
with every installed app. They identify the app, not you.
