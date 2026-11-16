# Google Play

Everything the Play listing needs that can live in the repository: the listing text, the privacy
policy, the answers to the Console's declaration forms, the store graphics, and how to build and
sign the bundle.

**Play is how this app reaches users.** An earlier reading said Enhanced Confirmation Mode hid the
service's switch from any `adb install`, which would have made Play the only way to turn it on. That
missing switch was the accessibility-button flag's settings page, not ECM: since the flag was removed,
an `adb install` on the OnePlus 15 gets the switch and Android's own confirmation dialog (2026-11-11).
See `AGENTS.md` for the measurement.

| file | what it is |
| --- | --- |
| `listing/en-US/title.txt` | app name, 30 characters at most |
| `listing/en-US/short-description.txt` | 80 characters at most |
| `listing/en-US/full-description.txt` | 4000 characters at most; plain text |
| `privacy-policy.md` | the policy; its URL in the public repository is what the Console asks for |
| `console-answers.md` | Accessibility declaration, Data safety and the other App content forms |
| `review-video.md` | what the declaration's video has to show, and where to record it |
| `graphics/icon.svg` | the 512 × 512 store icon; same geometry as the launcher icon |
| `graphics/feature-graphic.svg` | the 1024 × 500 feature graphic |
| `graphics/render.ps1` | renders both SVGs to the PNGs the Console takes, with a headless browser |
| `screenshots/page.html` | the page the Chrome screenshot is taken on, served to the emulator over `adb reverse` |
| `screenshots/capture.ps1` | takes the two listing screens on one emulator profile, into `screenshots/<profile>-<n>-<screen>.png` |
| `screenshots/serve.ps1` | the tiny HTTP server `capture.ps1` starts for `page.html` |
| `screenshots/tablet7-*.png` | the 7-inch tablet screenshots: `-1-chrome` (the pad over Chrome) and `-2-launcher`. Not taken yet: they need an idle machine (below) |
| `screenshots/tablet10-*.png` | the 10-inch tablet screenshots, the same two screens. Not taken yet either |

## Tablet screenshots

The Console wants 7- and 10-inch tablet screenshots before the listing can publish. Play takes a
tablet screenshot only at 16:9 or 9:16, a 7-inch one with each side 320 to 3840 px and a 10-inch one
with each side 1080 to 7680 px. `scripts\rig.ps1` has a profile for each, cloned from the rig's AVD
the way `handset` was (`rig.ps1 avd -RigProfile tablet7` prints the recipe):

| profile | AVD | serial | screen | smallest width |
| --- | --- | --- | --- | --- |
| `tablet7` | `asc_tablet7` | `emulator-5574` | 1080 × 1920 at 280 dpi | 617 dp |
| `tablet10` | `asc_tablet10` | `emulator-5576` | 1440 × 2560 at 320 dpi | 720 dp |

Both AVDs run 2 vCPUs (`hw.cpu.ncore=2`): a bulk session's emulator is held to 2 host cores, and 4
vCPUs on them never got past `offline`. **The first boot needs an idle machine.** Measured
2026-11-16 with `tablet7` under a bulk session's budget (2 host cores at Idle priority, other bulk
work beside it): adb saw `device` after 17 minutes, `sys.boot_completed` after 19, and then
`system_server` sat in "isn't responding" for 40 more minutes at a guest load average of 25, so
Chrome's first run never finished. For each profile, on a machine with nothing else running:

```powershell
.\scripts\rig.ps1 up -RigProfile tablet7
.\scripts\rig.ps1 install -RigProfile tablet7
# Chrome's first run, once per AVD, with the service off: open any page and click through.
.\play\screenshots\capture.ps1 -RigProfile tablet7
.\scripts\rig.ps1 down -RigProfile tablet7
```

Uploading them to the Console's store listing is a separate step.

## The upload key

Play App Signing holds the key that signs what users install. This build signs only with the
**upload key**, which proves a bundle came from the developer; if it is lost or leaked it can be
reset through Play support, so it is a credential rather than the app's identity.

It lives **outside the checkout**, and so do its passwords. Create it once:

```powershell
$jbr = "C:\Program Files\Android\Android Studio\jbr"
& "$jbr\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\.android\asc-upload.jks" `
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then give the build its four values in `~/.gradle/gradle.properties` (outside any repository):

```properties
ascUploadStoreFile=C:/Users/<you>/.android/asc-upload.jks
ascUploadStorePassword=...
ascUploadKeyAlias=upload
ascUploadKeyPassword=...
```

Each can instead be an environment variable (`ASC_UPLOAD_STORE_FILE`, `ASC_UPLOAD_STORE_PASSWORD`,
`ASC_UPLOAD_KEY_ALIAS`, `ASC_UPLOAD_KEY_PASSWORD`), which is the form a CI job would use. With none
of them set the release builds **unsigned**; with only some of them set the build stops and names
the missing ones.

**Back the keystore and its passwords up somewhere other than this machine.** A reset is possible,
but it takes a support request and days.

## Building the bundle

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:bundleRelease
# -> app\build\outputs\bundle\release\app-release.aab
```

Raise `versionCode` in `app/build.gradle.kts` before every upload: Play refuses a code it has
already seen, on any track.

Check the permission list on the release build, not just the debug one — the listing's strongest
claim rests on it: POST_NOTIFICATIONS, asked for only when the user turns the notification on, and
nothing else (above all no INTERNET):

```powershell
.\gradlew.bat :app:assembleRelease
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\aapt2.exe" dump permissions `
  app\build\outputs\apk\release\app-release.apk
# package: dev.mnaoumov.selectionpad
# uses-permission: name='android.permission.POST_NOTIFICATIONS'      <- and nothing else
```

## The order of a first release

1. Create the app in the Play Console (name *Selection Pad*, app, free).
2. Fill **App content** from `console-answers.md`, the privacy policy URL included.
3. Fill the **store listing** from `listing/` and `graphics/`, plus phone screenshots and the
   7- and 10-inch tablet ones from `screenshots/`.
4. Upload the bundle to **internal testing** and install it on a phone from Play.
5. Record the review video on that install (`review-video.md`), upload it to YouTube as unlisted,
   and add the link to the Accessibility declaration. If the Console will not roll out even the
   internal release until the declaration is complete, record it instead on a build installed
   with `adb install`, whose switch can be reached on the OnePlus 15.
6. Promote to closed testing, then production. A new personal developer account must first run a
   closed test with testers for a period before production access is granted; the Console states
   the current numbers.
