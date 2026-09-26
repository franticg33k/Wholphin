# Cable TV mode

A full-screen TV for channels scheduled by the [Cable TV Jellyfin plugin](https://github.com/franticg33k/jellyfin-cable-tv).
Tuning is instant and needs no server transcoding: the app keeps the schedule, works out what airs now and how far
into it, and direct-plays the original file from that point, with the next items queued so they preload.

## Modules

| Module | What it holds |
|---|---|
| `:tvmode-core` | Plain Kotlin, unit tested on the JVM: plugin API models and client, server clock, schedule cache, tune-in and queue planning, channel navigation, guide grouping |
| `:tvmode` | Android: `CableTvScreen` (player, static overlay, channel banner, guide), `CableTvViewModel`, `TvModeHost`, `CableTvAvailability` |

The app touches very little so rebasing on upstream stays easy:

- `WholphinTvModeHost` implements `TvModeHost` (signed-in API client, player factory, navigation), bound in `TvModeModule`.
- `Destination.CableTv` and its branch in `DestinationContent`.
- A *Cable TV* nav drawer item, shown only when the server's plugin answers (`CableTvAvailability`). It takes part in
  upstream's ordering: move or hide it under Settings → Customize Navigation Drawer Items.

## Remote keys

| Key | Action |
|---|---|
| Up / Down, Channel +/- | Change channel |
| 0–9 | Tune by number (completes at full length or after 2 s) |
| OK / Guide / Menu | Open or close the guide |
| Left / Right / Info | Show the channel banner |
| Last | Previous channel |
| Back | Close the guide, or leave TV mode |

## Guide and banner

- Channel logos come from the plugin (`logoUrl`, served by `/CableTv/Logo/{id}` without authentication) and show in
  the banner and the guide. Channels without one show only their number and name.
- When channels have a category (set per channel in the plugin), the guide shows category chips above the grid:
  press Up from the top row to reach them and pick one to filter the guide; *All* shows every channel.
- The line above the guide describes the focused programme. OK on the programme airing now tunes to it; OK on a
  later one only says when it starts, since a channel can't be watched ahead of its schedule.
- Video keeps its aspect ratio (pixel aspect included): 4:3 and widescreen shows get black bars rather than being
  stretched.

## How playback follows the schedule

- The clock is the server's (`serverTime` on every response, kept from the lowest-latency sample), never the device's.
- A channel change plans from the cached schedule: the airing slot's item is queued with a clipping range (its in and
  out points) and started at the offset; the next items are queued behind it. Filler and off-air stretches show static.
- The queue is topped up on every item transition; playback that drifts more than 5 s from the schedule is re-tuned.
- The schedule is fetched 6 hours ahead and refreshed when less than 2 remain; a changed `scheduleVersion` drops the
  channel's cache.

## Tests

```sh
./gradlew :tvmode-core:test
```
