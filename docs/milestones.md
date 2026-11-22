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

## Milestone 4: Lyrics
- LRC and enhanced-LRC parser; BitChord's 13 lyrics providers (vendored, GPL-3.0-only headers) raced with 5 s timeouts, richest result wins; SQL cache (7-day "none found" only when every provider answered); sidecar `.lrc` and embedded lyrics for desktop local files. Animated `LyricsView`: word sweep with soft edge, glow on the sung word, active-line scale, depth blur, cascading spring scroll, instrumental dots, tap-to-seek, "Back to current" after manual scroll, reduce-motion variant. Wired into the full player, the right-panel Lyrics tab and desktop full-screen lyrics.
- Evidence: `./gradlew check` → `BUILD SUCCESSFUL`; 171 desktop tests. Live providers for "Kanye West – Runaway": lrc.red, BiniLyrics, BetterLyrics word-synced (87 lines); KuGou, LRCLIB line-synced; Genius plain. Real app off-screen: `lyrics: WordSynced from lrc.red`. Render cost (off-screen CPU raster, 1440×900, p90): reduce motion 9.1 ms, full motion at REDUCED glass 11.2 ms, at FULL glass 78.9 ms. That's why blur is FULL-only, so the frame monitor's first step down removes it. GPU numbers need an on-screen run (open question).
- Screenshots: [lyrics panel in the app](screenshots/m4/08_lyrics_panel.png), [word-synced mid-word](../feature/lyrics/src/desktopTest/snapshots/lyrics_wordSynced_midWord.png), [line-synced](../feature/lyrics/src/desktopTest/snapshots/lyrics_lineSynced.png), [instrumental gap](../feature/lyrics/src/desktopTest/snapshots/lyrics_instrumentalGap.png), [reduce motion](../feature/lyrics/src/desktopTest/snapshots/lyrics_reduceMotion.png), [no lyrics](../feature/lyrics/src/desktopTest/snapshots/lyrics_none.png).
- Reviewer: 2 bugs + 25 risks + 3 questions. Fixed: both bugs and 17 risks. Rulings: 6 (see `docs/progress.md`). The rest were questions answered there.

## Milestone 5: Offline saving (encrypted cache)
- AES-256-GCM in 64 KiB chunks (format v2: 8-byte random nonce prefix + 4-byte chunk index, index and last-chunk flag in the associated data, so truncation and reordering fail), key kept by the OS keyring or the Android Keystore. Downloads encrypt as bytes arrive (no plaintext on disk), in 2 MiB ranges, 3 at once, resumable, with Wi-Fi-only and storage-limit pauses. Reference-counted saves (songs, playlists that keep saving new entries). Playback straight from the encrypted file: vlcj callback media on desktop, a Media3 DataSource on Android. Offline UX: banner, offline home, queue skipping, offline search, saved badges, Library "Saved" chip, Storage screen.
- Evidence: `./gradlew check` and `verifyRoborazziDesktop` → `BUILD SUCCESSFUL`. Off-screen real app with the network forced offline inside the JVM: `offline saved: One More Time – Daft Punk, 5316281 bytes`, `offline playing: One More Time at 3581 ms, online=false`.
- Screenshots: [offline home](screenshots/m5/09_offline_home.png), [Library → Saved](screenshots/m5/10_offline_library_saved.png), [Storage](screenshots/m5/11_storage.png).
- Reviewer: 2 bugs + 24 risks. Fixed: both bugs and 22 risks. Rulings: 2 (see `docs/progress.md`).

## Milestone 6: Imports (Spotify, YouTube Music, SoundCloud, untitled.stream)
- Link router (tracking parameters stripped, international paths, short links), track matcher (title/artist/duration scoring with feat./remaster handling), resumable import jobs (persisted items, batched atomic writes, retry-and-resume on network loss, Refresh from source). Spotify without sign-in (embed + anonymous GraphQL paging); YouTube Music/YouTube natively; SoundCloud source and importer (public API, Go+ previews matched instead, HLS playback on desktop and Android); untitled.stream direct streaming with project-scoped IDs, dead-link rechecks and Find replacement. Import screen with live ✓ / ? / ✗ list, Fix this match, duplicate handling, Android share target and deep links.
- Evidence: `./gradlew check` and `verifyRoborazziDesktop` → `BUILD SUCCESSFUL`. Live: Spotify 'All Out 80s' 150 tracks without sign-in; SoundCloud set 79 tracks, HLS stream plays and seeks on desktop; untitled sample link 1 → 15 tracks, sample link 2 → 4 tracks, a track from each playing (links never recorded); a public Spotify album through the real Import screen: 10 tracks, 6 matched, 4 to check. 5,000 native tracks import in 2.93 s.
- Screenshots: [import done](screenshots/m6/12_import_done.png), [import in progress](../feature/importer/src/desktopTest/snapshots/import_inProgress.png), [fix a match](../feature/importer/src/desktopTest/snapshots/import_matchFix.png).
- Reviewer: 3 bugs + 28 risks + 6 questions. Fixed: all 3 bugs and 25 risks. Rulings: 6 (see `docs/progress.md`).

## Milestone 7: Stats and Wrapped
- Play tracking with the 30-second rule (clock-based listening time, 15 s crash checkpoints), ISO-week/month/year periods in the listener's time zone (DST and week 53 handled), stats calculator (top songs/artists/albums, listening clock, weekdays, new artists, streaks, change vs the previous period), Stats screen, and Wrapped: a 12-card story tuned to the top song's artwork, with the top song playing from 30 %, 1080×1920 export and sharing, and Monday / 1st / 1 January banners.
- Evidence: `./gradlew check` and `verifyRoborazziDesktop` → `BUILD SUCCESSFUL`. Real app off-screen: `top song 'Around the World' playing at 129000 ms of 430000 (30%)`, then the previous song restored at 10 s, paused.
- Screenshots: [Wrapped in the app](screenshots/m7/13_wrapped.png), [every card](screenshots/m7/wrapped_cards.png), [Stats](../feature/stats/src/desktopTest/snapshots/stats_week_full.png), [empty week](../feature/stats/src/desktopTest/snapshots/stats_empty.png).
- Reviewer: 2 bugs + 14 risks + 1 question. Fixed: both bugs and 13 risks. Rulings: 2 (see `docs/progress.md`).

## Milestone 8: Sync
- Per-device op logs (JSONL, `"v":1`, 5 MB segments plus a manifest) through four backends: a folder (desktop path / Android SAF tree), WebDAV, Google Drive (desktop; app-data folder) and Dropbox. Sign-in uses OAuth PKCE (desktop loopback, Android `db-<key>` redirect) and only happens when you choose a service. Android syncs hourly through WorkManager. LAN pairing: a QR code or eight words, an ECDH key exchange authenticated by the one-time secret (the client proves first), and AES-GCM sessions. Paired devices gossip each other's changes and find each other again over mDNS. Settings → Sync screen and the pairing screen.
- Evidence: `./gradlew check` and `verifyRoborazziDesktop` → `BUILD SUCCESSFUL`. `TwoInstanceSyncTest` runs two instances with separate data directories and DB files: `folder: both have 5 likes` → `lan: paired [Laptop] / [Desk]; both have 7 likes`; after "Remove device" the removed peer no longer syncs.
- Not checked live: Google Drive and Dropbox. They need someone to sign in to an account (Open questions).
- Screenshots: [Settings → Sync](screenshots/m8/sync_settings.png), [pairing](screenshots/m8/sync_pair.png).
- Reviewer: 2 critical, 4 important, 6 minor. Fixed:
  - both criticals: offline guessing of the pairing secret plus try and window bypasses; ops lost after a read error
  - all 4 important: LAN DoS limits, sign-in only on choose with timeouts handled, sync work off the UI thread, camera released
  - 2 minors: a blank WebDAV password wiped the stored one; peers could write this device's own history
  - The other minors are deferred (see `docs/progress.md`).

## Milestone 9: Extras, polish, accessibility and release
- Features:
  - crossfade (a second engine, equal-power curve) and gapless
  - a 10-band equalizer with presets on both platforms
  - a sleep timer with a fade
  - Last.fm and ListenBrainz scrobbling with an offline queue
  - Discord status on desktop
  - a GitHub update check
  - onboarding, and the full Settings page: appearance with glass effects, playback, lyrics, storage, sync, import, accounts, integrations, about with the open-source license list
  - account sign-in: Android web views; desktop pastes a YouTube Music cookie
  - accessibility: an audit, keyboard order and 200 % text
  - installers on CI
- Evidence:
  - `./gradlew check` and `verifyRoborazziDesktop` → `BUILD SUCCESSFUL`.
  - Crossfade live on desktop: switched at 5.15 s with a 3 s fade on 8 s files.
  - The Last.fm key's signed call was accepted.
  - The installers workflow succeeded for android, windows and linux; every artifact matched its SHA256SUMS.
  - Release APK on the emulator: cold start into Home, and the new launcher icon.
- Screenshots: desktop, every main screen (`docs/screenshots/m9/desktop/`); Android, every main screen (`docs/screenshots/m9/android/`); 200 % text, compact and expanded (`composeApp/src/desktopTest/snapshots/a11y/`).
- Found in the hands-on pass and fixed:
  - the sleep timer dialog was drawn under the screens
  - content ended under the mini player on Android
  - an Android cold-start ANR with full glass on a weak GPU (the frame monitor now reacts to a single catastrophic frame and reads GPU frame metrics; glass is off on the emulator; new Glass effects setting)
- Final reviewer: 1 critical, 3 important and 10 minor findings. All of the critical and important ones and 7 minors are fixed with tests; 3 minors are deferred (see `docs/progress.md`).

### Spec goals → implementation → tests
| Goal | Implemented | Tested by |
| --- | --- | --- |
| Android, Windows, Linux | composeApp shells, androidApp, `release.yml` | `ShellSnapshotTest`, `WindowLayoutTest`, installers CI |
| Liquid glass | `GlassSurface`, `BlossomBackground`, `FrameBudgetMonitor` | `GlassSnapshotTest`, `GlassQualityResolveTest`, `ContrastTest`, `FrameBudgetMonitorTest` |
| YouTube Music as a guest | `YtmSource`, `DefaultBlossomPlayer` | `YtmParsingTest`, `StreamSelectionTest`, `DefaultBlossomPlayerTest`, `DesktopEndToEndLiveTest` |
| Playlists and library | `LibraryCommands`, `LibraryProjector` | `LibraryCommandsTest`, `ConvergenceTest`, `PlaylistViewModelTest` |
| Import (Spotify, YouTube Music, SoundCloud, untitled) | `ImportRouter`, `TrackMatcher`, `ImportJobRunner` | `ImportJobRunnerTest` (5,000 tracks, resume), `TrackMatcherTest`, importer tests, `DesktopImportLiveTest` |
| Encrypted offline | `ChunkedCipher`, `DownloadManager`, `SqlOfflineStore` | `ChunkedCipherTest`, `DownloadManagerTest`, `OfflineStoreTest`, `DesktopOfflineLiveTest` |
| Stats and Wrapped | `StatsCalculator`, `Period`, Wrapped | `StatsCalculatorTest`, `PeriodTest`, `PeriodLabelTest`, `WrappedDeckBuilderTest`, `DesktopWrappedLiveTest` |
| Lyrics | `LyricsResolver`, `LyricsView` | `LyricsResolverTest`, `LrcParserTest`, `LyricsUiTest`, `DesktopLyricsLiveTest` |
| Sync (cloud and LAN) | `SyncEngine`, backends, `PeerSync` | `SyncEngineTest`, `PeerSyncTest`, `TwoInstanceSyncTest`, `SettingsSyncTest` |
| Extras | `CrossfadeController`, EQ, `SleepTimer`, scrobbling, Discord, `UpdateChecker` | `CrossfadeControllerTest`, `EqBandMapperTest`, `SleepTimerTest`, `ScrobbleQueueTest`, `PresenceBuilderTest`, `SemVerTest` |
| Accessibility | labels, focus order, large text | `AccessibilityAuditTest`, `KeyboardNavigationTest`, `AccessibilitySnapshotTest` |

### For the dev → main pull request (when `main` exists)
- **Before sharing any installer, make the repository public (AGPL).**
- The owner's to-do list is "Open questions for owner" in `docs/progress.md`.

