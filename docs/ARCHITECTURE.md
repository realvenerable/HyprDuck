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
│   ├── Thermals.java            # device temperature, from sysfs
│   └── SliderView.java          # the pill slider
├── tools/
│   ├── make-icons.sh            # source SVGs -> VectorDrawables
│   ├── svg2vector.py            # the converter
│   └── vector2svg.py            # render a drawable back to SVG, to check work
├── assets/
│   ├── icons/                 # source artwork, as plain SVG
│   ├── hyprduck-mark.svg         # README mark, dark ink, for light themes
│   └── hyprduck-mark-inverse.svg # same geometry, light ink, for dark themes
```

`res/drawable/` is generated but committed, so building never needs the SVGs.
Regenerating the icons does.
