# Upstream reuse map

Audited 2026-11-21, read-only clones in `../_upstream/` (outside the repo).

| Upstream | Ref | Commit | License |
|---|---|---|---|
| `kushagrasinghx/BitChord` | tag `v1.9.1` | `7f45c768a461fd7e68bf4613f71e8a767e643d4c` | GPL-3.0 |
| `KRTirtho/spotube` | tag `v6.0.0` | `8925b1b9351c3af2fd672db898d8d899badc1cb9` | AGPL-3.0-or-later |
| `ayhamo/BitChord` (fork, hints only) | `main` | shallow clone, nothing copied | GPL-3.0 |

## What BitChord looks like at v1.9.1

- Modules: `app` (Android), `shared` and `sharedUi` (Kotlin Multiplatform with an `android` and a `jvm` target joined
  by a hand-made `jvmSharedMain` source set), `tv`, `wearos`, `native` (C++ audio analysis), `backend` (Go, listen-together), `web`.
- Almost everything blossom needs already lives in `shared/src/jvmSharedMain`, so it is plain JVM Kotlin with no
  Android APIs. blossom's two targets are both JVM (Android + desktop), so this code ports into `commonMain` of a
  module whose targets are only `android` and `jvm("desktop")`.
- No DI library (Kotlin `object` singletons), no Room/SQLDelight (SharedPreferences + files), Coil 3, Haze 1.3.1,
  Media3 1.11.0, Ktor 3 (OkHttp engine), kotlinx.serialization, jsoup.
- Files carry no copyright header. Ported files get a header naming the upstream path, `// SPDX-License-Identifier: GPL-3.0-only`
  (BitChord's LICENSE is GPL-3.0 without an "or later" clause), and `// Modified for blossom, 2026.` GPLv3 §13 allows combining
  them with the AGPL-3.0-or-later whole.

### Innertube (YouTube Music)
- `shared/.../data/innertube/Innertube.kt` (1,287 lines): its own Ktor client for `youtubei/v1` search, browse, next, player,
  with WEB_REMIX context, visitor data, optional cookie/SAPISID auth (`auth/` package normalises data-sync IDs).
- `InnertubeParser.kt` (1,839 lines): JSON → models for search shelves, home sections, album, artist, playlist, queue/radio.
- `StreamResolver.kt` (1,236 lines): **InnerTubeX first** (`com.github.MetrolistGroup.innertubex:innertubex`, JitPack, GPL-3.0),
  which owns client selection, cipher/n-param deobfuscation (hosted QuackJS, pure Kotlin) and PoToken contracts;
  **NewPipeExtractor as the fallback** (`com.github.TeamNewPipe:NewPipeExtractor`, JitPack, GPL-3.0); every URL is
  **probed** (a ranged GET) before it reaches the player. Quality = bitrate ceiling, `mediaHeadersFor(url)` gives the
  headers a media fetch must send.
- `InnerTubeXResolver.kt` (408 lines): wraps `InnerTubeExtractor`, `YouTubeCipherService`, `RemotePlayerConfigStore`.
- PoTokens: `potoken/` drives a BotGuard page. Only Android implements the minter (`app/.../PoTokenWebView.kt`, WebView).
  On desktop InnerTubeX runs without a token provider and NewPipe covers the rest — that is also what BitChord's own
  JVM target does.
- Dependencies that are Android-only: none in `shared`. `java.util`/`okhttp3` are fine on both blossom targets.

### Lyrics
- Providers, in BitChord's default priority (`LyricsSource` declaration order; hidden ones skipped):
  1. lrc.red (word) 2. BiniLyrics (word) 3. BetterLyrics (word) 4. BetterLyrics Portato (word) 5. PaxSenix (word)
  6. PaxSenix: Spotify (line, needs key → skipped) 7. PaxSenix: Musixmatch (needs key → skipped) 8. LyricsPlus (word)
  9. ~~SimpMusic~~ (hidden) 10. Unison (word) 11. YouTube captions (line) 12. YouTube Music (plain) 13. ~~Megalobiz~~ (hidden)
  14. KuGou (line) 15. LRCLIB (line) 16. Musixmatch (line) 17. Genius (plain)
- Formats parsed: LRC, enhanced LRC (`EnhancedLrc.kt`, `<mm:ss.xx>` word stamps), TTML (`TtmlLyrics.kt`, Apple syllable
  timing), syllable runs, background vocals. Embedded lyrics tags: `app/.../lyrics/EmbeddedLyrics.kt` (Android module).
- Shared HTTP helper `LyricsHttp.kt`, query model `LyricsQuery.kt`, orchestration `LyricsRepository.kt` (sequential by
  priority, keeps going for a word-synced result).

### Spotify
- `shared/.../data/spotify/SpotifyImporter.kt`: **public playlists without login** — reads `open.spotify.com/embed/playlist/<id>`,
  takes the anonymous `accessToken` the embed page carries, then pages the rest through the web player's GraphQL
  (`api-partner.spotify.com/pathfinder/v1/query`) because the embed stops at 100 tracks.
- `SpotifyLibrary.kt`: signed-in playlists and Liked Songs through `pathfinder/v2/query` with persisted-query hashes.
  Login itself is in the Android `app` (web login → `sp_dc` cookie → token).

### Scrobbling, presence, stats
- Last.fm: `app/.../data/scrobbling/LastFM.kt` (Ktor, signed `api_sig` calls), ListenBrainz:
  `ListenBrainzManager.kt` (token header). Pure Kotlin + Ktor, portable to `commonMain`.
- Discord presence: `shared/.../com/my/kizzy/*` is a Discord **gateway** (user-token) client. blossom uses local IPC with an
  application ID instead (spec), so nothing is taken from here.
- Stats: `app/.../data/stats/ListeningStats.kt` + `ListeningRecorder.kt` exist (Android-only, tied to its storage). blossom's
  stats rules (30 s rule, ISO weeks, DST, streaks, Wrapped) are written new against the spec; nothing copied.

## What Spotube v6.0.0 looks like

- Compose Multiplatform, Kotlin 2.4.21, CMP 1.12.2, AGP 9.1, `composeApp` with `androidJvmMain` and `jvmMain`.
- **Desktop audio engine:** `composeApp/src/jvmMain/.../core/audioplayer/AudioPlayer.jvm.kt` — **libVLC through vlcj 4.12.1**
  (`uk.co.caprica:vlcj` + `vlcj-natives`), factory flags `--no-video --vout=dummy --aout=any`. Natives are bundled by the
  `org.spotube.vlcj-bundler` Gradle plugin (0.2.1, Gradle Plugin Portal), which downloads VLC for each OS and generates a
  loader object; Spotube skips bundling only for its Linux .deb.
- **System media controls:** `core/mediacontrol/SystemMediaControlService.kt` on top of
  `dev.nucleusframework:nucleus.media-control` (MIT, Maven Central). One API (`configure(dbusName, displayName)`,
  `setMetadata`, `setPlaybackState`, `attach(events)`) with bundled natives for Linux MPRIS (D-Bus name from `dbusName`) and
  Windows SMTC (`win32-x64/nucleus_media_control_windows.dll`).
- Spotify/SoundCloud: none in the core; v6 moved metadata sources into Zipline plugins. Nothing to take.
- Scrobbling: `core/scrobble/ScrobbleManager.kt` (queue only). BitChord's clients are more complete; take BitChord's.

## Map

| Upstream path | → blossom module | License | Changes needed |
|---|---|---|---|
| BitChord `shared/.../innertube/Innertube.kt` (search, browse, next, player context, visitor data, cookie auth) | `source/youtubemusic` | GPL-3.0 | Keep only search/browse/next/radio/auth; drop library mutation, uploads, listen-together; replace `DebugLog`/`TrackLog` with blossom logging; constructor-injected `HttpClient`. |
| BitChord `shared/.../innertube/InnertubeParser.kt` | `source/youtubemusic` | GPL-3.0 | Map to blossom `Track`/`AlbumPage`/`ArtistPage`/`HomeSection`; `ytm:` IDs. |
| BitChord `shared/.../innertube/StreamResolver.kt`, `InnerTubeXResolver.kt`, `PlayerClient.kt` | `source/youtubemusic` | GPL-3.0 | Turn the `object` into a class behind `MusicSource.resolveStream`; return `StreamInfo` with `mediaHeadersFor(url)` and the URL's `expire` param as `expiresAt`; keep the probe; drop upload escalation and loudness. InnerTubeX bumped to the newest tag that works (decided in Task 2.2). |
| BitChord `potoken/*` + `app/.../PoTokenWebView.kt` | `source/youtubemusic` (androidMain) | GPL-3.0 | Android only; desktop runs without a token provider like BitChord's JVM target. |
| BitChord `shared/.../lyrics/{LrcLib,KuGou,BetterLyrics,LrcRed,BiniLyrics,PaxSenix,LyricsPlus,Unison,YouTubeLyrics,Musixmatch,Genius,TtmlLyrics,EnhancedLrc,LyricsHttp}.kt` | `feature/lyrics` (`providers/`) | GPL-3.0 | Each becomes a `LyricsProvider` returning blossom `Lyrics`; priorities from the list above; shared Ktor client; timeouts move to the resolver. |
| BitChord `app/.../lyrics/EmbeddedLyrics.kt` | `feature/lyrics` (`LocalLyricsSource`) | GPL-3.0 | Replace with jaudiotagger USLT/SYLT reads on both platforms (EmbeddedLyrics parses MediaStore/Android tags). |
| BitChord `shared/.../spotify/SpotifyImporter.kt`, `SpotifyLibrary.kt` | `source/spotify` | GPL-3.0 | Output `ImportedPlaylist`/`ImportedTrack` with durations, ISRC when present and album; albums and single tracks too; GraphQL pagination for any size. |
| BitChord `app/.../scrobbling/{LastFM,ListenBrainzManager}.kt` | `feature/integrations` (`scrobble/`) | GPL-3.0 | Behind blossom's `ScrobbleQueue`; keys from build config. |
| Spotube `composeApp/src/jvmMain/.../audioplayer/AudioPlayer.jvm.kt` | `core/player` (desktopMain `DesktopPlaybackEngine`) | AGPL-3.0-or-later | Single-item player per instance (the queue is blossom's), two instances for crossfade/gapless, vlcj `Equalizer`, events mapped to `EngineEvent`. |
| Spotube `vlcj-bundler` plugin config (`composeApp/build.gradle.kts`) | `composeApp` packaging | AGPL-3.0-or-later (config) | Bundle VLC on **every** desktop format, including .deb (blossom's installs are self-contained). |
| Spotube `core/mediacontrol/SystemMediaControlService.kt` | `core/player` (desktopMain `media/`) | AGPL-3.0-or-later | `configure("blossom", "blossom")` → `org.mpris.MediaPlayer2.blossom`; metadata from `MprisMetadataMapper`. |

## Desktop audio engine: libVLC via vlcj (Spotube's)

Chosen because it is what Spotube v6 ships, it is bundleable for Windows and Linux with the existing plugin, and it covers
every requirement: HTTP(S) streams, local files, an equalizer (`uk.co.caprica.vlcj.player.base.Equalizer`, 10 bands + preamp)
and two player instances for crossfade and gapless pre-roll.

libVLC can set only `User-Agent`/`Referer` on HTTP, not arbitrary headers. blossom therefore feeds VLC every remote stream
through the same 127.0.0.1 `LocalStreamServer` that serves the encrypted cache (Task 5.3): the server forwards the
`StreamInfo.headers` upstream and supports Range. One loopback path, any header, no plaintext on disk. The proxy lands in
Task 2.5 and Task 5.3 adds the decrypting route to it.

## Interface changes recommended

| Interface | Change | Why |
|---|---|---|
| `ImportedTrack` | add `album: String?` and `sourceKey: String?` (the service's own track ID/URL) and `unplayableReason: String?` | Task 6.2 tests the album name; Task 6.1 `UpdateExisting` needs an "origin track key"; Task 6.4 flags preview-only SoundCloud tracks with a reason. |
| `PlaybackEngine` | add `fun preload(input: MediaInput)` and `fun setEqualizer(gainsDb: FloatArray?, preampDb: Float)` (Task 9.x, default no-op) | Gapless pre-roll and the equalizer both live in the engine. Added when those tasks start, not now. |
| `StreamInfo` | none | `headers` is enough; the desktop proxy applies them. |
| `MusicSource`, `PlaylistImporter`, `Track`, `TrackId` | none | Fit what the upstream code needs. |
| `MprisMetadataMapper` | unchanged; its map feeds nucleus `MediaMetadata(title, artist, album, artUrl, lengthMs)` | nucleus builds the D-Bus dictionary itself, so `mpris:trackid` is computed and tested but nucleus assigns its own object path. |
