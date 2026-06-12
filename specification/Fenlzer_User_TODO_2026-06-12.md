# Fenlzer User TODO — 2026-06-12

This checklist was provided as the next implementation backlog after Phase 21.

## Songs

- [ ] Make thumbnails render cropped to fit square thumbnail content and playlist/album covers.
- [ ] Reduce song-row/layout height so more songs fit on screen.
- [ ] Prevent streamed playback from restarting when the same song finishes downloading/importing.

## Fullscreen player

- [ ] Add opening and closing animations.
- [ ] Add visual effect on the cover when swiping right/left, with cancel support.
- [ ] Remove the Repeat and Shuffle lines under Next song.
- [ ] Make the fullscreen player actually fullscreen and non-scrollable; everything must fit at once.

## Mini player

- [ ] Make the mini-player floating instead of an attached bar.
- [ ] Add swipe right/left to previous/skip with visual effects and cancel support.
- [ ] Put the play/pause button instead of the thumbnail.
- [ ] Make the background use the average color of the thumbnail.
- [ ] Swipe up on the mini-player to open fullscreen player.
- [ ] Merge the seek bar with the mini-player so progress creates a purple overlay over the background while seeking remains possible.

## Home tab

- [ ] Display duration at the far right of the artist row so there is more space for the title.
- [ ] Song cards must take the whole width instead of being inside a boxed card.
- [ ] Landscape mode: hide/remove filters.
- [ ] While in Settings or Statistics, pressing Home in the navigation bar must return to the Home tab.

## Import tab

- [ ] Add separate subsections: Import From Device, Download From YouTube, Import YouTube Playlist, Active Imports, Import History.
- [ ] Make Active Imports and Import History persistent subtabs.
- [ ] Passing a URL to the YouTube search bar should resolve only that song and return metadata as a single downloadable search result.

## Notification

- [ ] Add the Favourite button.
- [ ] Make the background image full-scale instead of pixelated.
- [ ] Pressing the notification body, other than buttons, should open the application.

## Search bars

- [ ] Add a clear X button to quickly empty search bars.

## Splash screen

- [ ] Turn the splash screen black.

## Loading state

- [ ] Add animated loading placeholders wherever things are loading, including Home songs and mini-player.

## Animations

- [ ] Add opening and closing animations for mini-player to fullscreen player.
- [ ] Add a transition on the mini-player when changing from one song to another.

## Shuffle

- [ ] While shuffle is on, it should not only shuffle the queue at creation; shuffle behavior must remain correct after each song.
- [ ] If the user turns shuffle on while a queue is already playing, shuffle the queue while keeping playback on the same song.
