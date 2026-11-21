# Product spec (source of truth)

Owner: Kasi. Original request, verbatim (Russian), followed by an English working translation.

> Сделай приложение на андроид.
> Имитация винилового плеера. Приложение определяет звуки, играющие из других приложений. Берет обложку и
> информацию воспроизводимого (полагаю, из уведомления), вставляет на диск. Посмотри на скриншоте.
> Нажатие на центр диска останавливает или продолжает воспроизведение. Перетаскивание головки с иглой
> перематывает воспроизведение. При проигрывании игла тоже движется, соответственно. При паузе головка
> снимается и наоборот – при снятии головки пауза. При свайпе вправо или влево переход к прошлому или
> следующему, с анимацией. Кнопки как на скриншоте можно не делать, только информацию снизу.
> Полагаю, будет разумно сделать коллекцию симпатичных видов пластинок, а потом просто подгонять их под цвет
> обложки трека.
> Собери в гитхаб проект и сразу с компиляцией в апк, запиши все нужные данные для продолжения работы агентов.

## Requirements (English)
1. Android app imitating a vinyl turntable.
2. Detects audio playing from other apps; takes the cover art and track info (likely from the notification /
   media session) and puts it on the record label. See `docs/reference/`.
3. Tap on the record centre toggles play/pause.
4. Dragging the tonearm head (with the needle) seeks.
5. While playing, the needle moves across the record accordingly.
6. Pause lifts the head; conversely, lifting the head pauses.
7. Swipe left/right = next/previous track, with an animation.
8. The reference screenshot's buttons (PLAY, prev, next) are NOT needed — only the info text below the record.
9. Build a collection of attractive record looks and fit them to the track cover's colours.
10. Deliver as a GitHub project with APK compilation (CI) set up from the start, plus notes for continuing agents
    (`AGENTS.md`).

## Reference screenshots (`docs/reference/`)
- `screenshot-playing.jpg` — splatter record (red/white), needle on the record, title "Dry Your Tears" / "Yorina",
  old design with three buttons at the bottom.
- `screenshot-paused.jpg` — marbled yellow record, tonearm parked straight down at the right edge, title "bellyache" /
  "Billie Eilish". (Overlays at the top/bottom of this one belong to other apps/the OS and are not part of the design.)

Layout facts measured from them (720x1560): record radius ≈ 0.607·W, centre ≈ (0.275·W, 0.36·H) so it bleeds off the
left edge; label (cover) radius ≈ 0.446 of the record; tonearm pivot top-right with a translucent rounded housing;
title bold ≈ 0.05·W at y ≈ 0.73·H, artist light grey below it; warm off-white paper background (#EFEDE8).
The cover rotates with the record.

## Addendum (owner request, 2026-11-21)
App name: **винИИла**. A settings section, opened by tapping the tonearm pivot, must contain:
1. A slider for the visual disc rotation speed; the current speed is **100 %**.
2. A "hide the status bar" switch.
3. A theme choice: light / dark / system.
Settings must actually work and nothing that existed before may break.
