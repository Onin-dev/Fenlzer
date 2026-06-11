# Fenlzer Phase 15 Regression Checklist

This checklist belongs to the final adaptive layout, polish, and V1 regression phase.
Run it after applying Phase 15 patches and before considering the app ready for daily use.

## Build checks

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew connectedDebugAndroidTest` on an emulator/device when available

## Rotation checks

Run each screen in portrait and landscape:

- [ ] Home — search/options remain reachable; song rows remain dense enough; artists/albums are usable.
- [ ] Playlists — smart playlists and regular playlists remain visible; playlist detail is usable.
- [ ] Import — YouTube search, playlist preview, active imports, and import history remain reachable.
- [ ] Discover — refresh, broader refresh, stream/import buttons, and empty/error states remain usable.
- [ ] Queue — current/upcoming rows remain visible; remove/jump actions remain reachable.
- [ ] Fullscreen player — artwork and controls remain reachable without vertical overflow.
- [ ] Settings — all controls, storage management, destructive confirmations, and diagnostics remain reachable.
- [ ] API Diagnostics — All/Failed/Success filters and clear action remain reachable.
- [ ] Statistics — stat cards and recent history remain readable.

## Offline behavior

- [ ] Disable API / use invalid URL.
- [ ] Home library remains usable.
- [ ] Local playback remains usable.
- [ ] Settings test connection shows a safe failure.
- [ ] Diagnostics records a sanitized failure without tokens.
- [ ] API-backed features fail gracefully without breaking local features.

## Process death / restart behavior

- [ ] Start playback, kill app process, reopen; queue and position restore as specified.
- [ ] Start an active import, kill app process, reopen; import state is recoverable or safely marked needs attention.
- [ ] Rotate while playback is active; mini-player/fullscreen state remains coherent.
- [ ] Toggle private mode and restart; private mode does not persist.

## Destructive action checks

- [ ] Clear cache does not remove permanent thumbnails or audio tracks.
- [ ] Clear import history keeps imported songs.
- [ ] Delete one song removes file, playlist refs, queue refs, and stats references safely.
- [ ] Delete all songs requires typed `DELETE` and returns app to empty states.

## Privacy checks

- [ ] Diagnostics never show API tokens.
- [ ] Diagnostics never show Authorization headers.
- [ ] Diagnostics never show full direct YouTube media URLs.
- [ ] Private-mode playback creates no stats/history and is not uploaded for Discover.

## Android Auto checks

- [ ] Browse local library.
- [ ] Browse regular playlists.
- [ ] Browse allowed smart playlists only.
- [ ] Discover/remote items do not appear in browse.
- [ ] Current remote playback remains controllable if already playing on phone.
