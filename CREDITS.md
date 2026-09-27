# Credits and provenance

## Artwork

All three source SVGs in `assets/icons/` are original work by realvenerable,
traced and drawn for HyprDuck:

| File | Subject |
|---|---|
| `hyprduck.svg` | Duck head, used for the launcher icon and the in-app mark |
| `hyprduck1.svg` | Duck head, line-art variant, not currently used in the UI |
| `realvenerable.svg` | Portrait, used as the profile avatar |

They are converted to Android VectorDrawables by `tools/make-icons.sh`. The
generated drawables are committed under `res/drawable/`, so building does not
require this folder; regenerating the icons does.

## Visual reference set

The visual direction — near-black surfaces, elevated cards with a hairline
border, one large light-weight numeral, a tick meter, pill sliders with round
thumbs, segmented controls and a bottom pill navigation bar — was informed by a
personal collection of interface design references. That collection is a local
scratch folder and is **not** redistributed here, because those images are other
people's work and their inclusion would be a licensing question rather than a
technical one. Nothing in this repository is derived from them: the drawables,
the layout, the palette and the icon geometry are all original.

If you own the rights to any reference you want included, add it to
`assets/reference/` and note it here.

## Platform

Built entirely against the Android SDK command line tools — `aapt2`, `javac`,
`d8`, `zipalign` and `apksigner`. No Gradle, no Android Studio, and no
third-party libraries, including no AndroidX or Material Components dependency.
See the README for why the last one is deliberate.

## Name

HyprDuck is not affiliated with, endorsed by, or derived from any product of
that name.
