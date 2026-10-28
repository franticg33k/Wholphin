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
| Back | Open the guide | Back to full screen (Settings → *Back closes TV mode* makes it leave instead) |
| (guide button) ✕ CLOSE | | Leave TV mode, back to where you came from |
| Long press Back, Last | Previous channel | |
| Play/Pause | Pause (a "broadcast paused" screensaver); any key rejoins live | |
| CC (Captions) | Subtitles: Off, On, Auto | |
| A, audio-track key | Pick audio and subtitle tracks | |
| `*` (or yellow) | Mark this channel as a favourite, or clear it | Same, for the channel in focus (holding OK on a programme does too) |
| `#` (or blue) | Channel up/down: favourites only, or all channels | |
| Guide / Menu | Open the guide | Close the guide |

## Screens

Inspired by NostalgiaTV, but written from scratch: none of its code or assets are used. The layout follows
classic cable-guide apps in behaviour and layout only; every asset is original or openly licensed.

- **Guide:** a top bar (Search, Audio & subs, Sleep, Settings, ✕ Close; **Back** or **Menu** jumps up to it from anywhere in the grid, and Back again returns to full screen); an info panel with the focused programme's clear logo or title, the
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
- **On the picture:** the channel logo in a corner (always or every five minutes, with position, size and opacity), a rating
  badge when a programme starts, "Our feature presentation" before movies, and an up-next card in a programme's last
  minute.
- **CRT:** scanlines, corner shading, and 4:3 picture modes: cropped, letterboxed, or inside a drawn CRT set.
- **Idle:** after a set number of hours without a key press, "Still watching?" asks for a minute and then signs off
  (stops streaming) until a key is pressed.
- **Themes:** Retro cable (navy, pastel cells, yellow focus, Share Tech Mono), Midnight, Phosphor and Modern, plus
  your own: Settings → Edit themes starts a copy of the current theme, and every colour role (background, bars, cells
  by type, focus, text, time marker) can be set from swatches or a hex code. Custom themes are kept on the device.
- **Audio and subtitles:** press **A** (or the remote's audio-track key, or *AUDIO & SUBS* in the guide's top bar) to
  pick the audio and subtitle track of what is playing; the choice lasts for that programme.
  - *Subtitles* (Settings, or the CC key) has three modes: **Off**, **On**, and **Auto**, the default, which shows
    subtitles only when the audio isn't in your language (foreign films, anime) and keeps them off otherwise.
  - *Audio language* (Settings) picks the audio track: **File default** (usually the original language; the default),
    the device language, or a language.
  - Subtitles use the track in your *Subtitle language* ("Device language" by default), or the first full track if none
    matches. Embedded tracks (SRT, ASS with styling, PGS) and subtitle files next to the video both work, drawn in
    Android's caption style.
- **Favourites:** mark channels with `*` (or yellow, or hold OK on a programme in the guide). Favourites show a ★ in
  the guide and get their own **★ Favourites** tab. `#` (or Settings → *Channel up/down: favourites only*) makes
  channel up/down visit only the favourites; typing a number or using the guide still reaches every channel. They are
  kept on the device.
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

## Screensaver

Settings → *Screensaver* can be **Cable TV channel surfing**: instead of backdrops and posters, the screensaver plays
Cable TV with the sound off and changes channel every 15 seconds to 5 minutes (*Change channel every*). It picks a
channel at random (your favourite channels only, when you have marked some), joins it at its scheduled point as the TV
mode does, and shows the channel number, logo and programme in a corner for a few seconds after each change.

- It replaces the content of both screensavers: the one the app shows when idle, and the system screensaver (Android's
  *Daydream*) if you chose Wholphin there. Turn the screensaver on as usual in the app's own settings; this only changes
  what it shows.
- With no Cable TV channels (no plugin, or nothing to play) it falls back to the normal screensaver.

## Home screen row

On Android TV and Google TV the app adds an **On now on Cable TV** row to the launcher's home screen: one tile per
channel (up to 12) with what it is airing, its time and what is next. Picking a tile opens the TV mode already tuned
to that channel (`wholphin://cabletv?channel=<id>`; `wholphin://cabletv` opens the TV mode itself).

- The row is titled **On now on <service name>**, using the service name set in the plugin's settings (default "Cable
  TV"), so renaming the service there renames the row at the next refresh.
- The row refreshes every 15 minutes in the background, and when the app starts. It uses Android's TV provider (the
  same mechanism as the app's own "Recently added" row), so Android asks you once to let it show on the home screen.
  Home screens that don't support preview rows ignore it.
- Turn it off in the TV mode settings (*Home screen row*), or hide it in the launcher's own settings.
- **Row status:** Settings → *Home screen row* shows how the row is doing ("12 tiles, 8:30 PM", or why it couldn't be
  filled). Selecting it refreshes the row, and if Android hasn't been allowed to show it yet, opens Android's prompt for
  that (the prompt needs the app in front, so it can't appear from the background).
- **In the app's own Live TV rows:** Cable TV channels (and programmes on them) listed in Wholphin's Live TV rows on the
  home page open the TV mode, tuned to that channel, instead of Jellyfin's plain Live TV player.
- **Cable TV instead of Live TV on the home page:** the app's own home page has a Live TV row ("Watch live", and the
  channels row). By default it now lists only the Cable TV channels, named after the service ("Cable TV"), instead of
  every Live TV channel on the server. Settings → *Live TV row on the app's home page* switches between *Cable TV
  channels only* and *All Live TV*. On a server without Cable TV the row is the normal Live TV row either way.
- The row is only taken off the home screen when the server has no Cable TV channels, the plugin is missing, or you
  turn it off. A failed refresh (offline, signed out, server busy) or a moment with every channel off the air leaves
  the last tiles in place. Refreshes are logged under the tag `CableTvHomeRow`.
- Opening a tile from a cold start needs *Sign in automatically* turned on, the same as every other launch from the
  home screen.

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
