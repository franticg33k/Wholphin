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

| Key | Full screen | Guide |
|---|---|---|
| Up / Down, Channel +/- | Change channel | Move between channels |
| 0–9 | Tune by number (`1 0 _ _`; red when no channel has it) | |
| OK | Open the guide | On the programme airing now: watch it. On a later one: say when it starts |
| Left / Right / Info | Show the overlay | Move through programmes; the grid pages in half hours |
| Back | Open the guide | Leave TV mode |
| Long press Back, Last | Previous channel | |
| Play/Pause | Pause (a "broadcast paused" screensaver); any key rejoins live | |
| Guide / Menu | Open the guide | Close the guide |

## Screens

Inspired by NostalgiaTV, but written from scratch: none of its code or assets are used. The layout follows
classic cable-guide apps in behaviour and layout only; every asset is original or openly licensed.

- **Guide:** a top bar (Watch, Settings, Exit); an info panel with the focused programme's clear logo or title, the
  `S1E10 - Episode` line in the accent colour, times, channel, chips (air date, rating, quality, audio, runtime, NEW,
  community rating) and overview, over a faint backdrop; the live channel in a preview window; category tabs; a time
  row with a clock, half-hour columns and a "now" marker and line; and the grid, whose cells are as wide as the
  programmes are long, coloured by type (show, movie, kids), with a progress underline on what's airing, rating pills
  and dimmed ended programmes. Details come from Jellyfin's item API when focus rests on a programme.
- **Search** (guide top bar): type a channel name or number, or a title that's on now; OK tunes.
- **Sleep timer** (guide top bar): off, 15, 30, 60, 90 or 120 minutes; TV mode closes then.
- **Overlays:** *Satellite* is a receiver-style banner across the top (date, now with times and progress, next).
  *Classic* puts the channel number and logo in the top-right corner and now / coming up (with show
  logos), a timeline and media info bubbles along the bottom. *Lineup* is a bottom bar with the channel, what's on,
  the next programmes and a clock.
- **Tuning screens:** static, colour bars or a "please stand by" card, optionally with the channel logo. A gap in
  the schedule shows a break screen with an "UP NEXT IN m:ss" countdown instead.
- **On the picture:** the channel logo in a corner (always or every five minutes, with position and opacity), a rating
  badge when a programme starts, "Our feature presentation" before movies, and an up-next card in a programme's last
  minute.
- **CRT:** scanlines, corner shading, and 4:3 picture modes: cropped, letterboxed, or inside a drawn CRT set.
- **Idle:** after a set number of hours without a key press, "Still watching?" asks for a minute and then signs off
  (stops streaming) until a key is pressed.
- **Themes:** Retro cable (navy, pastel cells, yellow focus, Share Tech Mono), Midnight, Phosphor and Modern, plus
  your own: Settings → Edit themes starts a copy of the current theme, and every colour role (background, bars, cells
  by type, focus, text, time marker) can be set from swatches or a hex code. Custom themes are kept on the device.
- **Interface sounds:** optional soft clicks for moving, selecting and changing channel (generated, no audio files).
- **Branding:** the guide's top bar shows the service name set in the plugin (default "Cable TV").
- **Settings** (Settings in the guide, kept on the device): theme, accent and focus colours, logo on screen, rating
  badge, feature presentation, up next, break screens, picture mode, scanlines, corner shading, idle sign-off, overlay, tuning screen and logo, channel column
  (numbers, logos, both), detailed two-line cells, colour by type, dim ended, time line, 24-hour clock, media info.
- **Special channels** (set up in the plugin):
  - *Stream* channels play an HLS/HTTP stream URL directly (a news feed, a webcam); the guide shows them as live.
  - *Weather* channels show a local forecast drawn in the app from the plugin's `/CableTv/Weather/{id}`: current
    conditions, the next hours and the week ahead, rotating every 10 seconds and refreshed every 10 minutes.
  - *Music* channels (audio items or artists) play the audio stream with a now-playing screen: cover art, title,
    artist, album and an animated level meter.
  - *Trailer* channels show a "Coming attractions" card naming the channel where the film airs; OK tunes there while
    it is on.
- Channel logos come from the plugin (`logoUrl`); categories from each channel's plugin setting.
- Video keeps its aspect ratio (pixel aspect included) instead of stretching.

## How playback follows the schedule

- The clock is the server's (`serverTime` on every response, kept from the lowest-latency sample), never the device's.
- A channel change plans from the cached schedule: the airing slot's item is queued with a clipping range (its in and
  out points) and started at the offset; the next items are queued behind it. Filler and off-air stretches show static.
- Stream channels skip planning: the stream URL is played live. Audio slots use the audio stream endpoint.
- The queue is topped up on every item transition; playback that drifts more than 5 s from the schedule is re-tuned.
- The schedule is fetched 6 hours ahead and refreshed when less than 2 remain; a changed `scheduleVersion` drops the
  channel's cache.

## Tests

```sh
./gradlew :tvmode-core:test
```

Screenshots of the guide (every theme), the overlays and a tuning screen, rendered with sample data to
`tvmode/build/screenshots/`:

```sh
./gradlew :tvmode:testDebugUnitTest --tests '*TvModeScreenshots*'
```

The guide font is Share Tech Mono (SIL Open Font License, `tvmode/ShareTechMono-OFL.txt`).
