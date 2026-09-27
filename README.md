<div align="center">
  <h1>HyprDuck</h1>
  <p><strong>A brightness level readout for Android, built with nothing but the SDK command line tools.</strong></p>
  <img height="150" src="assets/hyprduck-mark.svg" alt="HyprDuck" />
</div>

> [!IMPORTANT]
> **This is a personal project, not a supported release.** It exists to build an
> Android app end to end with `aapt2`, `javac`, `d8` and `apksigner` and no
> Gradle, no Android Studio and no third-party libraries. There is no roadmap, no
> support promise and no public contribution programme. Install it if it is
> useful, but do not rely on it.

A read-only brightness level readout, with optional adjustment, saved levels and
a bottom navigation bar. ~76 KB, one permission, no network access, no
analytics.

## What it does

| | |
|---|---|
| **Exact level** | The real stored value from the 0–255 ramp, not a rounded guess |
| **Tick meter** | Lit ticks up to the current value, in the wallpaper accent colour |
| **Adjust** | A slider that writes the level live, plus a Manual/Automatic switch |
| **Quick levels** | Even tenths of the ramp: 25, 51, 76, 102 … 255 |
| **Saved levels** | Keep the ones you like. Tap to apply, hold to delete |
| **One permission** | `WRITE_SETTINGS`, and only for writing. Reading needs nothing |

## Pages

| Page | Contents |
|---|---|
| **Brightness** | The centred duck mark, the numeral, tick meter, quick levels, slider, mode segments, saved levels |
| **About** | Identity card, profile, why the permission exists, source, report an issue, releases, device block |

Two pages, switched from a bottom pill bar. There is no system title bar:
`Theme.DeviceDefault` ships with an ActionBar, and the framework would otherwise
draw the activity label as a title across the top, so the theme sets
`windowActionBar` and `windowNoTitle` to false. Both are repeated in
`values-v31`, because a qualified resource folder replaces a style outright
rather than merging into it.

## Build

```bash
./build.sh
```

Requires the SDK at `ANDROID_SDK_ROOT` (defaults to
`/media/Linux/Eos/Linux/android-sdk`) with `platforms;android-35` and
`build-tools;35.0.0`. Nothing else — no Gradle wrapper, no `gradlew`.

```
aapt2 compile --dir res      # XML -> flat
aapt2 link                   # resources.arsc + AndroidManifest.xml + R.java
javac --release 17           # JDK 27 emits class file v67, which d8 rejects
d8 --min-api 26              # classes.dex
zipalign -f -p 4
apksigner                    # v1 + v2 + v3
```

A keystore is generated on first build. **Keep it.** Android matches signatures
to allow in-place updates, so if you lose it you must uninstall the app before a
new build will install over it. It is gitignored, deliberately.

### Install

```bash
adb install -r dist/hyprduck.apk
```

## Repository layout

```
HyprDuck/
├── AndroidManifest.xml
├── build.sh                     # the whole build, one command
├── res/
│   ├── drawable/                # generated VectorDrawables, committed
│   ├── mipmap-anydpi-v26/       # adaptive icon
│   ├── values/                  # colours, strings, theme
│   └── values-v31/              # Material You accent override
├── src/dev/realvenerable/hyprduck/
│   ├── MainActivity.java        # shell, navigation, both pages
│   ├── Palette.java             # theme colours and shape helpers
│   ├── LevelReadoutView.java    # the large numeral
│   ├── TickMeterView.java       # the tick meter
│   └── SliderView.java          # the pill slider
├── tools/
│   ├── make-icons.sh            # source SVGs -> VectorDrawables
│   ├── svg2vector.py            # the converter
│   └── vector2svg.py            # render a drawable back to SVG, to check work
├── assets/
│   ├── icons/                   # source artwork, as plain SVG
│   └── hyprduck-mark.svg        # the README header mark
├── CREDITS.md
├── RELEASE_NOTES.md
└── LICENSE
```

## Design

The visual language comes from a personal collection of interface references:
near-black surfaces, elevated cards with a hairline border, one large
light-weight numeral with a small unit beside it, a tick meter, pill sliders
with round thumbs, segmented controls and a bottom pill bar, with a single accent
used sparingly. That collection is not redistributed here — see
[CREDITS.md](CREDITS.md) for why.

Dark in **both** system modes. That ruled out `?android:attr/textColorPrimary`:
on a forced dark surface the framework would resolve dark text colours in light
mode and they would be invisible. Every token is a literal instead, and
`windowLightStatusBar` is explicitly false so the status bar icons stay legible.

| Token | Value | Used for |
|---|---|---|
| `bl_background` | `#0B0B0D` | window, icon background |
| `bl_card` | `#1A1A1D` | elevated cards, nav pill |
| `bl_card_stroke` | `#2C2C31` | card hairline |
| `bl_track` | `#26262A` | meter ticks, segmented container, chips |
| `bl_segment_selected` | `#33333A` | selected segment, active nav item |
| `bl_text_primary` | `#F5F5F7` | numeral, buttons, thumb, active icon |
| `bl_text_secondary` | `#8A8A8F` | units, hints, inactive icon |
| `bl_accent` | wallpaper on 31+, `#F2C94C` below | current meter tick, slider fill, links |

### Material You

There are **no `Theme.Material3.*` platform styles** — that family exists only
in the AndroidX support library, which this project does not use. So:

- The theme parent is `@android:style/Theme.DeviceDefault`, the only platform
  family available back to API 26.
- `values-v31/themes.xml` overrides only the accent with
  `@android:color/system_accent1_500`, which the system generates from the
  wallpaper, so the single accent colour follows it.
- Everything else is the literal token set above.
- The components are drawn by hand, because the platform widgets do not render as
  Material 3.

### Fitting the screen

Both pages fill the screen rather than overflowing it. The level card takes the
leftover space with `weight = 1`, so it shrinks as the cards below it grow.
`LevelReadoutView` scales the numeral to the room it is actually given — capped
at 66% of the available height, then shrunk until the number and its unit fit
the width — so it grows on a tall screen and shrinks on a short one, including
at large font scales.

The `ScrollView` on each page is only a fallback for extreme font scales. Its
scrollbars are disabled in both directions and over-scroll is off, so no scroll
indicator or glow is ever shown. Every page column ends with a 16 dp spacer, so
the last card never sits flush against the navigation bar.

`LevelReadoutView` cannot use `setAutoSizeTextTypeUniformWithConfiguration`
here: inside a horizontal `wrap_content` row the view is measured to fit its own
text, so it would never shrink and would clip instead.

## Icons

The artwork in `assets/icons/` is plain SVG, converted to VectorDrawables by
`tools/make-icons.sh` and committed under `res/drawable/`. Building does not need
the SVGs; regenerating the icons does.

```bash
./tools/make-icons.sh
```

`tools/svg2vector.py` does the work, and three things about it are worth knowing
before editing the artwork:

- **The transform is a real matrix.** The sources wrap their paths in
  `<g transform="translate(0,H) scale(0.1,-0.1)">`, and a VectorDrawable `<path>`
  has no `transform` attribute, so the matrix is baked into the coordinates.
  `translate` and `scale` compose as a 2×3 matrix; folding them into a single
  scale number scales the translation too.
- **Relative commands are deltas.** potrace emits plenty of them. The bounding
  box has to accumulate them against the current point, or the box comes out
  too small and the fit matrix pushes the art outside the viewport.
- **pathData cannot exceed `0x7FFF` bytes.** Past that aapt2 reports
  `STRING_TOO_LARGE` and writes a *truncated* resource, so the drawable renders
  as nothing at all — the build still succeeds. The portrait needs an
  Ramer–Douglas–Peucker tolerance of `0.15` to get under it, which is why
  `make-icons.sh` passes a tolerance for every icon. The converter refuses to
  emit anything over the limit, so an edit that pushes an icon over fails there
  instead of silently shipping a blank one.

The launcher foreground is additionally capped at **radius 35**, inside the
adaptive icon's radius-36 safe circle. The duck is far wider than it is tall, so
fitting it to a square box would leave the beak and crest outside the mask.

`ic_duck_mark` is the same artwork at a different fit, for use inside the app.
The launcher layer has to keep margin for the mask, which makes it look small
when shown in a card.

## Known limits

**Measured brightness in nits is not shown.** There is no public API for it.
`android.view.Display` has no brightness method at all, and neither
`Display.BrightnessInfo` nor `android.hardware.display.BrightnessInfo` exists in
the public SDK — both are `@SystemApi`, blocked by non-SDK interface
restrictions on Android 9 and later. The app reports only values it can
genuinely read. The ambient light sensor (`Sensor.TYPE_LIGHT_SENSOR`, in lux, no
permission) is the closest honest substitute and is not implemented.

`screen_brightness_float` is usually absent on HyperOS, so that line hides itself
when the device does not publish it.

`WRITE_SETTINGS` is an AppOps-gated special permission, not a runtime one. Until
you grant it through the system screen, the slider, mode segments and save button
are hidden and replaced by an *Allow adjusting brightness* button. Reading the
level needs no permission, so the readout works regardless.

## Licence

[GPL-3.0-or-later](LICENSE). See [NOTICE.md](NOTICE.md) for the artwork and
[CREDITS.md](CREDITS.md) for provenance.
