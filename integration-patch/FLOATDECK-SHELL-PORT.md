# FloatDeck shell + APRIL engine integration

This branch is a safe integration lane. The original `build/android-debug-apk` branch and the already-working APRIL APK are not changed.

## Upstream shell reference

- Upstream: https://github.com/kxxoling/FloatDeck
- License: MIT (keep its copyright and license notices if code is reused)
- Relevant upstream components:
  - `app/src/main/kotlin/app/floatdeck/settings/SettingsActivity.kt` — existing native settings shell
  - `app/src/main/kotlin/app/floatdeck/data/SettingsRepository.kt` — persisted settings and SharedPreferences mirror
  - `app/src/main/kotlin/app/floatdeck/service/FloatDeckWallpaperService.kt` — live wallpaper service / renderer lifecycle
  - `app/src/main/kotlin/app/floatdeck/gl/FloatDeckRenderer.kt` — OpenGL parallax renderer

## Integration rule

FloatDeck is the **shell reference**, not a replacement for APRIL's art or depth engine. Do not swap in FloatDeck's renderer as the final renderer: it draws a wallpaper plus floating portrait cards, while APRIL renders its existing six-layer depth artwork. The APRIL art assets and original source ZIP must remain unchanged.

## Shared settings contract

Use the same three values across the shell and APRIL engine:

| Setting | Stored range | Default | APRIL effect |
| --- | ---: | ---: | --- |
| `gyro` | 0–100 | 50 | Sensor response multiplier; 50 keeps current response |
| `parallax` | 0–100 | 60 | Depth offsets / layer separation multiplier |
| `motion` | 0–100 | 40 | Overall motion amplitude multiplier |

The existing Adalo-to-APRIL deep-link contract remains:

`april://settings?gyro=50&parallax=60&motion=40`

The values in the example are defaults only; the actual Save Settings action must supply the current slider values.

## Adapter added in this branch

`integration-patch/AprilSettingsBridge.kt` defines the shared values and writes them to both the APRIL preference file and the FloatDeck settings preference file. It is an adapter component, not yet connected to FloatDeck's Compose sliders or included in a combined APK. It deliberately does not replace the APRIL renderer or alter the artwork.

## Safe integration sequence

1. Keep the current APRIL engine and artwork as the source of truth.
2. Port the shell/settings flow only after identifying the exact Compose entry points and preference keys.
3. Call `AprilSettingsBridge.save()` from the shell's settings-save path with current slider values.
4. Keep the existing APRIL renderer/service and register them in the combined app manifest; do not replace them with FloatDeck's service.
5. Build in CI, inspect the APK, and test the deep link and wallpaper before considering any merge.

## Current status

- [x] Created isolated `floatdeck-shell-integration` branch from `integrate-adalo-settings`.
- [x] Confirmed FloatDeck has a native Compose settings activity, DataStore-backed settings repository, SharedPreferences mirror, and its own OpenGL wallpaper service.
- [x] Added a settings adapter scaffold for sharing the three values between the future shell and APRIL renderer.
- [x] Kept the existing APRIL bridge and artwork source untouched.
- [ ] Port FloatDeck settings UI into the APRIL app without changing the APRIL artwork or renderer.
- [ ] Wire all three settings values to APRIL's renderer and validate on device.
- [ ] Produce and test a combined debug APK.

No upstream FloatDeck files have been changed. This branch is the integration lane; the original APK branch remains untouched.
