# Fenlzer Phase 16 — Queue and Playlist Manual Editing Completion

## Implemented in this phase

- Queue drag-step reordering from the visible drag handle.
- Current queue item remains fixed as the current song and cannot itself be dragged.
- Previous and upcoming queue items can be moved across the current item while the current song identity remains unchanged.
- Queue source is marked modified after manual reordering or queue shuffle actions.
- Queue menu now supports shuffle whole queue and shuffle upcoming songs.
- Queue can be saved as a regular playlist using only local/imported tracks.
- Queue left-to-right swipe removal now shows an Undo snackbar for non-current rows before committing removal.
- Current-song removal still requires confirmation and skips/stops through the existing playback controller behavior.
- Regular playlist detail rows now expose the same drag-step handle for manual ordering when the playlist is in manual order with no active search.
- Smart playlist save-as-regular was already implemented and was intentionally not changed.

## Manual tests

1. Open Queue with at least three songs.
2. Drag a non-current row handle upward and downward; verify order changes and persists after closing/reopening Queue.
3. Try dragging the current row; it should not move.
4. Drag a previous row downward past the current song; current remains the current song.
5. Swipe a non-current row left-to-right; tap Undo and verify it remains.
6. Swipe a non-current row and do not undo; verify it is removed.
7. Swipe the current row; verify confirmation appears and removing it skips/stops according to queue contents.
8. Use Queue actions -> Shuffle whole queue; verify current stays current and other rows reorder.
9. Use Queue actions -> Shuffle upcoming; verify previous/current prefix remains and upcoming order changes.
10. Save Queue as playlist; verify only local/imported songs appear in the created playlist.
11. Open a regular playlist in manual order with no search and drag row handles; verify order persists after leaving/reopening.
12. Enable playlist search or non-manual sort; verify reorder is disabled.

## Automated checks

Run:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Then, with device/emulator:

```bash
./gradlew connectedDebugAndroidTest
```
