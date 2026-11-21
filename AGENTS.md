# AGENTS.md — handoff notes for any agent/human continuing this project

Read this first. Keep it up to date: when you finish a piece of work, update **Status** and **Backlog**.

## What this is
Android app (Kotlin + Jetpack Compose) that imitates a vinyl turntable. It detects what *other* apps are playing
(via their MediaSession, which requires notification-listener access), puts the cover onto the record label and
shows title/artist below. Owner's original request (Russian) and requirements: [`docs/SPEC.md`](docs/SPEC.md).
Visual reference: [`docs/reference/`](docs/reference) (`screenshot-playing.jpg`, `screenshot-paused.jpg`) —
the layout/proportions in `ui/Geo.kt` were measured from these (720x1560 px).

## Status (update me)
- **Builds locally and runs** (checked 2026-11-21 on the `reusable/` toolchain + its android-30 emulator):
  `assembleDebug` and `assembleRelease` are green, the APK installs, launches, draws the record and the
  "Allow notification access" / "Nothing playing" states, and the pill opens the system Notification access
  screen. No `FATAL` in logcat. First-run compile/visual fixes were applied: the patched
  `getEnabledListenerPackages` call (was `...PackageNames`), the record bitmap mask (the `DST_IN` circle never
  cut the square's black corners — `VinylRenderer.clearOutsideCircle` now does), and the record is drawn with
  `drawImage` on a full-screen canvas at absolute `geo` coordinates (a laid-out child wider than the screen got
  clamped/re-centred, so the visible disc centre did not match `geo.cx/cy`; gestures and the title use `geo`).
- **Still unverified: anything that needs a real MediaSession** — spin, tonearm follow/drag, tap-to-pause, swipe
  next/previous. There is no player app on the emulator, so install on a phone and grant notification access.
- Implemented: media-session tracking, record rendering (6 styles), spin, tonearm, all gestures, swipe animation,
  permission prompt, adaptive icon, CI.
- Not implemented: see Backlog.

## Build
- CI: `.github/workflows/build.yml` → `./gradlew assembleDebug assembleRelease`; artifacts `vinyl-player.apk`
  (release build, minify off) and `vinyl-player-debug.apk`. On push to `main` it also (re)creates the pre-release
  tagged **`latest`** with both APKs; on `v*` tags it creates a normal release.
- Local: JDK 17 + Android SDK (platform 34), `./gradlew assembleDebug`. Without a system JDK/SDK use the bundled
  `reusable/` set: `source reusable/env.sh` (JDK 17, SDK, Gradle cache, AVD), put `sdk.dir` in `local.properties`
  (`reusable/setup.sh <project>` writes it), install platform 34 once with
  `sdkmanager "platforms;android-34"`, then `./gradlew assembleDebug` (the wrapper pulls Gradle 8.9 once;
  dependencies come from the network, the `reusable/gradle-home` cache only covers the old project).
- Toolchain pins (known-good set; bump together): Gradle 8.9, AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14,
  Compose BOM 2024.06.00, compileSdk/targetSdk 34, minSdk 26, Java 17.
- Signing: a **committed debug keystore** (`app/debug.keystore`, pw `android`, alias `androiddebugkey`) signs both
  build types so APKs from different CI runs update in place. Personal-use only; for a store release create a real
  keystore and move it to GitHub secrets.

## Architecture (package `app.vinylplayer`)
```
media/NowPlayingService.kt  NotificationListenerService. Exists only to hold notification access; on connect it
                            registers an OnActiveSessionsChangedListener and feeds NowPlayingRepo.
media/NowPlayingRepo.kt     Singleton. StateFlow<NowPlaying> + commands (play/pause/toggle/next/previous/seekTo).
                            Picks the session (playing one first, else sticky current). Registers a callback on
                            ALL active sessions so a paused->playing change in a non-current session is noticed.
                            Loads cover from bitmap keys or from *_URI (content:// or https://). Commands apply an
                            optimistic state update; the player's callback confirms/corrects it.
media/NowPlaying.kt         Snapshot + position interpolation (positionNow()/progressNow()).
MainActivity.kt             Edge-to-edge host; checks notification access in onResume, calls requestRebind().
ui/VinylScreen.kt           The whole screen: state machine, gestures, animations (see below).
ui/Geo.kt                   Geometry in px derived from screen size; tonearm angle<->position maths.
ui/VinylDesign.kt           VinylStyle enum + VinylDesigner: cover -> (style, base colour, accent), deterministic
                            per track key via Palette. THIS is the "collection of records" extension point.
ui/VinylRenderer.kt         Renders record (surface/grooves/rim/label+cover) into one Bitmap per track; the UI
                            only rotates that bitmap on the GPU. Shadow and sheen are drawn separately (they must
                            not rotate).
ui/TonearmPainter.kt        Tonearm + housing, android.graphics canvas, blur shadow that grows when lifted.
```

### Behaviour contract (from the owner) and where it lives (`ui/VinylScreen.kt`)
| Requirement | Implementation |
|---|---|
| Tap record centre = play/pause | pointerInput: tap within `geo.labelR` of the record centre → `NowPlayingRepo.toggle()` |
| Drag the head = seek | head hit-test (0.13·W around stylus) → `beginArm/updateArm/endArm`; angle ↔ progress via `Geo.thetaOuter..thetaInner` |
| Head moves while playing | arm effect: `angleForProgress(progressNow())` every frame (position is interpolated from the last PlaybackState) |
| Pause lifts head / lifting head pauses | arm effect parks (angle 0, lift 1) when not playing; `beginArm` calls `pause()`; releasing the head on the record = `seekTo` + `play()`, releasing it off the record stays paused |
| Swipe left/right = next/previous with animation | horizontal drag moves record+text (`swipeX`); release past 20 % W or fling → slide out, `next()/previous()`, new record slides in from the opposite side once its bitmap is rendered; 1.6 s fallback springs back if the player ignores the command |
| Info only below, no buttons | Title/artist `BasicText`. The only button is the "open notification settings" pill, shown while access is missing |
| Record look follows the cover | `VinylDesigner.design()` (Palette swatch → HSL-tuned base colour, accent, style) |

## Gotchas / decisions
- **Notification access is mandatory** (Settings → Notification access). On Android 13+ a sideloaded APK first needs
  *App info → ⋮ → Allow restricted settings*, otherwise the toggle is greyed out. Mention this in any user docs.
- We use MediaSessions, not notification text parsing: it gives metadata, art, position, duration and transport
  controls in one go, and is what notifications themselves are built from.
- `PlaybackState` position is extrapolated from `lastPositionUpdateTime` (elapsedRealtime base) × speed.
  Some players never report duration → arm stays on the outer groove, seeking is skipped.
- Hardware bitmaps from `MediaMetadata` are copied to software (`softAndSmall`) — Palette/BitmapShader on a
  software canvas crash otherwise. Art is capped at 800 px.
- `BlurMaskFilter` shadows only render with hardware acceleration on API 28+; on 26–27 they degrade to hard shadows.
- Record bitmap is `2·R` px square (~1300 px on a 1080-wide phone) rendered on `Dispatchers.Default`
  once per (trackKey, has-art) — don't render per frame.
- Orientation is locked to portrait in the manifest; geometry assumes a tall phone (see `Geo`).
- `app/debug.keystore` is intentionally committed (see Build).

## Backlog / ideas (rough priority)
1. Push so GitHub Actions runs, install on a real phone with notification access and check the media-session
   behaviour (tonearm hit area, swipe feel, colours, cover label) — the emulator can only show the two empty states.
2. Tune geometry/visuals against `docs/reference/` screenshots (arm shape, head tilt, label size, grain).
3. More record styles for the collection (translucent, galaxy/stars, tri-colour, picture-disc, glitter) — add to
   `VinylStyle`, `VinylRenderer.drawSurface`, and the pools in `VinylDesigner.design`.
4. Fallback cover when a player gives none (notification large icon via `StatusBarNotification` in the listener).
5. Optional: keep-screen-on / dock mode, landscape layout, haptic tick when the head lands, needle-drop sound.
6. Optional: release signing from GitHub secrets, R8 + baseline profile, app icon polish.
7. Unit tests for `Geo` (angle maths) and `NowPlaying.positionNow`.

## Working agreements
- Keep `docs/SPEC.md` as the source of truth for requirements; don't silently drop a requirement.
- If you change a toolchain version, change the whole set together and note it above.
- Commit messages: imperative, short subject; mention the area (`ui:`, `media:`, `ci:`).
