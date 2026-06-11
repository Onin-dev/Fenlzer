# Fenlzer Final Application Plan

Status: final product specification draft based on the completed clarification process.
Last updated: 2026-06-07.
Decision rule: when older and newer specifications conflict, the latest clarification wins.
Scope note: the YouTube download/import feature is treated as legally authorized and technically handled by the user's API.

## 1. Product purpose and boundaries

Fenlzer is a personal Android music application for the user's own usage. It is primarily a private music library and playback app with local storage, YouTube/API imports, smart playlists, detailed statistics, and a highly controllable queue.

V1 should focus on a polished personal music-player experience rather than social features, cloud sync, lyrics, or backup/export. Lyrics are explicitly out of scope.

### Settled product boundaries

- Personal app for private use.
- Music files imported into Fenlzer are copied into private app storage.
- Imported files are lost when the app is uninstalled.
- Backup/export is not necessary for V1.
- App language: English only.
- Dark theme only.
- AMOLED mode is included now.
- Android Auto support is required for V1.
- If the phone is currently streaming a remote Discover song when Android Auto connects, Android Auto may show Now Playing controls for that current remote song, but the item is not exposed in browse or queue. Android Auto must not abruptly skip or stop current playback solely because the current item is remote.
- Android Auto allows favouriting the current downloaded song, matching notification controls. Remote items remain excluded from Android Auto browse and queue.

Android Auto smart playlists:

- Shown: Favourites, Recently Played, Most Listened, Morning Mix, Afternoon Mix, Evening Mix, Night Mix.
- Hidden: Discover, Never Played, Missing Metadata.
- YouTube import/search/download is handled through an existing API and is considered authorized.
- The API will most likely be built/refined first, so Fenlzer keeps a companion API specification and should use Fenlzer-specific high-level API endpoints.
- Endpoint contract refinement order: Health -> Search -> Playlist Preview -> Downloads/Jobs -> Streaming -> Discover.
- Discover listening history upload uses Zstandard-compressed JSON chunks targeting roughly 1 MB each.
- YouTube playlist import is required.
- No playback speed feature.
- No crossfade feature.
- No lyrics system.

## 2. Main navigation and app shell

### Bottom navigation

Fenlzer has three main persistent tabs:

1. Home
2. Playlists
3. Import

The persistent mini-player sits above the bottom navigation.

### Persistent tab state

Main tabs preserve their state when switching tabs:

- Home search remains active when leaving and returning.
- Home sort/filter state remains active.
- Home keyboard does not automatically reopen when returning to an active search.
- Import search/results remain visible when leaving and returning.
- Import sub-state remains visible.
- Playlists tab keeps its scroll position.
- Opened playlist detail screens do not need to persist as tab state.
- Discover smart playlist contents remain the same until the user manually refreshes it.

### Settings, queue, statistics, and secondary screens

- Settings opens from a top-right gear icon on Home.
- Statistics opens only from a Home button.
- Queue opens from the mini-player more menu, fullscreen player, and other relevant playback controls.
- Queue opens as a full screen on portrait and as a side panel in landscape.
- Song Details is a dedicated screen/sheet reachable from selected song actions, fullscreen stats/details area, import result screen, and mini-player more menu.
- Import History is a separate screen from the main Import page.
- Active Imports is a separate screen reachable from the Import page and from an active progress banner/chip.

### Library browsers

The phone app includes Artists and Albums browsing in addition to Home search/filter. These browsers can be accessed from Library options or a Home library section. Genre remains search/filter only and does not need a dedicated browse screen.

Artist and album identity rules:

- Artist identity is based on the editable `artist` field.
- Album identity is based on editable `album + albumArtist` when Album Artist exists, otherwise `album + artist`.
- Artists and albums are editable in the phone app. Editing them is handled as a deliberate bulk metadata edit over the affected songs rather than as independent manually managed artist/album objects.
- Empty values are grouped under Unknown Artist / Unknown Album.
- Multiple artists are stored as one text field exactly as written, for example `Artist A, Artist B`; V1 does not parse them into separate artist entities.
- Fenlzer stores normalized internal sort/search keys for title, artist, album, and album artist while preserving the exact display text entered by the user.
- Artist browsing sorts exactly as written, so leading articles such as `The`, `A`, and `An` are not ignored.
- Album browsing sorts by Album Artist A-Z, then album title A-Z.
- Inside an album, if disc number exists, songs sort by disc number, then track number, then title. If disc number does not exist, songs sort by track number, then title.

Artist and album editing rules:

- Editing an artist from the Artists browser affects all songs whose current Artist exactly matches that artist. Fenlzer shows an affected-song preview and requires confirmation before applying the change.
- If an artist rename would merge into an existing artist name, Fenlzer warns first with wording such as `This will merge artists` before applying.
- Editing an album title from Album Details affects songs matching the exact album identity: Album + Album Artist.
- If an album edit would merge into an existing Album + Album Artist identity, Fenlzer warns first with wording such as `This will merge albums` before applying.
- Editing Album Artist from Album Details affects every song in that exact album identity.
- Album Details can bulk-edit album title, Album Artist, Year, Genre, and album thumbnail for all songs in the album, with confirmation.
- Setting a thumbnail from Album Details sets the same custom thumbnail on every song in that album. Fenlzer does not need a separate album-level thumbnail model for V1.
- If a bulk album edit would overwrite existing custom Year, Genre, or Thumbnail values, Fenlzer asks with checkboxes whether to overwrite existing custom values or only fill empty fields.
- Artist Details allows editing the artist name only. Artist thumbnails and bulk genre/year editing are out of scope for V1.
- Artist and album edits are stored internally as bulk metadata operation records, including operation type, old value, new value, affected track IDs, and timestamp. No user-facing undo is required, but this preserves audit/debug information.

Artist and Album detail screens:

- Artist Details shows artist name, songs by artist, albums by artist, total songs, total listened time, most played song by this artist, and edit artist name.
- Album Details shows album title, Album Artist, year if available, songs ordered by disc/track number, total duration, total listened time, most played song from album, completion average, last played, and album-level editing actions.
- Artist Details and Album Details do not include destructive delete actions. Bulk deletion is only available through song-level actions, selection mode, Storage Management, and Song Details.

## 3. Persistent mini-player

### Normal mini-player layout

The mini-player is always visible, even when no song has ever been played.

Normal layout:

[Thumbnail] [Title / Artist] [Heart] [Play/Pause] [Next] [More]
[Thin embedded progress bar]

Title should be visually more important than artist. Artist appears below the title in a subtler gray style where space allows.

### Mini-player empty state

When no song has ever been played, the mini-player still mirrors the normal layout so the user understands the app structure.

Empty-state layout:

[Placeholder] [Import songs to start listening] [disabled Heart] [disabled Play] [disabled Next] [disabled More]
[Inactive thin progress bar]

Tapping the main empty-state area opens the Import tab/page.

### Mini-player interactions

- Tap main area: open fullscreen player.
- Drag/tap progress bar: seek.
- Tap heart: favourite/unfavourite current song.
- Tap play/pause: toggle playback.
- Tap next: follow the current repeat mode and skip logic.
- Tap more: open mini-player song/action menu.

### Mini-player more menu

The mini-player more menu contains:

- Open Queue
- Add to Playlist
- Song Details
- Edit Tags
- Sleep Timer

Do not include Delete from Fenlzer in the mini-player more menu.

### Private mode indicator

When private mode is enabled, show a small indicator in the mini-player. Private mode itself is changed only in Settings.

## 4. Fullscreen player

### Structure

The fullscreen player uses a bottom-sheet style expansion from the mini-player. The animation should feel smooth and modern. Elements shared between mini-player and fullscreen should slide naturally into position. Elements that exist only in fullscreen should appear as if already present when unobstructed; otherwise they fade in.

Portrait fullscreen player should be a clean music-player view:

- Large cover art.
- Title and artist.
- Duration.
- Seekbar.
- Current time and remaining time.
- Shuffle button.
- Previous button.
- Play/pause button.
- Next button.
- Repeat button.
- Heart/favourite button.
- Add to playlist button.
- Queue button.
- Audio output route.
- Sleep timer state when active.
- Song stats/details entry point.
- More menu.
- Down arrow and swipe-down gesture to minimize.

### Fullscreen lower area

Below the main controls, show playback context by default:

- Playing source, such as “Playing from Playlist: Night Drive”.
- Next song.
- Repeat state.
- Shuffle state.
- Sleep timer state when active.

The “Now Playing Source” label is shown only in the fullscreen player, not the mini-player.

### Gestures

- Swipe down: minimize fullscreen player.
- Swipe left/right on cover: previous/next track.

### Fullscreen more menu

The fullscreen more menu includes:

- Add to Playlist
- Edit Tags
- Share
- Sleep Timer
- Delete from Fenlzer

### Per-song stats sheet

The song statistics button opens a per-song stats sheet with:

- Title
- Artist
- Play count
- Total listened time
- Skip count
- Completion count
- Last played
- First imported
- Appears in playlists

### Landscape fullscreen player

Landscape fullscreen player uses:

- Cover art on the left.
- Controls and metadata on the right.

## 5. Home tab

### Main purpose

Home displays the full downloaded/imported library and provides library search, sorting, filtering, statistics access, and settings access.

### Home layout

Home contains:

- Search bar.
- Library options for sorting/filtering.
- Statistics button.
- Settings gear icon.
- Song list with metadata.
- Persistent mini-player above bottom navigation.

Adaptive top bar behavior:

- Portrait: search bar visible.
- Landscape: compact top layout to preserve vertical space.

### Empty Home state

When there are no songs, Home shows:

Import songs to start listening
[Import from Device]
[Search YouTube]

### Song row contents

Each song row shows:

- Thumbnail.
- Title.
- Artist.
- Duration.
- More menu.

If title metadata is empty, display the original filename as the title fallback.

If artist is empty:

- Local import: show empty artist.
- YouTube import: use channel/artist if available.

### Home song actions

Song more menu includes context-appropriate actions such as:

- Play Next
- Add to Queue
- Add to Playlist
- Song Details
- Rename Song / Edit Tags
- Change Artist
- Change Thumbnail
- Delete from Fenlzer

### Selection mode

Holding a song enters selection mode. Selection mode actions:

- Add to playlist
- Add to queue
- Play next
- Change artist
- Change thumbnail
- Select all
- Clear selection
- Delete from Fenlzer

### Sorting options

Home sorting options:

- Title A-Z
- Artist A-Z
- Recently added
- Recently played
- Most played
- Least played
- Duration shortest
- Duration longest
- Favourites first

### Filtering options

Home filtering options:

- All songs
- Favourites only
- Never played
- Recently added
- Missing artist
- Missing thumbnail
- Downloaded/imported source

Sorting/filtering UI:

- Phones: one combined Library Options sheet.
- Landscape/wider layouts: visible filter chips plus sort control when space allows.

### Home playback behavior

Home unfiltered tap behavior:

- Tapped song becomes current immediately.
- Existing queue remains after it.
- After the tapped song finishes, playback continues with the old queue as it was.

Home search active tap behavior:

- Tapped song becomes current immediately.
- Only the tapped song is played as the immediate override.
- Existing queue remains after it.
- After that song finishes, playback continues with the old queue.

This is a deliberate personal preference and must be preserved.

### Search matching

Home search matches:

- Title
- Artist
- Album Artist
- Album
- Genre

Notes are not included in Home search. Notes are searchable/visible only in Song Details.

## 6. Playlists tab

### Top-level playlist layout

Portrait layout:

- Smart Playlists shown as a horizontal row.
- Regular Playlists shown as a vertical list.

Landscape layout:

- Grid cards for both Smart Playlists and Regular Playlists.

### Playlist card contents

Each playlist card shows:

- Thumbnail.
- Name.
- Song count.
- Total duration.
- Last modified date.

Regular playlist thumbnail behavior:

- Custom playlist thumbnail overrides generated four-song collage.
- Removing custom thumbnail returns to generated collage.

### Smart playlists

Smart playlists:

- Are read-only.
- Can be saved as regular playlists.
- Are shown even when empty, with an empty-state explanation.

Smart playlist list:

- Discover
- Favourites
- Most Listened
- Recently Played
- Never Played
- Missing Metadata
- Morning Mix
- Afternoon Mix
- Evening Mix
- Night Mix

Discover:

- Generated through YouTube/API recommendations based on the user's library and tastes.
- Displays the same generated contents until the user manually refreshes it.

Favourites:

- All songs where isFavourite = true.
- Sorted by favouritedAt descending.
- Store favouritedAt.

Most Listened:

- Songs sorted by totalListenMs descending.
- Minimum listened time: 30 seconds.
- Suggested fixed limit: top 100.

Recently Played:

- Latest 100 recently played songs.

Never Played:

- All songs that have never had a valid listen.

Time-based playlists:

- Morning Mix: songs most listened to by total duration from 05:00-11:00.
- Afternoon Mix: songs most listened to by total duration from 11:00-17:00.
- Evening Mix: songs most listened to by total duration from 17:00-22:00.
- Night Mix: songs most listened to by total duration from 22:00-05:00.
- Suggested fixed limit: top 50 each.

### Regular playlists

Regular playlists:

- User-created.
- Have custom thumbnail support.
- Default thumbnail generated from the first four songs.
- Do not allow the same song twice.
- Store song count, total duration, and last modified date.

Regular playlist actions:

- Rename Playlist
- Change Thumbnail
- Delete Playlist

Deleting a regular playlist deletes only the playlist and keeps songs.

### Playlist creation

A create playlist button lets the user choose a name and create the playlist.

### Add-to-playlist behavior

The Add to Playlist dialog behaves like a toggle selector.

- Regular playlists appear.
- Favourites appears and behaves like a playlist.
- Smart playlists do not appear because they are read-only.
- If the song is already in a playlist, this is visually indicated.
- Clicking a playlist where the song already exists removes it from that playlist.

### Playlist detail screen

Playlist detail contains:

- Play all button.
- Shuffle play button.
- Add songs button.
- Search bar.
- Temporary sort.
- Manual reorder.
- Song count.
- Total duration.
- Last modified date.
- Playlist actions such as rename, change thumbnail, delete.
- Song list with metadata and song actions.

Playlist song actions:

- Play Next
- Add to Queue
- Add to Playlist
- Remove from Playlist
- Song Details

### Playlist ordering

- Manual order is preserved.
- Temporary sort does not overwrite manual order.
- Search does not overwrite manual order.
- Drag-to-reorder is disabled while temporary sort or search is active.

### Playlist playback

Playlist detail tap behavior:

- Replace queue with playlist songs.
- Start tapped song.

Play All:

- Replace queue with full list.
- Start first song.

Shuffle Play:

- Replace queue with shuffled list.
- Start first shuffled song.

If a playlist somehow contains duplicates from old data/import bugs, creating a queue from it keeps the first occurrence and ignores later duplicates.

Saving a smart playlist as a regular playlist saves the current visible songs as a static snapshot.

## 7. Queue

### Queue concept

The queue is treated as a manually modifiable temporary playlist. It persists after closing the app and can be created from Home, playlists, smart playlists, search, or manual queue actions.

The Queue screen displays the source/context of the queue at the top, such as:

- Queue from Playlist: Night Drive
- Queue from Smart Playlist: Most Listened
- Queue from Home
- Queue manually edited
- Queue from Playlist: Night Drive · Modified

The queue source label and modified state persist after app restart together with the queue items and current position.

### Queue screen contents

Queue screen shows:

- Queue source/context.
- Currently playing song.
- Previous songs.
- Upcoming songs.
- Clear queue/upcoming action.
- Save queue as playlist.
- Shuffle remaining/whole queue action as appropriate.

Opening the queue scrolls so the currently playing song is at the top. Scrolling upward shows previous songs.

### Queue row layout

Always use this queue row structure:

[Thumbnail] [Title over Artist] [≡]

Visual hierarchy:

- Title appears above artist.
- Title is larger and more visually important.
- Artist is below title in a grayer/subtler font.
- Three horizontal bars are the drag handle.

Queue removal is done by swiping the row from left to right. There is no visible X/remove button in the row.

Remote queue rows use subtle state badges when relevant: Remote, Ready, Downloading, or Unavailable.

### Queue row actions

- Drag using the three-horizontal-bars handle to reorder.
- Swipe left-to-right to remove from queue.
- Tap a queue item to jump to it.

### Queue editing rules

- Current song is fixed and cannot be dragged.
- User can reorder previous and upcoming songs.
- User can drag a previously played song into an upcoming position so it can play again soon.
- If the user removes the currently playing song, show a confirmation first, then skip to the next song if confirmed.
- If there is no next song after removing current, stop playback and show empty current song state.
- Removing non-current queue items does not need confirmation and shows an Undo snackbar.

### Queue duplicates

The same song cannot appear multiple times in the queue.

Rules:

- Add to Queue on a song already in queue removes the previous occurrence and adds/moves it according to the requested action.
- Play Next on a song already in queue removes the previous occurrence and places it after manually inserted Play Next songs.
- Add to Queue / Play Next on the currently playing song does nothing and shows “Already playing”.
- If a new queue is created from a playlist, the old queue is discarded, so duplicate checks apply only inside the new queue.

The queue data model still needs unique queue item IDs internally, even though duplicates are forbidden, because queue order and history are mutable.

Suggested QueueItem fields:

- queueItemId
- trackId
- position
- insertedBy
- addedAt

### Queue commands

Play Next:

- Insert after the currently playing song and after all manually inserted Play Next songs.

Add to Queue:

- Add song to the end of the queue, after removing any previous occurrence.

Clear Queue:

- Clear upcoming only.
- Keep current song playing.
- If there are no upcoming songs, the button is disabled.

Save Queue as Playlist:

- Save previous + current + upcoming.
- Remove duplicate songs automatically.

Shuffle Remaining / Shuffle Queue:

- Shuffle is treated as creating a new concrete queue order.

### Shuffle behavior

- Shuffle randomizes the whole queue immediately when creating a new queue.
- While a queue is playing, it is not dynamically shuffled unless the user manually shuffles it.
- When shuffling the whole queue while a song is playing, the current song stays at its visible position and all other songs shuffle around it.
- Turning shuffle off keeps the shuffled order as the new queue order.
- Manual reorder while shuffle is on keeps shuffle on and accepts the manual edit.
- If no queue or only one song exists, shuffle action is disabled.

### Repeat behavior

Default repeat mode: Repeat all.

Repeat button cycle:

1. Repeat all
2. Repeat one
3. Repeat off
4. Back to Repeat all

Additional rules:

- Save last repeat mode in settings.
- Show clear icons for all three repeat states.
- Repeat one loops the same song.
- Pressing Next manually while Repeat One is active goes to the next song despite Repeat One.
- Repeat all at queue end continues from the first item in the full queue, including previous songs.
- Mini-player Next follows the current repeat mode.

### Previous button behavior

- If current position is greater than 3 seconds, Previous restarts the current song.
- If current position is 3 seconds or less, Previous goes to the previous queue item.

## 8. Import system

### Import tab structure

The Import tab includes:

- Import from Device.
- Search YouTube.
- Import YouTube playlist.
- Entry to Active Imports.
- Entry to Import History.

The Import page itself remains the main import hub. Import History and Active Imports are separate screens.

### Active Imports screen

Active Imports shows currently queued/running imports and downloads.

Access:

- Button/card on Import screen.
- Persistent progress chip/banner when imports are running.

Example banner:

3 imports running - View

Active Imports should show:

- Song/video thumbnail if available.
- Title.
- Source: Device / YouTube / YouTube Playlist.
- Status: queued, downloading, extracting metadata, copying, completed, failed.
- Progress bar.
- Queue position.
- Cancel action.
- Retry where appropriate after failure.
- Error message if failed.

The user can cancel queued and currently running imports. Partial files should be cleaned up automatically.

### Local import

Import from Device lets the user choose one or multiple music files from storage and import them into Fenlzer.

Supported formats for V1:

- MP3
- M4A
- WAV
- FLAC
- OGG

Local import behavior:

- Copy imported files into private app storage.
- Internal filename format: hash.extension.
- Duplicate detection: hash of file content.
- If the same hash appears with a different filename, reject as duplicate.
- If the same file is imported twice, show an error explaining it is already imported.
- If two files have same title and artist but different duration, treat them as distinct songs.
- If a file has no metadata, fields are empty and can be filled through metadata editing.
- If a file has embedded album art, use it unless a higher-priority thumbnail exists.
- If file format is unsupported, show an error.
- If copy operation fails, show an error and provide a retry option.
- If metadata extraction fails but the file imports successfully, import the file with empty metadata and inform the user.
- User should see import progress.
- Failed imports should be listed after the import attempt.

After successful import, show an import result screen with actions:

- Play imported songs.
- Add imported songs to playlist.
- View in library, going to Home and highlighting the imported songs.

Import result screen groups items by:

- Success
- Duplicate
- Failed

### YouTube search/import

YouTube search allows the user to search a video and see the five closest related results.

YouTube result cards show:

- Thumbnail
- Title
- Channel/artist
- Duration
- Download button

YouTube-imported songs store:

- Original YouTube video ID.
- Original YouTube URL.
- Available metadata from the API.
- YouTube thumbnail, unless overridden by custom thumbnail.

YouTube-imported songs are editable exactly like local imports.

YouTube duplicate detection:

- Same YouTube video ID = duplicate.
- Audio hash is used as backup.

YouTube downloads:

- Multiple downloads can run at once because the API can handle it.
- Failed downloads automatically retry 3 times.
- If download still fails after 3 retries, show the error reason and suggested action where possible.
- Failed YouTube downloads appear in Import History.
- Failed YouTube downloads have a Retry button in Import History.

### YouTube playlist import

Fenlzer supports importing YouTube playlists.

When a playlist URL is provided, the user can either:

- Download the whole playlist directly.
- Choose specific songs from the playlist to download.

### Import History

Import History is separate from Active Imports.

Import History includes:

- Successful local imports.
- Successful YouTube imports.
- Failed local imports.
- Failed YouTube downloads.
- Duplicate import attempts/results.

Import History is permanent until manually cleared.

- Clear Import History does not affect imported songs.
- It only clears history records.

## 9. Metadata, thumbnails, and Song Details

### Stored metadata

Each music entry stores metadata required for display, editing, playback, smart playlists, and statistics.

Metadata fields:

- Title
- Artist
- Album Artist
- Album
- Genre
- Year
- Track number
- Disc number
- Custom thumbnail
- Notes
- Source type: local / YouTube
- Original filename, for local imports
- YouTube video ID and URL, for YouTube imports
- Original metadata values for reset support
- Date imported
- Technical file details

### Metadata editing

Metadata is stored in the database and can be edited without necessarily changing the audio file itself.

Rules:

- Editing artist/title affects smart playlists immediately.
- Metadata edits are reversible by keeping original/default values.
- Metadata editor uses a Save button, not auto-save.
- Leaving editor with unsaved changes shows a “Discard changes?” warning.
- Reset field to original value is available per field.
- Changed fields should show a small edited indicator beside the reset control.
- Reset all metadata is available with confirmation.
- If an original field was empty, resetting it returns it to empty.
- Changing artist for multiple songs is available only through bulk selection mode.
- Changing thumbnail copies the image into app storage.
- Reset all metadata may include a checkbox asking whether to also reset the custom thumbnail.

### Thumbnail priority

Thumbnail priority:

1. Custom user thumbnail
2. YouTube thumbnail, if YouTube source
3. Embedded album art, if local source
4. Placeholder

### Song Details screen

Song Details is a dedicated screen/sheet.

Entry points:

- Song three-dot menu.
- Fullscreen player stats/details area.
- Import result screen.
- Mini-player three-dot menu.

Song Details includes:

- Title
- Artist
- Album Artist
- Album
- Genre
- Year
- Track number
- Disc number
- Notes
- Favourite state/date
- Basic Play button only
- Play count
- Skip count
- Completion count
- Total listened time
- Last played
- First played
- First imported
- Average completion percentage
- Appears in playlists
- Expandable Original Metadata section
- Expandable Technical Details section
- Expandable Source Information section for YouTube/API-backed songs

Song Details should not contain full playback controls beyond a basic Play button.

Original Metadata section shows the extracted/default metadata values separately from the current edited values.

Pressing Play in Song Details uses Home tap behavior: immediate override, old queue remains.

Technical Details section may include:

- Source type
- Original filename
- Internal stored filename only, not the full private app-storage path
- Content hash
- File format/container
- Codec
- File size
- Duration
- Bitrate/sample rate/channels if available

Source Information section for YouTube/API-backed songs includes:

- YouTube video ID
- Original YouTube URL
- Imported from Search / Discover / YouTube Playlist / automatic pending action
- Import date
- Requested download format
- Final audio format

## 10. Playback behavior and audio integration

### App startup and resume

If a song was previously playing:

- Restore last song.
- Restore last position.
- Restore queue.
- Do not autoplay unless playback was active before app was closed.

Fenlzer must support Android's media resume feature so the user can resume the last thing that was playing from the Android media controls.

### Audio focus

Audio focus behavior:

- Phone call / voice call: pause.
- Navigation prompt / short interruption: lower volume.
- Another media app starts: lower volume.

### Becoming noisy

When headphones or Bluetooth disconnect, Fenlzer pauses automatically.

### External media controls

Fenlzer supports:

- Play/pause headset button.
- Next/previous Bluetooth controls.
- Lock screen controls.
- Notification controls.
- Android resume.
- Android Auto.

### Android Auto V1 requirement

Android Auto support is required for V1. Fenlzer should provide a car-safe media browsing/playback experience compatible with Android Auto.

At minimum, Android Auto should expose:

- Library browsing.
- Playlists.
- Smart playlists where appropriate.
- Current playback controls.
- Queue/current media session state.

Final Android Auto browsing hierarchy is still to be determined.

### Sleep timer

Sleep timer options:

- 15 min
- 30 min
- 45 min
- 60 min
- End of current song
- End of queue
- Custom

When timer ends:

- Pause playback.
- Fade out during the last 10 seconds.

Sleep timer state should be visible more clearly in fullscreen player and accessible from mini/fullscreen more menus.

## 11. Statistics and listening history

### Playback event storage

Fenlzer stores each play of songs with:

- Time played at.
- Listening duration.
- Whether it became a valid listen.
- Completion information.
- If not listened to the end, timestamp/position where listening stopped.

Stats update only on:

- Song end.
- Song change.
- Pause.
- App close.

If the app crashes, Fenlzer should try to recover partial listening time on next launch by saving periodic temporary progress every few seconds.

Private mode prevents statistics updates and also avoids adding playback events to history. Notification and lock-screen playback still work.

### Valid listen

A valid listen is counted when:

listenedMs >= min(15 seconds, 50% of duration)

Examples:

- 10-second song: 5 seconds counts.
- 5-minute song: 15 seconds counts.

### Skip

A skip is counted if:

- User manually moves to another song before reaching the valid listen threshold.

### Completion

A completion is counted if:

- User listens to at least 90% of the song duration.

Skipping to the last 10 seconds and finishing does not count as completion unless the user actually listened to at least 90% of the duration.

### Playback sessions

Fenlzer stores PlaybackSession records explicitly. Sessions are used for longest listening session, streak-related analysis, and future statistics.

Session rules:

- A new playback session starts when any playback starts after no playback for 5+ minutes, even if that playback never becomes a valid listen.
- A session continues as long as gaps between playback activity are under 5 minutes.
- Paused time does not count toward listening time, but short pauses under the 5-minute gap threshold can still keep the session connected.
- Reset Statistics clears sessions.
- Clear Listening History clears sessions because sessions are part of listening history.

### Repeated playback

If Repeat One loops the same song five times, each loop creates a separate playback event.

### Listening time

- Total listened time counts real elapsed listening time.
- Paused time does not count.
- Background playback counts normally.
- Seeking around counts normally based on actual elapsed listening time.

### Song stats

Per-song stats:

- Play count
- Skip count
- Completion count
- Total listened time
- Last played
- First played
- Favourite date
- Average completion percentage

Average completion percentage is calculated from listened duration / song duration.

### Global stats

Global stats:

- Total listening time
- Total songs imported
- Total playlists
- Most listened song
- Most listened artist
- Most skipped song
- Favourite artist
- Songs never played
- Listening time by day
- Listening time by hour
- Listening streak
- Longest listening session
- Recently rediscovered songs

Listening streak day:

- At least one valid listen.

Recently rediscovered songs:

- A song not played for at least 20 days.
- Then played again within the last 14 days.

## 12. Settings

Settings opens from the top-right gear icon on Home.

Settings include:

- Default repeat mode.
- Default shuffle mode.
- Default Home sorting.
- Import duplicate behavior.
- Delete confirmation on/off.
- Sleep timer default duration.
- Private mode.
- Clear listening history.
- Reset statistics.
- Storage usage / Storage Management.
- AMOLED mode.
- App version.
- About Fenlzer.

### Private mode

Private mode:

- Manual toggle only.
- Can be enabled only for the duration of the current application session.
- Controlled from Settings only.
- Shows indicator in mini-player/fullscreen player.
- Prevents stats updates.
- Prevents playback event history creation.

### Repeat and shuffle settings

- Default repeat mode: Repeat all.
- Changing default repeat mode in Settings immediately affects the current player.
- Default shuffle mode applies to new queues only.
- Save shuffle state in settings.
- Save last repeat mode in settings.

### Clear listening history

Clear listening history clears playback event history, derived Recently Played, and stored PlaybackSession records. It keeps aggregate song statistics so it remains distinct from Reset Statistics.

### Reset statistics

Reset statistics should reset:

- All global stats.
- All song stats.
- Listening history.
- Derived Recently Played.
- Stored PlaybackSession records.

Reset statistics should keep imported time/date.

### Storage Management

Storage Management shows:

- Audio files size.
- Thumbnail size.
- Cache size.
- Database size.
- Total Fenlzer storage usage.

Storage Management actions:

- Clear cache.
- Clear import history.
- Delete all songs from Fenlzer.

Delete all songs from Fenlzer is allowed but must be behind a strong confirmation requiring the user to type DELETE.

Clearing cache does not remove thumbnails.

## 13. Delete behavior

Delete Song / Delete from Fenlzer removes:

- Copied audio file from Fenlzer app storage.
- Custom thumbnail file, if any.
- Song entry from database.
- Playlist references.
- Queue references.

There must be a confirmation dialog using the phrasing:

Delete from Fenlzer

Delete undo:

- No undo.
- No internal Trash.
- Permanent delete after confirmation.

Even if delete confirmation is disabled, destructive bulk delete still requires confirmation.

## 14. Theme and visual direction

### Theme

- Dark only.
- Base background: dark gray / near-black.
- AMOLED mode included now.
- AMOLED mode uses pure black for main background and player background.
- Dark purple accent/theme color.
- Exact HEX code will be provided later.
- Multiple themes may be supported later.

### Visual hierarchy principles

- Song title is more important than artist.
- Artist uses a subtler gray style.
- Queue rows and song rows should remain compact enough for good visibility.
- Landscape mode should avoid large persistent bars consuming too much vertical space.

## 15. Data model notes

This is not final implementation schema, but the app clearly needs database support for songs, playlists, queue, playback events, imports, metadata originals, and settings.

### Track/Song

Likely fields:

- trackId
- title
- artist
- albumArtist
- album
- genre
- year
- trackNumber
- discNumber
- notes
- durationMs
- sourceType
- originalFilename
- storedFilename
- contentHash
- youtubeVideoId
- youtubeUrl
- thumbnail paths/references
- original metadata fields
- normalized title/artist/album/albumArtist sort keys
- dateImported
- isFavourite
- favouritedAt
- technical details

### Playlist

Likely fields:

- playlistId
- name
- customThumbnailPath
- createdAt
- modifiedAt
- type regular/smart if needed

### Playlist item

Regular playlists prevent duplicates.

Likely fields:

- playlistId
- trackId
- position
- addedAt

### Queue item

Even though duplicate songs are forbidden in the queue, queue items need unique IDs for ordering and mutation.

Likely fields:

- queueItemId
- trackId
- position
- insertedBy
- addedAt

### Playback event

Likely fields:

- eventId
- trackId
- startedAt
- endedAt
- listenedMs
- startPositionMs
- endPositionMs
- validListen
- skipped
- completed
- completionPercentage
- privateModeExcluded flag or exclusion by omission

### Playback session

Fenlzer explicitly stores playback sessions instead of deriving them only on demand. Sessions are used for longest listening session and future session-based statistics. A new session starts when any playback starts after no playback for 5+ minutes, even if that playback never becomes a valid listen. A session remains continuous when gaps between playback are under 5 minutes. Clear Listening History and Reset Statistics both clear stored sessions.

Likely fields:

- sessionId
- startedAt
- endedAt
- totalListenedMs
- eventCount
- privateModeExcluded flag or exclusion by omission

### Discover snapshot

Discover is stored as a persistent snapshot so the list survives app restarts and only changes on manual refresh or eligible startup refresh.

Likely fields:

- discoverSnapshotId
- generatedAt
- lastOpenedAt
- normalOrBroaderRefresh
- candidatesRequested
- candidatesReceived
- alreadyImportedFilteredCount
- unavailableFilteredCount
- finalDisplayedCount
- refreshBroaderSuggested
- remote item list/order

### Import history item

Likely fields:

- importHistoryId
- sourceType
- title/displayName
- originalUri/url
- youtubeVideoId
- status success/duplicate/failed/cancelled
- errorMessage
- startedAt
- completedAt
- resultingTrackId if any

### API diagnostics retention

The advanced API diagnostics/logs screen keeps the last 500 sanitized diagnostic entries. Entries include request ID, endpoint, method, status, timing, stable error code, and sanitized technical details. Static tokens/API keys are never shown.

## 16. Final implementation follow-ups

The product behavior is now considered settled enough to draft the final application plan. The remaining items are implementation/design-production follow-ups rather than product-direction questions.

| Area | Follow-up detail | Current direction |
|---|---|---|
| Theme | Exact dark purple HEX code | User will provide |
| Visual design | Final spacing, typography scale, row density, and component sizes | Tune during UI layout/implementation using the settled layouts |
| UI copy | Exact wording for errors, confirmations, pending states, and empty states | Dedicated UI copy pass during implementation; behavior is settled |
| Database | Exact Room schema, indexes, migrations, and entity relationships | Preserve all user data through migrations; schema should follow this plan |
| API | Implementation-ready request/response examples for every endpoint | Refine in order: Health -> Search -> Playlist Preview -> Downloads/Jobs -> Streaming -> Discover |
| API | Exact yt-dlp metadata mapping and supported output formats in practice | Default M4A/AAC; fallback to best available when needed |
| Discover | Exact recommendation algorithm and broader-refresh scoring | API filters imported songs and returns up to 25 valid remote recommendations |
| Android Auto | Platform-specific behavior testing for voice search and media controls | Use downloaded-only browse tree; Now Playing may control current remote stream if already playing |
| Technical metadata | Exact codec/container fields available per format/device | Show what can be extracted reliably |
| Theme system | Multiple themes beyond AMOLED/dark purple | Dark + AMOLED now, multiple themes later |

## 17. Finalization status

This document is the final complete Fenlzer application plan draft. The remaining work is implementation planning: exact Room schema, final API endpoint contracts, UI copy, theme HEX values, and platform-specific testing.
