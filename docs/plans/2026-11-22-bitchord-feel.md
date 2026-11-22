# blossom: BitChord's lyrics, glass and speed

Owner feedback on test build 1 (2026-11-22), phone:

1. Switching glass options changes nothing; the glass isn't "liquid" enough.
2. Lyrics: most are slow, often a plain Genius page, no animation or word tracking, some are the wrong song.
3. Switching songs and loading songs is slow; RAM use is high; animations aren't smooth.

Direction from the owner: take BitChord's liquid glass, lyrics and lyric animations as the base, or anything better.
Reference: `../_upstream/BitChord` at `21356206` (kushagrasinghx/BitChord, GPL-3.0). Its `shared` and `sharedUi`
`jvmSharedMain` code is Compose Multiplatform, so it ports to Android and desktop alike.

## Causes found

- **Glass options look the same.** `GlassSurface` tints FULL/REDUCED at 55–65 % plus a readability floor, OFF at 88 %:
  the blurred backdrop barely shows, so the three settings look alike. BitChord's glass is Kyant0 `backdrop`
  (lens refraction + blur + highlight; Apache-2.0; 2.0.1 publishes Android and desktop), not Haze glass.
- **Lyrics slow / Genius / wrong song.** `LyricsResolver` waits for every provider (5 s timeout each) and takes the
  richest; on a phone network the synced sources time out, Genius answers, and that plain result is cached forever.
  It passes only the first artist, never identifies the recording (ISRC), and drops background vocals, duet sides
  and syllable data in the adapter. BitChord's `LyricsRepository` races all sources but takes answers in priority
  order (first timed answer wins at once), asks Genius only when nothing timed exists, and settles the ISRC first.
- **Lyrics animation.** blossom's view is ~400 lines; BitChord's (`PlayerLyrics`, `LyricClock`, `LyricFocus`) is ~3,800
  with a frame-interpolated clock, per-syllable growth and spring-scrolled focus.

## Tasks

- [x] **1. Lyrics engine.** Vendor the latest BitChord `data/lyrics` (incl. `LyricsRepository`, `LyricsSource`);
  `LyricsResolver` delegates to it with the full artist string and the video id; keep BitChord lines end to end
  (source + `List<LyricLine>`). Cache: timed results kept, plain ones retried after a day, old cache rows ignored.
  Tests: ordered pick (timed beats a faster plain; Genius never asked when timed exists), cache rules.
  Done: the resolver now runs BitChord's `LyricsRepository` (full artist list, ISRC first, Genius last), lyrics HTTP
  timeouts raised from 5 s to BitChord-like values, YouTube Music timed lyrics via the mobile client, cache keyed `v2:`
  so the old resolver's rows are ignored. Live (desktop): 6/6 test songs word-synced in 0.8–3.2 s. Still converted to
  blossom's old `Lyrics` model for the current view; task 2 keeps BitChord lines end to end.
- [x] **2. Lyrics view.** Port `PlayerLyrics`, `LyricClock`, `LyricFocus`, `SyncedLyricsPanel` and their helpers;
  feed them blossom's player position. Snapshot + clock tests.
  Done: BitChord's panel (translation, romanization and the one-line strip left out) on BitChord's lines end to end,
  cache stores the full lines (`v3:`). The player's position moved out of `PlaybackState` into `BlossomPlayer.progress`
  (BitChord's `PlaybackPosition` reasoning): a tick no longer recomposes everything that watches the player.
- [x] **3. Liquid glass.** Kyant0 `backdrop` 2.0.1 for glass surfaces (FULL = lens + blur + highlight, REDUCED = blur,
  OFF = tint), lighter tint, BitChord's floating tab bar look; now-playing backdrop from `ArtworkMeshBackdrop`.
  Snapshot test proving the three settings render differently.
  Done: Kyant0 `backdrop` 2.0.1 replaces Haze glass. Two recorded layers: the background (in-page glass) and the
  page (floating chrome: nav bar, mini player, sidebar, player bar, combined with the background). Pages run under
  the floating bars; screens end their lists `LocalBarsPadding` up. Tint 28 % / 42 % (FULL), 45 % / 55 % (REDUCED),
  readability floor capped. `GlassQualitiesDifferTest`: full↔reduced 22, reduced↔off 39, full↔off 56 mean per channel.
  Still open: BitChord's lens selection pill in the tab bar; now-playing `ArtworkMeshBackdrop`.
- [ ] **4. Speed and memory.** Measure first (track start, track switch, lyrics time, RSS) on desktop and the
  emulator; compare with BitChord's stream pipeline; fix what the numbers show.
- [ ] **5. Smoothness.** Frame-time pass over the lyric view, now playing and lists; Android baseline profile.
- [ ] **6. Wrap-up.** `check verifyRoborazziDesktop`, review, push, new test build for the owner.
