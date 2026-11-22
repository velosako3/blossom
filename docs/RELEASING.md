# Releasing blossom

Installers are built by `.github/workflows/release.yml` and uploaded as **workflow artifacts**. Pushing a tag like
`v0.1.0` also publishes them as a GitHub release.

- **Manual runs** (Actions → installers → Run workflow) always build.
- **Pushes to `dev`** build only when the commit message contains `[build]`.

Each run produces three artifacts, each with a `SHA256SUMS` file:

- `android`: APKs per ABI and a universal one.
- `windows`: an MSI and a portable zip.
- `linux`: an AppImage and a `.deb`.

> **Before sharing any installer:** blossom is AGPL-3.0. Anyone you give it to is entitled to the source, so make the
> repository public first (Settings → General → Danger zone → Change visibility).

## Android signing

A release key is set up: the keystore and its password are in a `blossom-signing` folder in the owner's home folder
(outside the repo, never committed), and the four secrets below are set on GitHub. Back that folder up; GitHub
secrets can't be read back.

Without a keystore, the APKs are signed with the debug key and named `...-test-build.apk`. Such builds can't be
updated by a properly signed build later (Android requires the same key), so set up a real key once:

1. Create a keystore and keep a copy somewhere safe. If you lose it, you can't ship updates to existing installs.

   ```
   keytool -genkeypair -v -keystore blossom-release.jks -alias blossom -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add four repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | `base64 -w0 blossom-release.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | the store password |
   | `ANDROID_KEY_ALIAS` | `blossom` |
   | `ANDROID_KEY_PASSWORD` | the key password |

The next run signs with it. Locally, set the same variables, plus `ANDROID_KEYSTORE_FILE` pointing at the `.jks`, before
running `./gradlew :androidApp:assembleRelease`.

## Optional service IDs

These come from `secrets.properties` locally, or from repository secrets of the same names on CI. A missing one hides
that option in the app:

- `GOOGLE_OAUTH_DESKTOP_CLIENT_ID`, `GOOGLE_OAUTH_DESKTOP_CLIENT_SECRET`: Google Drive sync on desktop.
- `GOOGLE_OAUTH_ANDROID_CLIENT_ID`: Google Drive on Android (an Android OAuth client for package `io.github.velosako3.blossom` and the release key's SHA-1).
- `DROPBOX_APP_KEY`: Dropbox sync. The redirect `http://127.0.0.1:53682/dropbox` must be registered.
- `LASTFM_API_KEY`, `LASTFM_API_SECRET`: Last.fm scrobbling.
- `DISCORD_APPLICATION_ID`: Discord status on desktop.

## Version

`blossom.version` in `gradle.properties` is the single source: the app name, `versionName`/`versionCode`, the
installer file names and the update check all read it.

## Publishing a GitHub release

The `release` job in `release.yml` runs on tags `v*`: it waits for the three builds, then creates the release with all
installers and their checksum files (`SHA256SUMS-android.txt` and so on) and generated notes.

1. Make the repository public first (AGPL). The app's update check reads `releases/latest` without signing in, so it
   only sees releases of a public repository.
2. Set `blossom.version` in `gradle.properties`, commit, and push.
3. Tag that commit: `git tag v0.1.0 && git push origin v0.1.0`.
