# Architecture and implementation notes

Reference material for anyone changing the code. For what the app does, see the
[README](../README.md).

## Stack

Six binaries from the Android SDK and nothing else.

```
aapt2 compile --dir res      # XML -> flat
aapt2 link                   # resources.arsc + AndroidManifest.xml + R.java
javac --release 17           # JDK 27 emits class file v67, which d8 rejects
d8 --min-api 26              # classes.dex
zipalign -f -p 4
apksigner                    # v1 + v2 + v3
```

No Gradle, no Android Studio, no AndroidX, no Material Components, no
third-party code of any kind.

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
└── assets/icons/                # source artwork, as plain SVG
```

`res/drawable/` is generated but committed, so building never needs the SVGs.
Regenerating the icons does.

## Pages

`MainActivity` hosts a bottom pill navigation bar and a page frame. Each page is
built once and toggled by visibility, so control state survives tab switches.
The poller stops when you leave the Brightness page rather than running behind
another page.

Adding a page is four edits, all in `MainActivity.java`:

1. A `PAGE_*` constant next to the existing ones.
2. Its id in `NAV_PAGES`, plus an icon in `NAV_ICONS` and a label in `NAV_LABELS`,
   in the order you want it in the pill.
3. Return a view from `buildPage()`. Use `matchWrap()` for fixed-height cards, or
   a `weight = 1` `LayoutParams` for a card that should absorb the leftover
   space. Finish with `space(BOTTOM_GAP_DP)`.
4. Wrap it in `pageScroller()`.

The shell, navigation, ripples and window insets are automatic.

## Layout

Both pages fill the screen rather than overflowing it.

- The level card takes the leftover space with `weight = 1`, so it shrinks as the
  cards below it grow.
- `LevelReadoutView` scales the numeral to the room it is actually given: capped
  at 66% of the available height, then shrunk until the number and its unit fit
  the available width. So it grows on a tall screen and shrinks on a short one,
  including at large font scales.
- The `ScrollView` on each page is only a fallback for extreme font scales. Its
  scrollbars are disabled in both directions and over-scroll is off, so no scroll
  indicator or glow is ever shown. The chip strips are configured the same way.
- Every page column ends with a 16 dp spacer, so the last card never sits flush
  against the navigation bar.

`LevelReadoutView` cannot use `setAutoSizeTextTypeUniformWithConfiguration` here.
Inside a horizontal `wrap_content` row the view is measured to fit its own text,
so it would never shrink and would clip instead — which is what the first
version did.

### Window insets

Android 15 forces edge-to-edge for `targetSdk 35`. Without handling insets the
content is drawn underneath the status and navigation bars, which clipped the
numeral in v1.0. The shell root applies system bar and display cutout insets as
padding, with a separate code path for API 26–29.

### No title bar

`Theme.DeviceDefault` ships with an ActionBar, and the framework draws the
activity label as a title across the top of the window. The theme sets
`windowActionBar` and `windowNoTitle` to false to remove it. Both switches are
needed, and both are repeated in `values-v31` — a qualified resource folder
replaces a style outright rather than merging into it, so fixing only `values/`
brings the title bar straight back on API 31 and newer.

## Colour

Dark in **both** system modes, so all tokens are literal colours rather than
`?android:attr` references. On a forced dark surface the framework would resolve
dark text colours in light mode and they would be invisible.
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

There are **no `Theme.Material3.*` platform styles**. That family exists only in
the AndroidX support library, which this project does not use.

- The theme parent is `@android:style/Theme.DeviceDefault`, the only platform
  family available back to API 26.
- `values-v31/themes.xml` overrides only the accent with
  `@android:color/system_accent1_500`, which the system generates from the
  wallpaper, so the single accent colour follows it.
- Everything else is the literal token set above.
- Components are drawn by hand, because the platform widgets do not render as
  Material 3.

The launcher icon background is a fixed colour rather than a wallpaper-derived
one, unlike the in-app accent: launchers cache and reuse icon resources, so a
dynamic background would disagree with the app's own colours and would not
reliably update when the wallpaper changed.

## Icons

```bash
./tools/make-icons.sh
```

The artwork in `assets/icons/` is plain SVG, converted to VectorDrawables and
committed under `res/drawable/`. `tools/svg2vector.py` does the work, and three
things about it matter before editing the artwork.

**The transform is a real matrix.** The sources wrap their paths in
`<g transform="translate(0,H) scale(0.1,-0.1)">`, and a VectorDrawable `<path>`
has no `transform` attribute, so the matrix is baked into the coordinates.
`translate` and `scale` compose as a 2×3 matrix; folding them into a single scale
number scales the translation too.

**Relative commands are deltas.** potrace emits plenty of them. The bounding box
has to accumulate them against the current point, or the box comes out too small
and the fit matrix pushes the art outside the viewport.

**pathData cannot exceed `0x7FFF` bytes.** Past that aapt2 reports
`STRING_TOO_LARGE` and writes a *truncated* resource, so the drawable renders as
nothing at all — and the build still succeeds. The portrait needs an
Ramer–Douglas–Peucker tolerance of `0.15` to get under it, which is why every
conversion in `make-icons.sh` passes a tolerance. The converter refuses to emit
anything over the limit, so an edit that pushes an icon over fails there instead
of silently shipping a blank one. Simplification is RDP followed by a
Catmull–Rom refit, so curves stay smooth rather than faceted.

Two further details worth keeping:

- The launcher foreground is capped at **radius 35**, inside the adaptive icon's
  radius-36 safe circle. The duck is far wider than it is tall, so fitting it to a
  square box would leave the beak and crest outside the mask.
- `ic_duck_mark` is the same artwork at a different fit, for use inside the app.
  The launcher layer has to keep margin for the mask, which makes it look small
  when shown in a card.

`tools/vector2svg.py` renders a VectorDrawable back to SVG. It exists to compare
converted icons against their sources, which is the only way to catch a bad
transform.

## Platform behaviour and limits

**Measured brightness in nits is not shown.** There is no public API for it.
`android.view.Display` has no brightness method at all, and neither
`Display.BrightnessInfo` nor `android.hardware.display.BrightnessInfo` exists in
the public SDK — both are `@SystemApi`, blocked by non-SDK interface
restrictions on Android 9 and later. The app reports only values it can genuinely
read. The ambient light sensor (`Sensor.TYPE_LIGHT_SENSOR`, in lux, no
permission) is the closest honest substitute and is not implemented.

**`screen_brightness_float` is usually absent on HyperOS**, so that line hides
itself when the device does not publish it.

**`WRITE_SETTINGS` is AppOps-gated, not a runtime permission.** Until it is
granted through the system screen, the slider, mode segments and save button are
hidden and replaced by an *Allow adjusting brightness* button. Reading the level
needs no permission, so the readout works regardless. Tapping a quick level or a
saved chip before granting it opens the system screen rather than failing
silently.

**A stored level does nothing while the sensor is driving**, so adjusting
switches the mode to Manual automatically.

## Signing

A keystore is generated on first build and gitignored. Android matches signatures
to allow in-place updates, so losing it means the app has to be uninstalled
before a new build will install over it. Password and alias are `brightnesslevel`
in `build.sh`.
