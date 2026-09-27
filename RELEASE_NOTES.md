# HyprDuck 1.0

> **Personal project, not a supported release.** This is a side project built for
> fun and for the exercise of building an Android app with nothing but the SDK
> command line tools. There is no roadmap, no support promise and no public
> contribution programme here. Install it if it is useful, but do not rely on
> it.

The first tagged version. A read-only brightness level readout for Android with
optional adjustment, built without Android Studio and without Gradle.

## What is in it

- The exact brightness level your device has stored, as a large numeral over a
  tick meter.
- A slider that writes the level directly, a Manual/Automatic switch, and
  `WRITE_SETTINGS` requested only when you actually use one of them.
- Quick levels at even tenths of the 0–255 ramp, and your own saved levels.
- An About page with the profile, the links, the one permission spelled out, and
  the device it is running on.

## Build

```bash
./build.sh
```

Needs the SDK at `ANDROID_SDK_ROOT` (defaults to
`/media/Linux/Eos/Linux/android-sdk`) with `platforms;android-35` and
`build-tools;35.0.0`. A keystore is generated on first build; see the README
before losing it, because it is what authorises in-place updates.

## Notes

- **No measured brightness in nits.** There is no public API for it, so the app
  reports only values it can genuinely read rather than inventing one.
- **`screen_brightness_float` is usually absent on HyperOS**, so that line hides
  itself when the device does not publish it.
- The launcher icon is a duck head drawn as vector paths, with a monochrome layer
  for Android 13+ themed icons.
