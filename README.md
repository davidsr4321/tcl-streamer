# Noir — a quiet launcher for the TCL TV

Noir is a minimal Android TV home screen built for a TCL 43S51B (Android TV 11, 1 GB RAM).
It shows the time, one hero card for your main streaming app (Stremio when it is installed),
a row of your other streaming apps, and everything else in a second row. Nothing loads in the
background, there are no ads or recommendations, and app artwork is scaled down to keep memory low.

## Look

- Near-black ink background, ivory text, a single gold accent for focus.
- Serif clock and app titles, small letter-spaced captions.
- Focus is a gentle lift and a thin gold edge. App names appear only on the tile you are on.

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
list becomes the hero card.
