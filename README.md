# Noir — a quiet launcher for the TCL TV

Noir is a minimal Android TV home screen built for a TCL 43S51B (Android TV 11, 1 GB RAM), laid out
in the spirit of the Apple TV home screen. There are no ads or recommendations, nothing loads in the
background, and app artwork is scaled down to keep memory low.

## Look

- **Top shelf:** the upper part of the screen is a pixel-art scene picked from the colour of the
  app you are on in the top row: a volcano for red apps (YouTube, Netflix), a desert for orange and
  yellow, a forest for green, a moonlit sea for blue, a starry night with an aurora for purple
  (Stremio) and a city at night for grey. The two pixel characters stroll around in it, and the
  app's name sits on the left in large type. Brand colours for the big apps live in `HUES` in
  `AppRepository.kt`; any other app gets its colour from its banner.
- **App grid:** rounded app tiles in rows of five. Your streaming apps fill the top row, with
  Stremio first, then Netflix, YouTube and the rest. Everything else follows by name.
- **Focus:** the tile you are on lifts, casts a soft shadow and catches a glassy highlight. Only that
  tile shows its name.
- A small clock sits in the top-right corner.
- **Screensaver:** after a minute without a remote press, the screen dims and two pixel-art
  friends each wander their own way around the screen, pausing only briefly between walks. Any button sends them
  away. Change the delay with `IDLE_MS` in `HomeActivity.kt`.

## Get the APK

Every push builds a signed APK on GitHub Actions. Open the **Actions** tab, pick the latest
**Build APK** run, and download the `noir-launcher` artifact (a zip with the APK inside).

To build locally instead, install the Android SDK and run `./gradlew assembleRelease`.
The APK lands in `app/build/outputs/apk/release/`.

## Install on the TV

1. On the TV: Settings → Device Preferences → About → click **Build** seven times, then turn on
   **USB debugging** (and **Network debugging** if listed) in Developer options.
2. Find the TV's IP address in Settings → Network.
3. From a computer with `adb`:

   ```sh
   adb connect <tv-ip>:5555
   adb install -r app-release.apk
   ```

4. Noir now appears in the TV's app list. Press Home and pick Noir if the TV asks which home app to use.

### Make Noir the home screen

Android TV 11 on TCL often skips the "choose a home app" prompt and always opens the stock launcher.
If that happens, disable the stock launcher so Home goes to Noir:

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To undo it at any time:

```sh
adb shell pm enable com.google.android.tvlauncher
```

Keep Noir installed while the stock launcher is disabled, or the TV will have no home screen.

## Change which apps are featured

The order of the streaming row lives in `FEATURED` in
`app/src/main/java/com/tclstreamer/launcher/AppRepository.kt`. The first installed app on that
list takes the first spot in the top row.
