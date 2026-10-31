# D&D — a pixel-art launcher for the TCL TV

D&D is a minimal Android TV home screen built for a TCL 43S51B (Android TV 11, 1 GB RAM), laid out
in the spirit of the Apple TV home screen. There are no ads or recommendations, nothing loads in the
background, and app artwork is scaled down to keep memory low.

## Look

- **Top shelf:** the upper part of the screen is an animated pixel-art scene of the two characters
  doing something different for each app you select: on the couch with popcorn (Stremio), a
  picnic (Netflix), Shir chasing the other two round a tree (YouTube), petting a cat (VLC), programming (SmartTube, Kodi), hiking (Prime Video),
  swimming (Disney+), climbing (Max), reading (Plex) or running (Spotify). Other apps get one
  picked from their name. The sky, or the wall indoors, takes the app's own colour, so no two apps
  look the same. The pairings live in `ACTIVITIES` and the brand colours in `HUES`, both in
  `AppRepository.kt`. The app's name sits on the left in large type.
- **App grid:** rounded app tiles in rows of five. Your streaming apps fill the top row, with
  Stremio first, then Netflix, YouTube and the rest. Everything else follows by name.
- **Focus:** the tile you are on lifts, casts a soft shadow and catches a glassy highlight. Only that
  tile shows its name.
- A small clock sits in the top-right corner.
- **Title:** "D&D" in pixel letters, centred just above the row of apps.
- **Screensaver:** after a minute without a remote press, the screen dims and the two pixel
  characters have a sword fight: salutes, clashes, thrust and parry, overhead chops, jump attacks,
  low sweeps, spin slashes, flurries, disarms, knockbacks, dodge rolls, knockdowns and feints, with
  sparks when the blades meet. Who attacks swaps at random. Any button stops it. Change the delay
  with `IDLE_MS` in `HomeActivity.kt`.

- **Continue watching:** right under the streaming apps, your half-watched titles from Plex and
  Stremio with a progress bar. Pressing one opens it in its app. Needs the keys in `dnd.json`
  (see below).
- **Quick settings:** a row of tiles under the apps for Wi-Fi, each HDMI input, a sleep timer and
  the system settings.
- **Number keys:** hold 1 to 9 on the remote to open the app in that spot, counting along the rows.

- **Time and weather:** the sky in the top shelf follows the day: bright by day, orange around
  sunrise and sunset, starry at night. Outdoor scenes get rain, snow, fog or clouds when the
  weather outside has them, and the temperature sits next to the clock. Weather comes from
  Open-Meteo.
- **Occasions:** birthdays get a banner, balloons and confetti; Halloween brings bats and
  pumpkins, Hanukkah a menorah with the right number of candles, Christmas lights and snow,
  and New Year fireworks.

## Personal settings

Your location, birthdays, holidays and streaming accounts stay on the TV, never in this repo.
Copy `dnd.example.json` to `dnd.json`, fill it in (dates are `MM-DD`, names in English letters,
leave out anything you don't use), then push it:

```sh
adb push dnd.json /sdcard/Android/data/com.tclstreamer.launcher/files/dnd.json
```

D&D reads it the next time you come back to the home screen. Without a location it works one out
from the internet connection.

- **Plex token:** open any item in Plex on the web, choose ⋯ → Get Info → View XML, and copy the
  `X-Plex-Token=` value from the address bar. The server address is the computer running Plex
  with port 32400.
- **Stremio key:** sign in at web.stremio.com, open the browser console (F12) and run
  `JSON.parse(localStorage.profile).auth.key`.

## Updates

D&D checks GitHub every few hours. When there is a newer build, an **Update D&D** tile appears in
the quick settings row. Press it, then confirm on Android's install screen. This needs:

- The `SIGNING_KEY` repository secret (Settings → Secrets and variables → Actions), holding the
  base64 of the shared signing key. Without it every build is signed with a different throwaway
  key and Android refuses to install one over another.
- Permission for D&D to install apps. If pressing the tile doesn't offer to turn that on, run:

```sh
adb shell appops set com.tclstreamer.launcher REQUEST_INSTALL_PACKAGES allow
```

## Sleep timer

Each press of the tile steps through 30, 60, 90 and 120 minutes and then off. To let it turn the
TV off, make D&D a device admin once:

```sh
adb shell dpm set-active-admin com.tclstreamer.launcher/.SleepAdmin
```

Android won't uninstall a device admin. To give the role up (before uninstalling D&D), hold OK on
the sleep timer tile.

## Get the APK

Every push builds a signed APK on GitHub Actions. Open the **Actions** tab, pick the latest
**Build APK** run, and download the `noir-launcher` artifact (a zip with the APK inside).

To build locally instead, install the Android SDK and run `./gradlew assembleRelease`.
The APK lands in `app/build/outputs/apk/release/`.

## Install straight from the TV

Every push also publishes the APK at a fixed link:
https://github.com/davidsr4321/tcl-streamer/releases/download/latest/noir.apk

1. On the TV, install **Downloader** (by AFTVnews) from the Google Play Store.
2. Settings → Device Preferences → Security & restrictions → Unknown sources: allow **Downloader**.
3. Open Downloader, type the link above, and choose **Install** when it finishes.

## Install on the TV

1. On the TV: Settings → Device Preferences → About → click **Build** seven times, then turn on
   **USB debugging** (and **Network debugging** if listed) in Developer options.
2. Find the TV's IP address in Settings → Network.
3. From a computer with `adb`:

   ```sh
   adb connect <tv-ip>:5555
   adb install -r app-release.apk
   ```

4. D&D now appears in the TV's app list. Press Home and pick D&D if the TV asks which home app to use.

### Make D&D the home screen

Android TV 11 on TCL often skips the "choose a home app" prompt and always opens the stock launcher.
If that happens, disable the stock launcher so Home goes to D&D:

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To undo it at any time:

```sh
adb shell pm enable com.google.android.tvlauncher
```

Keep D&D installed while the stock launcher is disabled, or the TV will have no home screen.

## Change which apps are featured

The order of the streaming row lives in `FEATURED` in
`app/src/main/java/com/tclstreamer/launcher/AppRepository.kt`. The first installed app on that
list takes the first spot in the top row.

## Redrawing the scenes

The scenes are drawn in `tools/scenes/activities.js` and baked into the PNG sheets in
`app/src/main/assets/scenes`. After changing them, run `node tools/scenes/render.js
app/src/main/assets/scenes` (it needs Playwright). `node tools/scenes/preview.js sheet.png`
draws every scene at a few frames on one image, for checking by eye.
