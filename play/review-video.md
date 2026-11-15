# The review video

Play asks an app that uses the Accessibility API, and is not an `isAccessibilityTool`, for a short
video showing the prominent disclosure and the feature that needs the API. It is uploaded to
YouTube as **unlisted** and the link goes in the declaration form (see `console-answers.md`).

It has to show, in this order, with nothing cut between the steps:

1. **Launching the app from the launcher.** The consent step, with the full disclosure, is the
   first thing it shows. It shows only until the user agrees or the service is on, so record on a
   fresh install, or clear the app's storage first (Settings → Apps → Selection Pad → Storage →
   Clear storage) with the service off.
2. **The disclosure, readable.** It fits one screen, so hold on it for a few seconds. It must be
   the app's own screen, during normal use — not a settings page, not the privacy policy.
3. **The affirmative choice.** Press *"I understand — open Accessibility settings"*. Showing
   *"No thanks"* first, then relaunching and agreeing, is better still: it proves the choice is a
   real one. After *"I understand"* the launcher opens on its normal screen from then on, with the
   disclosure behind its **Accessibility disclosure** button, so a retake needs the storage cleared
   again.
4. **Turning the service on**, in the Accessibility settings page the button opened: the
   **Selection Pad** row, its switch, and Android's own confirmation dialog.
5. **The feature.** Open a web page in Chrome, long-press a word, and use the pad: a few character
   steps each way, a word step, the swap button, and a held button that keeps stepping until a
   touch stops it. Then copy the selection with the page's own menu, to show the point of it.
6. **Turning it off** again in Accessibility settings, and the pad disappearing.

About a minute and a half is plenty. No narration is needed; captions are optional.

## Where to record it

**On a phone, preferably with the app installed from Play** — an internal-testing release is enough,
and it is exactly what a reviewer will run. A build installed with `adb install` works too: on the
OnePlus 15 (2026-11-11) its Settings page showed the **Selection Pad** switch, and turning it on went
through Android's own "full control" dialog with no restricted-setting block (`AGENTS.md` has the
measurement). Record the version the review is for.

Record with the phone's own screen recorder, or over adb:

```powershell
adb -s <device-serial> shell screenrecord --bit-rate 8000000 /sdcard/review.mp4   # Ctrl+C to stop; 3 min cap
adb -s <device-serial> pull /sdcard/review.mp4
```

## Driving a take over adb, and cutting it

What the 2026-11-15 take (a separate Android user on the OnePlus 15) taught:

1. **Turn on Show taps first** (`settings --user <id> put system show_touches 1`), and off again
   afterwards. Without it a viewer cannot tell what was pressed.
2. **Open every app by a tap a viewer can see.** Never `am start`. The docked pad covers the home
   screen's dock and the bottom of the screen, and the swipe up into the app drawer starts there.
   So minimise the pad (`– min`) before tapping the dock or swiping up, and tap `▲ pad` to bring it
   back. That shows the minimise feature too.
3. **Locate controls with `uiautomator dump` only while the service is off.** A dump unbinds every
   accessibility service for a moment, so after the service is on, use fixed coordinates or a
   screenshot. The drawer's recent-apps row reorders itself, so find the icon each time.
4. **Do not use Settings search on OxygenOS.** It shows an AI Search consent prompt every time. After
   **I understand**, Settings stays on the service's page, so opening Settings again from the drawer
   lands there for the switch-off.
5. **Record in 180 s segments** with a device-side loop (`for i in 1 2 3; do screenrecord
   --time-limit 180 /data/local/tmp/take_$i.mp4; done`), then join them with ffmpeg's concat demuxer.
6. **`screenrecord` writes a frame only when the screen changes.** So a cut that seeks to a still
   stretch lands on the next change, and every hold collapses. Seek to the last frame at or before
   the span, make it constant-rate (`tpad`, `fps`), then `trim` the span. Its packet times
   (`ffprobe -show_entries packet=pts_time`) are also an exact list of when something happened.
7. **Cut every still stretch to about 1-3 s,** and hold longer only where there is text to read.
   Captions in a band above the screen say what each step does without covering it.
