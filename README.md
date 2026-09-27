<div align="center">
  <h1>HyprDuck</h1>
  <p><strong>See and set your screen brightness on Android.</strong></p>
  <p>
    <a href="../../releases"><img alt="Releases" src="https://img.shields.io/github/v/release/realvenerable/HyprDuck" /></a>
    <a href="../../issues"><img alt="Issues" src="https://img.shields.io/github/issues/realvenerable/HyprDuck" /></a>
  </p>
  <img height="140" src="assets/hyprduck-mark.svg" alt="HyprDuck" />
</div>

## Why this exists

A friend of mine has mild OCD and uses a HyperOS phone. HyperOS doesn't show the
brightness level anywhere on the screen, and for them being able to read the
exact level — and to set it to a number they chose — is genuinely useful rather
than a novelty.

So this is for them, and for anyone else who has wanted the same thing. It is the
brightness number, on screen, always one glance away.

## Screenshots

<!-- Drop phone screenshots here and uncomment:
<img width="260" src="assets/screenshot-brightness.png" alt="Brightness screen" />
<img width="260" src="assets/screenshot-about.png" alt="About screen" />
-->
_Brightness screen on the left, About on the right._

## What it does

- **The exact brightness level**, as a large number over a meter — the real
  stored 0–255 value, not a rounded guess.
- **Set it** with a slider that moves the screen brightness as you drag, and a
  Manual / Automatic switch to hand control back to the sensor.
- **Quick levels** at even tenths of the range: 25, 51, 76, 102 … 255.
- **Save levels you like.** They become chips you can tap to bring back, or hold
  to delete.
- **Two screens**, switched from the bar at the bottom.

76 KB. One permission, used only for writing brightness — and only if you
adjust. Reading the level needs no permission at all. No internet permission, so
nothing can leave the phone. No accounts, no analytics, no network code.

## Get it

Grab the APK from the [releases page](../../releases) and install it over USB:

```bash
adb install -r hyprduck.apk
```

Or download the APK on the phone and tap it, allowing installs from that source
when asked.

Requires Android 8.0 or newer. Tested on Android 15 / HyperOS.

## Build it

```bash
./build.sh
```

The build is six command line tools from the Android SDK — `aapt2`, `javac`,
`d8`, `zipalign`, `apksigner`. No Gradle, no Android Studio, no third-party
libraries.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
./build.sh
```

A signing key is created on first build. **Keep it** — it is what lets a new
version install over an existing one, so if you lose it you have to uninstall
first. It is gitignored.

How the build works, how the icon pipeline works and the platform limits worth
knowing about are all in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## AI

Built with AI assistance.

## Credits

- **Artwork** — the duck and the portrait in `assets/icons/` are original work by
  the author. See [CREDITS.md](CREDITS.md).
- **Visual reference set** — a local collection of interface designs informed
  the look. Those images are other people's work and are not redistributed here.

## Licence

[GPL-3.0-or-later](LICENSE). Artwork and design are covered too — see
[NOTICE.md](NOTICE.md).
