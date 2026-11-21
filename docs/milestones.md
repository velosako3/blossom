# Milestone summaries

These are the PR comments for the `dev → main` pull request ("blossom 1.0"). The PR can't be opened yet because the
repository has no `main` branch (see `docs/progress.md` → Open questions); paste them once it exists.

## Milestone 0: Foundation and research
- KMP project (Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.2, Gradle 9.9.2), convention plugins, CI (`./gradlew check`).
- AGP 9 requires a separate `androidApp` module; `composeApp` is a KMP library.
- Reuse map: `docs/research/reuse-map.md` (BitChord v1.9.1 Innertube/lyrics/Spotify/scrobbling; Spotube v6.0.0 vlcj engine + media controls).
- untitled.stream: gate (a), streams without sign-in, plain MP3, no DRM (`docs/research/untitled.md`).

## Milestone 1: Liquid-glass design system and app shell
- Design tokens, Inter, WCAG contrast utilities; artwork palette extraction; glass quality levels with frame-budget downgrade.
- `GlassSurface` (Haze glass refraction at FULL, blur at REDUCED, tint at OFF) with rim, grain, shadow; ten glass components; animated `BlossomBackground`.
- Adaptive shell (bottom bar / rail / sidebar + player bar + Queue/Lyrics panel), undecorated desktop window with glass title bar and persisted window state.
- Evidence: `./gradlew check` → `BUILD SUCCESSFUL in 30s` (120 tasks). designsystem 25 tests, composeApp 10 tests, all passing.
- Screenshots: [glass FULL dark](../core/designsystem/src/desktopTest/snapshots/glassSurface_full_dark.png),
  [REDUCED](../core/designsystem/src/desktopTest/snapshots/glassSurface_reduced_dark.png),
  [OFF](../core/designsystem/src/desktopTest/snapshots/glassSurface_off_dark.png),
  [FULL light](../core/designsystem/src/desktopTest/snapshots/glassSurface_full_light.png),
  [components](../core/designsystem/src/desktopTest/snapshots/components_gallery_dark.png),
  [compact shell](../composeApp/src/desktopTest/snapshots/shell_compact_412x915.png),
  [expanded shell](../composeApp/src/desktopTest/snapshots/shell_expanded_1440x900.png),
  [running desktop app](screenshots/m1/desktop_home.png), [Library + Lyrics panel](screenshots/m1/desktop_library_lyrics_panel.png).
- Reviewer subagent: 1 bug (Reduce motion never on in the running app) and 6 risks (animations ignoring Reduce motion, theme mode not wired, no title-bar setting UI). All fixed except the settings UI, which is part of Task 9.7.
