#!/usr/bin/env bash
#
# Regenerates the vector drawables in res/drawable from the source SVGs in
# assets/icons. Run this after editing the originals, then rebuild.
#
# The sources are potrace output: outlines, and a <g> carrying a
# translate/scale that VectorDrawable cannot express, so svg2vector.py bakes the
# transform into the coordinates and fits the art to a viewport.
#
# The last argument to each conversion is an RDP tolerance in viewport units.
# It is not cosmetic: a single pathData attribute cannot exceed the
# ResStringPool limit of 0x7FFF bytes, and past that aapt2 reports
# STRING_TOO_LARGE and writes a truncated resource, so the drawable renders as
# nothing. The portrait needs 0.15 to get under it. svg2vector.py refuses to
# emit anything over the limit, so a source edit that pushes it over fails here
# instead of silently shipping a blank icon.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Sources are vendored in the repository, so this works on any clone. Override
# ICONS to regenerate from a different copy of the artwork.
ICONS="${ICONS:-$ROOT/assets/icons}"
OUT="$ROOT/res/drawable"
CONV="$ROOT/tools/svg2vector.py"

[ -d "$ICONS" ] || { echo "icons not found: $ICONS" >&2; exit 1; }

# Launcher foreground, 108x108 adaptive viewport.
# Capped at radius 35, since the safe circle is radius 36: the duck is much
# wider than it is tall, so fitting it to a square box would push the beak and
# crest outside the mask. The tolerance is small because this one is seen at
# 48px, where every curve shows.
python3 "$CONV" "$ICONS/hyprduck.svg" "$OUT/ic_launcher_foreground.xml" \
    108 0 0 1 1 1.0 35 0 0.05

# Same geometry for themed icons. Only the alpha channel is used by the system.
sed 's/#FFFFFFFF/#FF000000/' \
    "$OUT/ic_launcher_foreground.xml" > "$OUT/ic_launcher_monochrome.xml"

# In-app mark. Same source, but no radius cap: the launcher foreground leaves
# margin for the adaptive mask, which makes the duck look small when shown
# directly in a card. Transparent background, no dark disc behind it.
python3 "$CONV" "$ICONS/hyprduck.svg" "$OUT/ic_duck_mark.xml" 96 0 0 1 1 0.94 0 0 0.05

# Line-art duck, not referenced by the UI yet - kept because the source exists.
python3 "$CONV" "$ICONS/hyprduck1.svg" "$OUT/ic_duck_line.xml" 56 0 0 1 1 0.92 0 0 0.15

# Profile portrait, cropped to the head and scaled to cover a circular clip.
python3 "$CONV" "$ICONS/realvenerable.svg" "$OUT/ic_avatar.xml" \
    48 0.08 0.00 0.80 0.60 1.0 0 1 0.15

echo "regenerated:"
wc -c "$OUT"/ic_launcher_foreground.xml "$OUT"/ic_launcher_monochrome.xml \
      "$OUT"/ic_duck_mark.xml "$OUT"/ic_duck_line.xml "$OUT"/ic_avatar.xml
