# Wholphin Cable TV

A fork of [Wholphin](https://github.com/damontecres/Wholphin), the Android TV client for Jellyfin, that adds a
**Cable TV mode**: always-on channels from your own library with a retro channel guide, channel surfing and
commercial breaks. It needs the
**[Cable TV Jellyfin plugin](https://github.com/franticg33k/jellyfin-cable-tv)** on your server; everything else is
regular Wholphin.

## Install

1. Install the [Cable TV plugin](https://github.com/franticg33k/jellyfin-cable-tv#install) on your Jellyfin server and
   set up some channels.
2. Download the app from the [latest release](https://github.com/franticg33k/Wholphin/releases/latest):
   `Wholphin-release-arm64-v8a.apk` for most Android TV boxes, Google TV and Shield, `Wholphin-release-armeabi-v7a.apk`
   for older 32-bit devices, or `Wholphin.apk` if unsure.
3. Sideload it. It installs as **Wholphin Cable TV**, next to the regular Wholphin app if you have it, and offers
   new builds from this repository as in-app updates.
4. Sign in to your server; *Cable TV* appears in the navigation drawer when the plugin is installed.

## What's in Cable TV mode

- **Instant tuning**: the app direct-plays the scheduled file from the right point, with no server transcoding.
- **Channel guide**: a cable-style grid with categories, logos, programme details and search.
- **Overlays**: channel banners, static and colour-bar tuning screens, and on-screen logos. Themes include a
  theme editor.
- **Special channels**: live stream channels, a local weather channel, music channels with a now-playing screen,
  and "coming attractions" trailer channels.
- **Remote-first**: channel up/down, number entry, last channel, a sleep timer and a pause screen.

Details: [TV mode README](https://github.com/franticg33k/Wholphin/blob/cable-tv/tvmode/README.md).

## This fork

- The product branch is [`cable-tv`](https://github.com/franticg33k/Wholphin/tree/cable-tv). `main` mirrors upstream.
- The Cable TV code lives in its own modules (`:tvmode`, `:tvmode-core`), and only a few upstream lines change, so
  upstream releases merge in easily. See [FORK.md](https://github.com/franticg33k/Wholphin/blob/cable-tv/FORK.md).
- Everything else, including features, bugs and translations, is upstream Wholphin: see
  [its README](https://github.com/franticg33k/Wholphin/blob/cable-tv/README.md) and
  [project](https://github.com/damontecres/Wholphin). Please report Cable TV issues here, not upstream.

Inspired by NostalgiaTV, but written from scratch: none of its code or assets are used.

## License

Same as upstream Wholphin: see [LICENSE](https://github.com/franticg33k/Wholphin/blob/cable-tv/LICENSE).
