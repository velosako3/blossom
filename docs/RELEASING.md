# Releasing blossom

Installers are built by `.github/workflows/release.yml` and uploaded as **workflow artifacts**: there are no tags and
no GitHub releases yet.

- **Manual runs** (Actions → installers → Run workflow) always build.
- **Pushes to `dev`** build only when the commit message contains `[build]`.

Each run produces three artifacts, each with a `SHA256SUMS` file:

- `android`: APKs per ABI and a universal one.
- `windows`: an MSI and a portable zip.
- `linux`: an AppImage and a `.deb`.

> **Before sharing any installer:** blossom is AGPL-3.0. Anyone you give it to is entitled to the source, so make the
> repository public first (Settings → General → Danger zone → Change visibility).

## Android signing

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

## Turning builds into GitHub releases later

When you want public releases:

1. Make the repository public (AGPL).
2. Add a `release` job to `release.yml` that runs on tags (`on: push: tags: ['v*']`). It needs the three build jobs
   and `permissions: contents: write`, downloads their artifacts (`actions/download-artifact@v4`) and publishes them
   with `gh release create "$GITHUB_REF_NAME" out/* --generate-notes`.
3. Tag a commit: `git tag v1.0.0 && git push origin v1.0.0`.

The app's update check already reads `releases/latest`, so it shows the "Update" chip once a release exists.
