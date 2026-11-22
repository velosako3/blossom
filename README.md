# blossom

A free, open-source, liquid-glass music player for Android, Windows and Linux.

It's free for everyone: no ads, no paid tier, no analytics, no blossom account.

<p>
<img src="docs/screenshots/m9/desktop/03_now_playing_lyrics.png" width="560" alt="Now playing with synced lyrics on desktop">
<img src="docs/screenshots/m9/android/07_now_playing.png" width="200" alt="Now playing on Android">
</p>

## What it does

- **Streams YouTube Music** without an account (signing in is optional), plus SoundCloud and untitled.stream links.
- **Plays your own files** (MP3, FLAC, M4A/AAC, Ogg, Opus, WAV) from folders you choose.
- **Imports playlists** from Spotify, YouTube Music, SoundCloud and untitled.stream by pasting or sharing a link, and
  finds a playable match for every song. Signing in to Spotify (Android) also imports private playlists and Liked Songs.
- **Saves music offline** in an encrypted cache, with a storage limit and Wi-Fi-only downloads.
- **Synced lyrics**, word by word where available, full screen on desktop.
- **Listening stats** per week, month and year, and a Wrapped story you can export and share.
- **Sync between your devices** through storage you already own: a folder (Syncthing and the like), WebDAV,
  Google Drive (desktop) or Dropbox. Or pair devices on your network with a QR code or eight words. No blossom server.
- Crossfade and gapless playback, a 10-band equalizer, a sleep timer, Last.fm and ListenBrainz scrobbling, Discord
  status (desktop, off by default), system media controls (Android notification, Windows SMTC, Linux MPRIS).
- Glass you can turn down (Settings → Appearance), Reduce motion, large-text support, full keyboard navigation.

## Install

Installers are built by the [installers workflow](.github/workflows/release.yml) as workflow artifacts:

| Platform | File | Notes |
| --- | --- | --- |
| Android 8+ | `blossom-<version>-android-<abi>.apk` | Pick `arm64-v8a` for most phones, or `universal`. Allow installing from your browser or file manager. |
| Windows 10/11 (x64) | `blossom-<version>-windows-x64.msi` | Or the `-portable.zip`: unzip and run `blossom.exe`. |
| Linux (x86_64) | `blossom-<version>-linux-x86_64.AppImage` | `chmod +x` and run. Or `sudo apt install ./blossom_<version>_amd64.deb`. |

Each artifact has a `SHA256SUMS` file. Builds marked `test-build` are signed with a debug key.

## Building

JDK 21 and the Android SDK (`sdk.dir` in `local.properties`). See [CLAUDE.md](CLAUDE.md) for all commands:

```
./gradlew check                    # every test
./gradlew :composeApp:run          # the desktop app
./gradlew :androidApp:assembleDebug
```

Optional services (Google Drive, Dropbox, Last.fm, Discord) need client IDs in a git-ignored `secrets.properties`;
without them those options are simply hidden. See [docs/RELEASING.md](docs/RELEASING.md).

## Credits

blossom builds on two open-source players:
[BitChord](https://github.com/kushagrasinghx/BitChord) (YouTube Music client, lyrics providers, Spotify reading,
sign-in flows) and [Spotube](https://github.com/KRTirtho/spotube) (the libVLC desktop engine and system media
controls). Thank you. Every library and its license is listed in Settings → About → Open-source licenses, and in
[NOTICE](NOTICE).

## License

[AGPL-3.0-or-later](LICENSE). Files ported from BitChord stay GPL-3.0-only, which the AGPL allows combining with.
