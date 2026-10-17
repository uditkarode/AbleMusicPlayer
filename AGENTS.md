# AGENTS.md

## Project Overview

AbleMusicPlayer is an open-source Android music player that streams and downloads audio from YouTube/YouTube Music using NewPipeExtractor. It supports local file playback, streaming, cache-while-stream, and playlist management. Licensed under GPLv3.

- **Package**: `io.github.uditkarode.able`
- **Min SDK**: 23 (Android 6.0), **Target/Compile SDK**: 36
- **Language**: 100% Kotlin (built-in via AGP 9, no separate kotlin-android plugin)
- **Build**: Gradle 9.3.1, AGP 9.0.1, Kotlin 2.3.10, version catalog (`gradle/libs.versions.toml`)

## Module Structure

```
:app                    Main Android application (activities, fragments, adapters, utils)
:core:model             Data models (Song, Playlist, MusicMode, Format, Spotify models)
build-logic/            Convention plugins (Compose infra prepared but not used in app)
```

## Architecture

Classic Android activity/fragment/service architecture with a custom observer pattern — no ViewModel/LiveData/Hilt.

### MusicService & MusicClient Observer Pattern

The core communication mechanism. `MusicService` is a foreground service managing `MediaPlayer`, `MediaSession`, audio focus, and notifications. It maintains a static `registeredClients: CopyOnWriteArraySet<MusicClient>` in its companion object (thread-safe iteration over a snapshot — safe to notify while clients register/unregister).

**`MusicClient` interface** (implemented by activities, fragments, adapters):
- `playStateChanged`, `songChanged`, `durationChanged`, `isExiting`, `queueChanged`, `shuffleRepeatChanged`, `indexChanged`, `isLoading`, `spotifyImportChange`, `serviceStarted`

**`MusicClientActivity`** is the abstract base class (`AppCompatActivity + CoroutineScope + MusicClient`) that auto-registers/unregisters in `onResume`/`onPause`/`onDestroy`. Activities extend this; fragments register manually.

### Key Classes

| Class | Role |
|---|---|
| `MusicService` | Heart of the app. Foreground service, MediaPlayer, MediaSession, observer registry |
| `MusicClientActivity` | Abstract base activity with auto MusicClient registration |
| `MainActivity` | Entry point. Hosts ViewPager2 (Home/Search/Library/Playlists/Discover), bottom nav, mini-player bar |
| `Player` | Full-screen player. Single responsive layout (`res/layout/player.xml`). Seekbar, album art via the hybrid chain (app-stored covers → Last.fm identity → Deezer hi-res, Palette colors). Download button (grey/disabled when the song is already in the library), save-to-playlist button, artist tap → discography bottom sheet. No metadata editing (rename flows removed) |
| `Home` (fragment) | Tab 1. Song list, `streamAudio()` |
| `Search` (fragment) | Tab 2. Catalog-backed Discover search (see *Search & Discovery*) with modes Discover/Album/Playlists; raw YouTube/YTM reachable via `!`/`?` query prefixes |
| `Library` (fragment) | Tab 3. Local songs grouped by Artists/Albums toggle (`LibraryGroupAdapter` → `LibraryDetail` filtered song list) |
| `Playlists` (fragment) | Tab 4. Local playlist management, Spotify import trigger |
| `Shared` | Singleton utility. Song list loading (MediaStore + Able dir), playlist CRUD, album art I/O |
| `Constants` | File paths (lazy from `baseDir` set in `AbleApplication`), API keys, URLs |
| `ApiKeyProvider` / `LastFmKeyPrompt` | Runtime Last.fm API key storage (SharedPreferences) and in-app import dialog |
| `CustomDownloader` | OkHttp-backed NewPipeExtractor `Downloader` singleton |
| `SwipeController` | Custom `ItemTouchHelper.Callback` for swipe actions (add to queue/delete/stream) |
| `ChunkedDownloader` | Downloads audio via 256KB Range-header chunks to bypass YouTube throttling |
| `StreamProxy` | Local HTTP proxy for progressive streaming: serves the resolved audio to `MediaPlayer` immediately while caching the bytes; on completion hands off to the file-based playback path |
| `DownloadService` | Foreground `Service` for downloads: ChunkedDownloader → FFmpeg transcode (`-c:a mp3`) → video-ID COMM tag → art embed → rename to song name. Persistent queue in SharedPreferences, dedup statics (`isAlreadyQueued`, `onDownloadComplete`, progress) |
| `AbleApplication` | `Application` subclass, initializes `Constants` and `ViewPump` |
| `ViewMode` | Global list/grid preference + grid column count; `layoutManagerFor()` is the single source for layout managers |
| `LastFmClient` | Last.fm API client for discovery: similar tracks/artists, artwork resolution, rate limiting |
| `TrackResolver` | Resolves a Last.fm `TrackSuggestion` to a playable `Song` via YouTube Music |
| `CatalogSearch` | Two-tier cascade behind the Discover search mode: track gate (title + artist-name rules on normalized keys) and artist tier (homonym-safe), returning the catalog matches that gate the YouTube rows below |
| `Discover` (fragment) | Tab 5. Playlist-based discovery (Last.fm similar tracks aggregated across seeds) |
| `DiscoverResultAdapter` | Renders discovery suggestions in list or grid rows (used by Search chips, Discover tab, Player sheet) |
| `DiscographyAdapter` | Artist drill-down list: collapsible album headers (newest first) + "Singles" section; used by Search drill-down and the Player's artist bottom sheet |
| `DeezerClient` / `ArtworkCache` / `LazyArtLoader` | Hi-res cover fallback for the Player's art chain; persistent URL cache (incl. negative caching) and lazy per-row art loading |

### Data Models (`:core:model`)

- **`Song`** — Central model: `name`, `artist`, `youtubeLink`, `filePath`, `placeholder`, `ytmThumbnail`, `albumId`, `album`, `isLocal`. `youtubeLink` is populated for library songs too (read back from the file's video-ID tag at scan time)
- **`SongState`** — `playing` / `paused`
- **`MusicMode`** — `"Download"` / `"Stream"`
- **`Format`** — `MODE_MP3` / `MODE_WEBM`
- **`Playlist`** — `name` + `JSONArray` of songs
- **`DownloadableSong`** — Intent payload for DownloadService
- **`TrackSuggestion` / `AlbumSuggestion` / `ArtistSuggestion`** — catalog discovery models (Last.fm / Deezer / YTM)

## Video-ID Identity & Download Dedup

Every downloaded MP3 carries its YouTube video ID so duplicate detection never
depends on title/artist (which used to be editable and may differ in legacy
files):

- **Write**: `DownloadService` persists the ID as a canonical ID3 `COMM` frame
  (`FieldKey.COMMENT`) right after the FFmpeg transcode. FFmpeg additionally
  maps `-metadata comment=` to a legacy `TXXX` frame with description
  "comment" — older downloads only have that one.
- **Read**: `Shared.getSongList` extracts the ID with jaudiotagger (`COMM`
  first, `TXXX "comment"` fallback, validated against an 11-char URL-safe
  regex), caches it per path (`pathIdCache`), populates `Song.youtubeLink` and
  rebuilds the registry `Shared.localIds` (`isInLibrary(id)`,
  `refreshLocalIds()`, `localIdsFresh`).
- **Playlist dedup**: `Shared.songKey` = `yt:<videoId>` when known, else
  `filePath`; `addToPlaylist`/`removeFromPlaylist` match on that key (blank
  key → no-op), fixing false duplicates between streamed songs.

### Player Buttons

- **Download** (`player_download`, bottom-right of the song info block):
  grey + disabled when the current song is in the library (`Shared.isInLibrary`
  or device-local `isLocal`), white otherwise; re-evaluated on song change and
  `onResume`, with a cold-start `refreshLocalIds()` on IO when Home hasn't
  scanned yet. Tapping funnels into `Player.enqueueDownload`, which also
  blocks already-queued/in-library songs (covers the discovery sheet too).
- **Save** (`player_save`, "+" icon left of Discovery): `BottomSheetDialog`
  (`sheet_add_to_playlist`) listing user playlists with a trailing create row
  (the only row when none exist). Saving stores the song reference in the
  playlist JSON — never triggers a download. Creating uses a name-input
  dialog and guards against overwriting an existing playlist.
- **Artist tap** (`artist_name`): opens the artist discography in a bottom
  sheet (`sheet_discography` + `DiscographyAdapter`) — same data flow as
  Search's drill-down (`artistTopAlbums` → `albumTracks` → leftover
  `artistTopTracks` as singles, albums sorted newest-first). Artist rows in
  the similar-tracks sheet behave the same.

## Playback Pipeline (progressive streaming)

`MusicService` resolves the best audio stream via `StreamInfo.getInfo` and plays it through `StreamProxy`, a local HTTP proxy that starts serving the audio to `MediaPlayer` immediately while the incoming bytes are cached to disk; when the file completes it hands off to the file-based playback path (unified handoff, no restart). The next song is prefetched in the background (`maybePrefetchNext`). Verified behavior: first playback in ~2–4s, instant skip-back on freshly cached songs, zero errors in steady state.

## View Mode & Grid Columns

All list/grid screens share one global preference managed by `utils/ViewMode.kt`:

- `view_mode_grid` (boolean, default `false`) — grid instead of list
- `grid_column_count` (string `"1"`..`"4"`, default `"2"`) — columns for **every** grid in the app; editable in Settings as "Grid columns" (`ListPreference`, which is why it is stored as string; parsed with `toIntOrNull().coerceIn(1, 4)`)

Rules:

- Never hardcode `GridLayoutManager(context, 2)` — always use `ViewMode.layoutManagerFor(context)`.
- Screens with a list: Library, Playlists, Search (YT/YTM/chips/drill-down), LibraryDetail, LocalPlaylist, AlbumPlaylist, Discover tab, and the Player's similar-tracks bottom sheet. Most have an inline toggle icon bound to `ViewMode.toggleIcon`.
- Adapters choose the item layout themselves (`ViewMode.isGrid(parent.context)` → `rv_grid_result` vs row layout).

## Search & Discovery

Tab Search cycles three text-search modes by tapping the mode chip:
**Discover → Album → Playlists** (a stored "Music" preference migrates to
"Discover" on first open). The plain-YouTube surface stays reachable with the
`!` (YouTube) / `?` (YouTube Music) query prefixes.

- **Discover mode** (default): the query runs the `CatalogSearch` cascade —
  catalog rows (tracks: Deezer → Last.fm; artists: Last.fm → Deezer) gate
  relevance and render **above** the raw YouTube rows (hybrid `ConcatAdapter`,
  YT section labeled "Discover"). Without a Last.fm key the Last.fm parts
  return empty and the cascade runs Deezer-only.
- **Track gate**: bidirectional normalized-prefix match on `normKey`
  (punctuation inside words is stripped, so `GREEK.` ≡ `Greek`) — title
  matches first, then a second pass lets through tracks whose *artist* matches
  the query (catalog rank order).
- **Artist tier**: Last.fm `artist.search` (identity-ranked) → Deezer
  `/search/artist` (relevance-ranked); collisions on the normalized name keep
  the variant with the real audience; YouTube Music `MUSIC_ARTISTS` cards
  (`ChannelInfoItem`, " - Topic" suffix stripped, deduped by normalized name)
  form tier 4. Cards are audience-ordered and capped at 4.
- **Artist drill-down** (`showDiscography` → `DiscographyAdapter`): Last.fm
  discography when a key is present (top albums → `albumTracks`, leftover top
  tracks as "Singles", albums newest-first); the keyless fallback enumerates
  the artist's YTM albums+singles playlists (`MUSIC_ALBUMS` search + parallel
  playlist extraction). Albums whose `album.getInfo` returns **zero tracks**
  (Last.fm knows the title but has no tracklist) render as headers that expand
  to nothing.
- **Drill-down covers** resolve lazily (`LazyArtLoader`): album headers via
  Deezer `albumCover` (the winner also feeds the album's track rows), tracks
  via `resolveTrackImage` (persistent `ArtworkCache`); the artist header uses
  the tapped suggestion's own image when present, else `resolveArtistImage`.
- **Key-gated entry points**: Search chips (tracks/artists), Discover tab
  (playlist-seeded similar tracks), Player → Discovery button — all prompt for
  the key (`LastFmKeyPrompt`) when missing. The drill-down itself works keyless
  via the YTM fallback.
- Raw modes: **Album** = YTM `MUSIC_ALBUMS` search; **Playlists** =
  `MUSIC_PLAYLISTS`; the four raw fetch branches are unified in
  `ytRows()`/`playlistRows()`.
- `MusicService.addToPlayQueue` notifies clients via `queueChanged` on
  `Dispatchers.Default`.

## NewPipeExtractor Integration

Initialized once in `MainActivity.onCreate()` via `NewPipe.init(CustomDownloader.getInstance())`.

- **Search**: `YouTube.getSearchExtractor(query, contentFilter)` with filters for `MUSIC_SONGS`, `MUSIC_ALBUMS`, `MUSIC_PLAYLISTS`, `VIDEOS`
- **Stream resolution**: `StreamInfo.getInfo(url)` → `audioStreams.maxByOrNull { it.averageBitrate }` → `stream.content` for URL, `stream.averageBitrate`, `stream.getFormat()!!.suffix`
- **Playlist enumeration**: `YouTube.getPlaylistExtractor(link).fetchPage()` → iterate `initialPage.items`

## Key Libraries

| Library | Purpose |
|---|---|
| NewPipeExtractor v0.26.5 | YouTube search, stream URL extraction, playlist enumeration |
| mobile-ffmpeg (local .aar) | Audio transcoding, metadata writing |
| OkHttp 5.3.2 | HTTP client for NewPipe downloader and streaming |
| Glide 5.0.5 | Image loading (album art, thumbnails) |
| Gson 2.13.2 | Playlist JSON serialization |
| Lottie 6.7.1 | Loading animations |
| Material Dialogs 3.3.0 | Dialogs and bottom sheets |
| Calligraphy3 + ViewPump 2.1.1 | Custom font injection (Inter), local instance in AbleApplication |
| Palette KTX | Album art color extraction for player theming |
| jaudiotagger-android | Audio metadata tag reading/writing |

## Build & Run

```bash
./gradlew :app:assembleDebug
```

ABI splits are enabled: the debug APKs land in `app/build/outputs/apk/debug/` as `app-arm64-v8a-debug.apk` / `app-armeabi-v7a-debug.apk` (no plain `app-debug.apk`). Install the matching one, e.g.:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Storage

App uses scoped storage via `context.getExternalFilesDir()`:
- `ableSongDir` — base dir for songs
- `playlistFolder` — `playlists/` subdirectory (JSON files)
- `playlistSongDir` — `playlist_songs/`
- `albumArtDir` — `album_art/`
- `cacheDir` — `cache/`

## Known Limitations

- `SpotifyImportService` import logic is commented out (WIP)
- No ViewModel/LiveData — all state is in service companion object statics
- Compose dependencies declared in build-logic but not used in app module
- Device-local songs (`Shared.getLocalSongs`, outside the Able dir) have no
  embedded video ID, so download dedup only covers the Able library and the
  active download queue for them

## Conventions

### Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <description>

[optional body]

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `style`, `test`, `perf`, `build`, `ci`
