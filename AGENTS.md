# CLAUDE.md

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

The core communication mechanism. `MusicService` is a foreground service managing `MediaPlayer`, `MediaSession`, audio focus, and notifications. It maintains a static `registeredClients: MutableSet<MusicClient>` in its companion object.

**`MusicClient` interface** (implemented by activities, fragments, adapters):
- `playStateChanged`, `songChanged`, `durationChanged`, `isExiting`, `queueChanged`, `shuffleRepeatChanged`, `indexChanged`, `isLoading`, `spotifyImportChange`, `serviceStarted`

**`MusicClientActivity`** is the abstract base class (`AppCompatActivity + CoroutineScope + MusicClient`) that auto-registers/unregisters in `onResume`/`onPause`/`onDestroy`. Activities extend this; fragments register manually.

### Key Classes

| Class | Role |
|---|---|
| `MusicService` | Heart of the app. Foreground service, MediaPlayer, MediaSession, observer registry |
| `MusicClientActivity` | Abstract base activity with auto MusicClient registration |
| `MainActivity` | Entry point. Hosts ViewPager2 (Home/Search/Playlists), bottom nav, mini-player bar |
| `Player` | Full-screen player. 5 layout variants by DPI. Seekbar, album art (Palette colors), metadata editing |
| `Home` (fragment) | Tab 1. Song list, `streamAudio()` |
| `Search` (fragment) | Tab 2. NewPipeExtractor search (YouTube/YouTube Music, multiple content filters) |
| `Playlists` (fragment) | Tab 3. Local playlist management, Spotify import trigger |
| `Shared` | Singleton utility. Song list loading (MediaStore + Able dir), playlist CRUD, album art I/O |
| `Constants` | File paths (lazy from `baseDir` set in `AbleApplication`), API keys, URLs |
| `ApiKeyProvider` / `LastFmKeyPrompt` | Runtime Last.fm API key storage (SharedPreferences) and in-app import dialog |
| `CustomDownloader` | OkHttp-backed NewPipeExtractor `Downloader` singleton |
| `SwipeController` | Custom `ItemTouchHelper.Callback` for swipe actions (add to queue/delete/stream) |
| `ChunkedDownloader` | Downloads audio via 256KB Range-header chunks to bypass YouTube throttling |
| `DownloadService` | `JobIntentService` for downloads (download body is commented out / WIP) |
| `AbleApplication` | `Application` subclass, initializes `Constants` and `ViewPump` |
| `ViewMode` | Global list/grid preference + grid column count; `layoutManagerFor()` is the single source for layout managers |
| `LastFmClient` | Last.fm API client for discovery: similar tracks/artists, artwork resolution, rate limiting |
| `TrackResolver` | Resolves a Last.fm `TrackSuggestion` to a playable `Song` via YouTube Music |
| `Discover` (fragment) | Tab 5. Playlist-based discovery (Last.fm similar tracks aggregated across seeds) |
| `DiscoverResultAdapter` | Renders discovery suggestions in list or grid rows (used by Search chips, Discover tab, Player sheet) |

### Data Models (`:core:model`)

- **`Song`** — Central model: `name`, `artist`, `youtubeLink`, `filePath`, `ytmThumbnail`, `albumId`, `isLocal`, `cacheStatus`, `streamProg`, plus streaming fields (`streamMutexes`, `internalStream`, `streams`)
- **`SongState`** — `playing` / `paused`
- **`CacheStatus`** — `NULL` / `STARTED`
- **`MusicMode`** — `"Download"` / `"Stream"`
- **`Format`** — `MODE_MP3` / `MODE_WEBM`
- **`Playlist`** — `name` + `JSONArray` of songs
- **`DownloadableSong`** — Intent payload for DownloadService

## View Mode & Grid Columns

All list/grid screens share one global preference managed by `utils/ViewMode.kt`:

- `view_mode_grid` (boolean, default `false`) — grid instead of list
- `grid_column_count` (string `"1"`..`"4"`, default `"2"`) — columns for **every** grid in the app; editable in Settings as "Grid columns" (`ListPreference`, which is why it is stored as string; parsed with `toIntOrNull().coerceIn(1, 4)`)

Rules:

- Never hardcode `GridLayoutManager(context, 2)` — always use `ViewMode.layoutManagerFor(context)`.
- Screens with a list: Library, Playlists, Search (YT/YTM/chips/drill-down), LibraryDetail, LocalPlaylist, AlbumPlaylist, Discover tab, and the Player's similar-tracks bottom sheet. Most have an inline toggle icon bound to `ViewMode.toggleIcon`.
- Adapters choose the item layout themselves (`ViewMode.isGrid(parent.context)` → `rv_grid_result` vs row layout).

### Discovery (Last.fm)

- Requires a Last.fm API key, entered in-app on first use (`ApiKeyProvider` + `LastFmKeyPrompt`).
- Entry points: Search chips (tracks/artists), Discover tab (seeded by playlist tracks), Player → Discovery button (similar tracks in a `BottomSheetDialog` with per-track play/queue/download actions).
- `MusicService.addToPlayQueue` notifies clients via `queueChanged` on `Dispatchers.Default`.

## NewPipeExtractor Integration

Initialized once in `MainActivity.onCreate()` via `NewPipe.init(CustomDownloader.getInstance())`.

- **Search**: `YouTube.getSearchExtractor(query, contentFilter)` with filters for `MUSIC_SONGS`, `MUSIC_ALBUMS`, `MUSIC_PLAYLISTS`, `VIDEOS`
- **Stream resolution**: `StreamInfo.getInfo(url)` → `audioStreams[last]` → `stream.content` for URL, `stream.averageBitrate`, `stream.getFormat()!!.suffix`
- **Playlist enumeration**: `YouTube.getPlaylistExtractor(link).fetchPage()` → iterate `initialPage.items`

## Key Libraries

| Library | Purpose |
|---|---|
| NewPipeExtractor v0.25.2 | YouTube search, stream URL extraction, playlist enumeration |
| mobile-ffmpeg (local .aar) | Audio transcoding, metadata writing |
| OkHttp 5.3.2 | HTTP client for NewPipe downloader and streaming |
| Glide 4.16.0 | Image loading (album art, thumbnails) |
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

- `DownloadService` download body is commented out (WIP)
- `SpotifyImportService` import logic is commented out (WIP)
- `MusicService.registeredClients` is not thread-safe (potential `ConcurrentModificationException`)
- No ViewModel/LiveData — all state is in service companion object statics
- Compose dependencies declared in build-logic but not used in app module

## Conventions

### Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <description>

[optional body]

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `style`, `test`, `perf`, `build`, `ci`
