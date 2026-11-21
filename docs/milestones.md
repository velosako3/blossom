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

## Milestone 2: Playback (YouTube Music, player, now playing)
- Core models and source API; YouTube Music source ported from BitChord (Innertube search/browse/radio, InnerTubeX + NewPipe streams, URL probe); player core with expiry recovery; Android Media3 engine + media session; desktop libVLC engine (callback media, bundled imem/libebml/libmatroska) + MPRIS/SMTC; home, search, album, artist, full player, mini player, player bar, queue, desktop shortcuts.
- Evidence: `./gradlew check` → `BUILD SUCCESSFUL` (415 tasks), 99 desktop tests passing.
  - Live YTM: `live search: 20 songs, first = One More Time – Daft Punk`, `live stream: audio/webm; codecs="opus" 157 kbps`.
  - Desktop end to end (off-screen, real audio): `e2e playing: One More Time – Daft Punk at 3881 ms`, `e2e after seek: 120000 ms`, `e2e next: One More Time (Radio Edit) at 2841 ms`.
  - MPRIS: `playerctl -p blossom metadata` → title/artist/length; `position 120`, `next`, `pause` controlled blossom.
  - Android emulator (API 36): media session PLAYING with metadata, MediaStyle notification, media keys and lock-screen pause/next control blossom's queue.
- Screenshots: [home](screenshots/m2/01_home.png), [search](screenshots/m2/02_search.png), [playing](screenshots/m2/03_playing.png), [now playing](screenshots/m2/04_now_playing.png), [Android home](screenshots/m2/android_home.png), [Android playing](screenshots/m2/android_search_playing.png), [Android lock screen](screenshots/m2/android_lock_screen.png), [full player](../feature/nowplaying/src/desktopTest/snapshots/fullPlayer_compact_412x915.png), [player bar](../feature/nowplaying/src/desktopTest/snapshots/playerBar_expanded_1440x900.png), [mini player](../feature/nowplaying/src/desktopTest/snapshots/miniPlayer_compact.png).
- Decisions/deviations: libVLC is fed through vlcj callback media (its own HTTP failed a second connection inside the JVM); `EngineEvent.mediaId`; `PlaybackEngine.onTrackChanged`; `BlossomPlayer.skipTo`; nucleus media-control for MPRIS/SMTC. Reviewer: 5 bugs + 11 risks fixed or ruled (see `docs/progress.md`).

## Milestone 3: Library (operation log, playlists, liked songs, local files)
- Hybrid logical clock, base-62 fractional indexes, typed operations; SQLDelight database with the operation log; `LibraryCommands` (append + project in one transaction) and a convergent `LibraryProjector` (LWW per field, final deletes, removed beats moved, plays union); library, playlist, Liked Songs and Local files screens; desktop folder scanning/watching (jaudiotagger), Android MediaStore + picked folders.
- Evidence: `./gradlew check` → `BUILD SUCCESSFUL`; 137 desktop tests. `ConvergenceTest`: 3 devices × 200 random ops, clocks −2 h/0/+5 min, three delivery orders with duplicates → identical libraries. Off-screen app: `e2e library: liked=[One More Time (Radio Edit)] playlists=[(blossom e2e, 1)]`; `local: [Lossless Silence – Flac Band (LOCAL), Silent Song – Quiet Artist (LOCAL)]`, `local playing: Lossless Silence at 554 ms`.
- Screenshots: [library](screenshots/m3/05_library.png), [playlist](screenshots/m3/06_playlist.png), [local files](screenshots/m3/07_local_files.png), [playlist compact](../feature/library/src/desktopTest/snapshots/playlist_compact.png), [playlist expanded](../feature/library/src/desktopTest/snapshots/playlist_expanded.png), [empty library](../feature/library/src/desktopTest/snapshots/library_empty.png).
- Reviewer: 4 bugs + 6 risks; all fixed except one false alarm (see `docs/progress.md`).
