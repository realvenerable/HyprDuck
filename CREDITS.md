# Credits

## Artwork

All three source SVGs in `assets/icons/` are original work by realvenerable,
drawn for HyprDuck:

| File | Subject |
|---|---|
| `hyprduck.svg` | Duck head — the launcher icon and the in-app mark |
| `hyprduck1.svg` | Duck head, line-art variant, not currently used |
| `realvenerable.svg` | Portrait — the profile avatar |

They are converted to Android VectorDrawables by `tools/make-icons.sh`. The
generated drawables are committed under `res/drawable/`, so building does not
need this folder; regenerating the icons does.

## Visual reference set

The visual direction — near-black surfaces, elevated cards, a large light-weight
numeral, a tick meter, pill sliders, segmented controls and a bottom navigation
bar — was informed by a personal collection of interface design references.

That collection is a local scratch folder and is **not** redistributed here,
because those images are other people's work. Nothing in this repository derives
from them: the drawables, layout, palette and icon geometry are all original.

If you own the rights to a reference you want included, add it to
`assets/reference/` and note it here.

## Platform

Built entirely against the Android SDK command line tools — `aapt2`, `javac`,
`d8`, `zipalign` and `apksigner`. No Gradle, no Android Studio, and no
third-party libraries.

## Name

HyprDuck is not affiliated with, endorsed by, or derived from any other product
of that name.
