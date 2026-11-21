# blossom progress

## Current position
Task 0.1 done. Next: Task 0.2 (upstream reuse audit).

Toolchain: JDK 21 (Temurin), Gradle 9.9.2 wrapper, Android SDK in `~/Android/Sdk`
(platform 37, build-tools 37.0.0, emulator, system image android-36 google_apis x86_64,
AVD `blossom`). `/dev/kvm` is usable and passwordless sudo works. Display: Wayland (`grim`, `ydotool`).

## Decisions
- Versions: Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.2, Gradle 9.9.2, compileSdk/targetSdk 37, minSdk 26 — latest stable; Spotube v6 ships the same Kotlin/CMP pair, so they are known to work together.
- Material3 1.9.0 and material-icons-extended 1.7.3 for basic text/icons under the custom glass design system — saves hand-drawing every icon.
- `BlossomBuildInfo` is generated from `blossom.version` in `gradle.properties`, so the version lives in one place for the app, the APK and the installers.
- Live tests are any test class named `*LiveTest`; the convention plugin excludes them unless `-PliveTests=true`.
- Convention plugins are precompiled script plugins in `build-logic/` (`blossom.kmp.library`, `blossom.compose`, `blossom.android.app`).

## Deviations from plan
- **Separate `androidApp` module.** AGP 9 no longer allows `com.android.application` in a Kotlin Multiplatform module. `composeApp` is now a KMP library (shared UI, desktop `Main.kt`), and `androidApp` holds `MainActivity`, the manifest and the APK. Every plan command `:composeApp:assembleDebug` / `assembleRelease` becomes `:androidApp:assembleDebug` / `assembleRelease`. KMP Android unit tests are `androidHostTest` (AGP 9 naming).
- **No remote `main` branch.** `github.com/velosako3/blossom` has no commits, so `dev` starts from an empty history and the `dev → main` PR can't be opened until `main` exists (see open questions). Pushing to `main` is not allowed for this run.

## Open questions for owner
