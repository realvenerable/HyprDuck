# HyprDuck 1.1

A steadier readout, the device temperature, and a confirmation before deleting.

## What changed in 1.1

- **The level numeral is centred, and it holds still.** It is now fitted against
  the widest value the ramp can hold rather than the value on screen, so it no
  longer resizes or slides sideways as the level changes, and `of 255` has moved
  out of the line into the corner of the card. The level is the only thing on the
  card that moves.
- **The device temperature**, read from the battery thermistor or a thermal zone,
  under the level notes. There is no public API for it, so it is best effort and
  the line hides itself on devices that keep the sensor to themselves.
- **Deleting a saved level asks first.** A hold is easy to trigger by accident
  while scrolling the chip strip, and a saved level is not easy to get back. The
  prompt is drawn from the app's own palette, so it stays dark in light mode, and
  it now grows in and out instead of appearing between two frames.
- The About screen spells out the one permission inside its card, with each
  explanation inset from its heading.

## Why

A friend of mine has mild OCD and uses a HyperOS phone. HyperOS doesn't show the
brightness level anywhere on the screen, and for them being able to read the
exact level — and to set it to a number they chose — is genuinely useful. This
exists for them, and for anyone else who has wanted the same thing.

## What's in it

- The exact brightness level from the 0–255 ramp, as a large centred numeral over
  a tick meter. Not a rounded guess.
- A slider that writes the level directly, with Manual / Automatic segments.
- Quick levels at even tenths of the ramp: 25, 51, 76, 102 … 255.
- Saved levels as chips. Tap to apply, hold to delete, confirm to actually delete.
- The device temperature, read from the battery thermistor or a thermal zone.
- An About screen with the profile, the links, the one permission spelled out,
  and the device it is running on.

78 KB. One permission (`WRITE_SETTINGS`), used only for writing brightness. No
internet permission, so nothing can leave the device.

## One APK, every device

There are no per-ABI builds, and there is nothing to split. The app is pure
Java compiled to a single `classes.dex` and contains no native code at all — no
`lib/` directory and no `native-code` attribute in the manifest. Architecture is
only a property of native libraries, so this one file runs unchanged on 32-bit
ARM (`armeabi-v7a`), 64-bit ARM (`arm64-v8a`), `x86` and `x86_64`.

If you filter by ABI when installing, pick whichever matches your device; you do
not need a specific one.

## Install

```bash
adb install -r hyprduck.apk
```

Requires Android 8.0 or newer. Tested on Android 15 / HyperOS.

## Known limitations

- **No brightness in nits.** There is no public API for measured brightness, so
  the app shows only values it can genuinely read.
- **The temperature line hides itself** on devices that restrict the sysfs
  sensor nodes, because there is no public API for it either.
- **`screen_brightness_float` is usually absent on HyperOS**, so that line hides
  itself.
- Adjusting brightness needs the *Modify system settings* permission, granted
  once through the system screen. Reading the level needs no permission at all.

Implementation detail is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
