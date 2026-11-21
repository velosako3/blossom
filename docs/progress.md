# blossom progress

## Current position
Milestones 0–1 done (wrap-up: `./gradlew check` green, reviewer gaps fixed, `dev` pushed). Tasks 2.1–2.3 done; 2.4 done except the emulator lock-screen check (needs the play UI from 2.6). Next: Task 2.5 (desktop engine).

Toolchain: JDK 21 (Temurin), Gradle 9.9.2 wrapper, Android SDK in `~/Android/Sdk`
(platform 37, build-tools 37.0.0, emulator, system image android-36 google_apis x86_64,
AVD `blossom`). `/dev/kvm` is usable and passwordless sudo works. Display: Wayland (`grim`, `ydotool`).

## Decisions
- Versions: Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.2, Gradle 9.9.2, compileSdk/targetSdk 37, minSdk 26 — latest stable; Spotube v6 ships the same Kotlin/CMP pair, so they are known to work together.
- Material3 1.9.0 and material-icons-extended 1.7.3 for basic text/icons under the custom glass design system — saves hand-drawing every icon.
- `BlossomBuildInfo` is generated from `blossom.version` in `gradle.properties`, so the version lives in one place for the app, the APK and the installers.
- Live tests are any test class named `*LiveTest`; the convention plugin excludes them unless `-PliveTests=true`.
- Convention plugins are precompiled script plugins in `build-logic/` (`blossom.kmp.library`, `blossom.compose`, `blossom.android.app`).

- Desktop audio engine: libVLC via vlcj, Spotube v6's engine (see `docs/research/reuse-map.md`). VLC can't send arbitrary HTTP headers, so remote streams go through blossom's loopback `LocalStreamServer`, which forwards `StreamInfo.headers`.
- System media controls: nucleus media-control (MIT, what Spotube v6 uses) for both Linux MPRIS (`org.mpris.MediaPlayer2.blossom`) and Windows SMTC, instead of hand-written D-Bus/WinRT code.
- YouTube streams: InnerTubeX first, NewPipe Extractor fallback, URL probed before use — BitChord's pipeline. Desktop runs without a PoToken minter (BitChord's JVM target does the same).
- Ported BitChord files use `SPDX-License-Identifier: GPL-3.0-only` (its LICENSE has no "or later" grant), inside the AGPL-3.0-or-later whole (GPLv3 §13).
- Stats/Wrapped written new: BitChord's stats code is Android-only and its rules differ from the spec.

- untitled.stream gate: **(a) works without sign-in.** Sample links 1 and 2 load metadata, sign audio URLs and stream plain MP3 with no account and no DRM (see `docs/research/untitled.md`). The sign-in flow of Task 6.5 is therefore not built; private links (`302 → /login`) show "This untitled link is private or was removed".

- Glass rendering: FULL uses Haze 2.1's `haze-glass` (refraction shader: AGSL on Android 13+, SkSL on desktop) tuned to the spec (24 dp blur, 6 dp edge band, small chromatic fringe); REDUCED uses `haze-blur` (24 dp, no refraction); OFF is an 88% tint. blossom draws the rim, grain and shadow itself. No hand-written shader actuals are needed, so `GlassShaders.kt` holds the shared grain tile and rim brush instead.
- Glass tint doubles as the text scrim: its alpha is `max(spec alpha, requiredScrimAlpha(onSurface, palette.dominant, tint))`, so text on glass always meets 4.5:1 against the artwork colour behind it.
- `GlassSheet` and `GlassDialog` render as in-window overlays (not separate platform windows), so their glass samples the real backdrop; Esc and scrim taps dismiss them.
- Snapshot tests force a glass quality, which also disables the frame-budget monitor; its endless frame loop would otherwise keep the test clock busy.

- Navigation is a plain observable back stack (`Navigator`), not a navigation library: blossom's hierarchy is flat (5 top-level destinations, detail screens pushed on top).
- Desktop window capture for evidence uses `xwd -id <window>` (installed `x11-apps/xwd`, `x11-misc/xdotool`), so screenshots contain only blossom's window, never the rest of the owner's screen.

- Milestone 1 review: fixed the reviewer's findings (Reduce motion now drives every animation through `motionSpec`/`blossomSpring`, `ThemeMode` System/Light/Dark resolves in `App`, Android follows the system "Remove animations" switch until the in-app setting exists). The Settings toggles for theme, Reduce motion and "Use system title bar" are built with the settings screen in Task 9.7; until then the values are parameters with the spec's defaults (dark, motion on, custom title bar).

- Kotlin compiles `commonMain` against the JDK when the only targets are Android and desktop JVM, so JVM-only libraries (OkHttp, NewPipe, InnerTubeX's JVM artifact) live in `commonMain` and BitChord's `jvmSharedMain` code ports without an extra source set.
- InnerTubeX v0.12.0 (newest tag; same Kotlin 2.4.21/Ktor 3.6 as blossom) instead of BitChord's pinned v0.7.0; NewPipe Extractor v0.27.1. Both from JitPack, limited by a repository content filter.
- `YouTubeSource` (plain YouTube uploads) searches through NewPipe's YouTube search extractor and plays through the same `YouTubeStreams` pipeline.
- `selectAudioFormat` picks among NewPipe's progressive formats; InnerTubeX does its own selection from the quality hint (LOW/AUTO/HIGH).
- Desktop `SecureStore` uses java-keyring (Windows Credential Manager / Linux Secret Service) with a 0600 properties fallback; Android encrypts with a Keystore AES-GCM key.
- YouTube Music sign-in: `YtmSource` reads the cookie from `SecureStore` (`ytm.cookie`) like BitChord; the web-view login screen that captures it is built with the settings screens (Task 9.7).
- Live test (Task 2.2): `./gradlew :source:youtubemusic:desktopTest -PliveTests=true --tests "*YtmLiveTest*"` → `live search: 20 songs, first = One More Time – Daft Punk`, `live stream: audio/webm; codecs="opus" 157 kbps, expires 2026-11-21T22:43:42Z`.

- Player: shuffle reorders the visible queue (current track first, deterministic per seed) and remembers the original order to restore it; the Next button moves on even with repeat ONE (only natural track ends repeat). Non-HTTP engine errors use the same two re-resolves before skipping.
- Desktop `NetworkMonitor` checks for an up interface plus a DNS answer every 15 s (no request to any server); `-Dblossom.forceOffline=true` pins it offline for testing.

## Deviations from plan
- `AudioFocusPolicy` is a small class, not an `object`: "GAIN resumes only after a transient loss" needs to remember the previous loss.
- `PlaybackEngine` gained a default no-op `onTrackChanged(track)` so the engines can publish now-playing metadata to the notification/lock screen (Android) and MPRIS/SMTC (desktop); `DefaultBlossomPlayer` calls it before every load.
- Android audio focus is requested by `BlossomPlaybackService` through `AudioFocusPolicy` (ExoPlayer's own focus handling is off) so focus changes and "headphones unplugged" go through blossom's player state; the session's next/previous/play/pause are forwarded to blossom's queue.
- `ImportedTrack` gained `album`, `sourceKey` and `unplayableReason` (shared interfaces updated in the plan), per the reuse audit.
- **Separate `androidApp` module.** AGP 9 no longer allows `com.android.application` in a Kotlin Multiplatform module. `composeApp` is now a KMP library (shared UI, desktop `Main.kt`), and `androidApp` holds `MainActivity`, the manifest and the APK. Every plan command `:composeApp:assembleDebug` / `assembleRelease` becomes `:androidApp:assembleDebug` / `assembleRelease`. KMP Android unit tests are `androidHostTest` (AGP 9 naming).
- **No remote `main` branch.** `github.com/velosako3/blossom` has no commits, so `dev` starts from an empty history and the `dev → main` PR can't be opened until `main` exists (see open questions). Pushing to `main` is not allowed for this run.

## Open questions for owner
- **Create a `main` branch on GitHub** so the `dev → main` pull request can be opened. The repository had no commits when this run started, and this run may not push to `main`. One way: `git push origin <first commit of dev>:refs/heads/main` (the first commit only adds the plan). Until then, milestone summaries are kept in `docs/milestones.md`.
