# винИИла

An Android "vinyl turntable" that shows whatever you're playing in *any* other music app: the cover becomes the record
label, the record colour/style is derived from the cover, the tonearm tracks playback.

| Gesture | Action |
|---|---|
| Tap the record centre | play / pause |
| Drag the tonearm head | seek (lifting it pauses, putting it down plays) |
| Swipe left / right | next / previous track |
| Tap the tonearm pivot | settings |

Settings: disc rotation speed (25–200 %, 100 % = original), hide the status bar, theme (light / dark / system).

## Install
1. Download `vinyl-player.apk` from the **Latest build** release (Releases page) or from the latest workflow run's
   artifacts, and install it (allow installs from your browser/file manager).
2. Open the app → **Open settings** → enable *винИИла* under notification access.
   On Android 13+ you may first need *Settings → Apps → винИИла → ⋮ → Allow restricted settings*.
3. Start music in any player (Spotify, YouTube Music, Yandex Music, VK Music, local players, …).

## Build
`./gradlew assembleDebug` (JDK 17 + Android SDK 34) — or just push; GitHub Actions builds both APKs.

Project notes, architecture and backlog for contributors/agents: [AGENTS.md](AGENTS.md). Requirements: [docs/SPEC.md](docs/SPEC.md).
