# blossom progress

## Current position
Milestone 0 done. Tasks 1.1–1.3 done. Next: Task 1.4 (GlassSurface + components).

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

## Deviations from plan
- `ImportedTrack` gained `album`, `sourceKey` and `unplayableReason` (shared interfaces updated in the plan), per the reuse audit.
- **Separate `androidApp` module.** AGP 9 no longer allows `com.android.application` in a Kotlin Multiplatform module. `composeApp` is now a KMP library (shared UI, desktop `Main.kt`), and `androidApp` holds `MainActivity`, the manifest and the APK. Every plan command `:composeApp:assembleDebug` / `assembleRelease` becomes `:androidApp:assembleDebug` / `assembleRelease`. KMP Android unit tests are `androidHostTest` (AGP 9 naming).
- **No remote `main` branch.** `github.com/velosako3/blossom` has no commits, so `dev` starts from an empty history and the `dev → main` PR can't be opened until `main` exists (see open questions). Pushing to `main` is not allowed for this run.

## Open questions for owner
