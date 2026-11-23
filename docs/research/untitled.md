# untitled.stream investigation

Done 2026-11-21 with `curl` and a JVM `HttpClient`, using the owner's two sample links (both project links,
"sample link 1" with 15 tracks and "sample link 2" with 4) plus four publicly posted single-track links found in
public GitHub repositories. No browser tool was available; everything below comes from the share pages, their
`__remixContext` data and the web player's JavaScript bundles (`/assets/audio-context-*.js`, `/assets/storage-url-*.js`,
`/assets/manifest-*.js`).

## Outcome: gate (a) — works without signing in

- Metadata, signed audio URLs and the audio itself all load **without any account or cookie**.
- Audio is the uploaded file (mp3/wav/… as uploaded) or a transcoded mp3, served as plain HTTP with Range support.
  **No DRM**: no EME/Widevine, no encryption, ordinary `audio/mpeg` bytes (`ffprobe`: mp3, 44.1 kHz stereo).
- No paid membership involved (`payment_required: false`, `user_required_to_listen: false` on the samples).

So blossom streams untitled directly and needs no sign-in flow. A link the owner keeps **private** answers
`302 → /login`; blossom reports that as "This untitled link is private or was removed" and keeps the entry as
UNAVAILABLE (spec: dead links stay in the playlist).

## Link formats

The site is a Remix app; its route manifest lists these public pages:

| Kind | Pattern (regex, path part) | Example |
|---|---|---|
| Project (album) | `^/library/project/([A-Za-z0-9]{10,40})/?$` | `https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU` |
| Track inside a project | `^/library/project/([A-Za-z0-9]{10,40})/track/([A-Za-z0-9]{10,40})` | `https://untitled.stream/library/project/AbCd…/track/WxYz…` |
| Single track | `^/library/track/([A-Za-z0-9]{10,40})/?$` | `https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK` |
| Embeds | `^/(library/(project|track)/[A-Za-z0-9]+/embed|embed/[A-Za-z0-9]+)` | `…/library/track/QrSt…/embed` |
| Invite | `^/invite/([A-Za-z0-9_-]+)` | `https://untitled.stream/invite/abc123` (not verified: no sample; resolved by following redirects) |

Host: `untitled.stream` (also `www.untitled.stream`). Slugs seen are 21 characters `[A-Za-z0-9]`. Query strings and
fragments are ignored.

## Metadata

Append Remix's data parameter to the page URL to get the page loader's JSON directly (no HTML parsing):

- Project: `GET https://untitled.stream/library/project/<slug>?_data=routes%2Flibrary.project.%24projectSlug`
- Track:   `GET https://untitled.stream/library/track/<slug>?_data=routes%2Flibrary.track.%24trackSlug`

Shapes (only the fields blossom uses; full sanitized samples in
`source/untitled/src/commonTest/resources/fixtures/{project_data,track_data}.json`):

```json
{ "project": {
    "project": { "title": "...", "slug": "...", "username": "uploader", "access_type": "public",
                 "artwork_signed_url": "https://sb.untitled.stream/storage/v1/object/sign/private-project-art/...?token=...",
                 "payment_required": false, "user_required_to_listen": false, "password_protected": false },
    "tracks": [ { "id": "trck_…", "slug": "...", "title": "Kanye West – Song (Yedit)", "username": "uploader",
                  "duration": 105.29, "file_type": "mp3",
                  "audio_url": "https://sb.untitled.stream/storage/v1/object/private-audio/<owner>/<file>.mp3",
                  "audio_fallback_url": "https://sb.untitled.stream/storage/v1/object/private-transcoded-audio/<owner>/<file>.mp3",
                  "transcoding_state": "transcoded", "lexorank": "0|hzzzzz:" } ] } }
{ "track": { …same track fields… } }
```

- Titles, uploader names (`username`) and artwork come straight from these fields, kept verbatim.
- `duration` is seconds (float). Track order in a project is the array order (`lexorank` ascending).
- Project artwork: `artwork_signed_url` (signed, expires); tracks inherit the project's artwork.
- A link that is private or deleted: the page answers `302 Location: /login`, `_data` answers `204` with no body.

## Audio URL

The web player (`getBaseAudioUrl` in `audio-context-*.js`) turns a storage URL into a short-lived signed URL:

1. Split `audio_url` after `/storage/v1/object/` into `<bucket>/<path>` (buckets seen: `private-audio` for the original
   upload, `private-transcoded-audio` for the mp3 transcode; `audio-previews` URLs are used as-is).
2. `GET https://untitled.stream/api/storage/buckets/<bucket>/objects/<url-encoded path>/signedUrl?durationInSeconds=3600&cacheBufferInSeconds=300`
   with header `Content-Type: application/json` → `{"url": "https://sb.untitled.stream/storage/v1/object/sign/<bucket>/<path>?token=<jwt>", "expiresInMs": 3600000}`.
3. Play that URL. The player re-requests a new signed URL before `expiresInMs` runs out.

blossom prefers `audio_fallback_url` (the mp3 transcode, smaller and always mp3) when `transcoding_state` is
`transcoded`, else `audio_url`. `StreamInfo.expiresAt = now + expiresInMs`, so `StreamResolver`'s
expiry-minus-5-minutes cache re-signs in time.

## Required headers and cookies

- **No cookies.** The page sets `__cf_bm` (Cloudflare) and an installation id; neither is needed.
- **`untitled.stream` (pages, `_data`, `/api/...`)** sits behind Cloudflare bot rules keyed on the User-Agent:
  `curl/*`, `Java-http-client/*` and bare `Mozilla/5.0` get a 403 "Just a moment…" challenge; a full desktop browser UA
  (and, as it happens, `okhttp/*` and `blossom/*`) get 200. blossom sends a full desktop Chrome UA. A JVM
  `java.net.http.HttpClient` with that UA gets 200, so the TLS fingerprint is not the issue.
- **`sb.untitled.stream` (signed media)** only rejects curl's default UA; any other UA works, including VLC and
  ExoPlayer. `StreamInfo.headers = {"User-Agent": <browser UA>}`.
- Range requests: `206` with `Content-Range`, `Accept-Ranges: bytes`, `Cache-Control: private, max-age=300`.

## Expiry

- Signed media URL: `expiresInMs` (3,600,000 when asking for 3600 s). The duration is the caller's choice.
- Signed artwork URLs in the page data also carry a token and expire; blossom re-reads the page data when artwork fails.

## Rate limits

None hit during roughly 40 requests over 10 minutes (pages, `_data`, signing, ranged media). Cloudflare can challenge
bursts; blossom signs URLs lazily (on play/download), not for a whole project at import.

## Sign-in

Not needed (gate a). The login routes (`/_auth-v2.sign-in.email`, `.phone`, magic codes) exist for uploaders. Private
projects need the owner's invite or account, which blossom doesn't handle; such links are reported as private.
