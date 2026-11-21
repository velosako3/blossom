# blossom progress

## Current position
Milestones 0–1 done (wrap-up: `./gradlew check` green, reviewer gaps fixed, `dev` pushed). Task 2.1 done. Next: Task 2.2 (YouTube Music source).

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

## Deviations from plan
- `ImportedTrack` gained `album`, `sourceKey` and `unplayableReason` (shared interfaces updated in the plan), per the reuse audit.
- **Separate `androidApp` module.** AGP 9 no longer allows `com.android.application` in a Kotlin Multiplatform module. `composeApp` is now a KMP library (shared UI, desktop `Main.kt`), and `androidApp` holds `MainActivity`, the manifest and the APK. Every plan command `:composeApp:assembleDebug` / `assembleRelease` becomes `:androidApp:assembleDebug` / `assembleRelease`. KMP Android unit tests are `androidHostTest` (AGP 9 naming).
- **No remote `main` branch.** `github.com/velosako3/blossom` has no commits, so `dev` starts from an empty history and the `dev → main` PR can't be opened until `main` exists (see open questions). Pushing to `main` is not allowed for this run.

## Open questions for owner
- **Create a `main` branch on GitHub** so the `dev → main` pull request can be opened. The repository had no commits when this run started, and this run may not push to `main`. One way: `git push origin <first commit of dev>:refs/heads/main` (the first commit only adds the plan). Until then, milestone summaries are kept in `docs/milestones.md`.
