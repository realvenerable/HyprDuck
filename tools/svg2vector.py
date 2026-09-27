#!/usr/bin/env python3
"""
Convert the potrace-traced SVGs in ../hyprduck/icons into Android VectorDrawables.

Why a script rather than hand-editing: every path sits inside
    <g transform="translate(0,H) scale(0.1,-0.1)">
which flips Y and scales by 0.1. A VectorDrawable <path> has no transform
attribute, so that matrix has to be baked into the coordinates.

Two things that are easy to get wrong and were wrong in the first attempt:

  * translate and scale compose as a real 2x3 matrix. Folding them into one
    scale number, the way a naive accumulate does, scales the translation too.
  * potrace emits relative commands, whose arguments are deltas. The bounding
    box has to accumulate them against the current point, otherwise the box
    comes out too small and the fit matrix pushes the art out of the viewport.
"""

import re
import sys
import xml.etree.ElementTree as ET

# Number of arguments per path command.
ARGS = {
    'M': 2, 'L': 2, 'T': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'A': 7,
    'Z': 0,
}
for _c in 'mlthvcsqaz':
    ARGS[_c] = ARGS[_c.upper()]

# Coordinate pairs as (x_index, y_index).
SINGLE_PAIR = [(0, 1)]
CURVE_PAIRS = {
    'C': [(0, 1), (2, 3), (4, 5)],
    'S': [(0, 1), (2, 3)],
    'Q': [(0, 1), (2, 3)],
}

TOKEN = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?')


def parse(d):
    """Split path data into (command, args) pairs, expanding implicit repeats."""
    toks = TOKEN.findall(d)
    out = []
    i = 0
    while i < len(toks):
        cmd = toks[i]
        i += 1
        if cmd not in ARGS:
            raise ValueError('unsupported command %r' % cmd)
        n = ARGS[cmd]
        if n == 0:
            out.append((cmd, []))
            continue
        while i + n <= len(toks):
            out.append((cmd, [float(toks[i + k]) for k in range(n)]))
            i += n
            # An implicit repeat ends when the next token starts a new command.
            if i < len(toks) and not re.match(r'^-?[\d.]', toks[i]):
                break
    return out


def transform_all(cmds, m):
    """
    Bake an affine matrix into path coordinates, in place of the SVG transform.

    x' = a x + c y + e,  y' = b x + d y + f
    Relative commands keep being relative: their deltas get the linear part
    only, never the translation.
    """
    a, b, c, d, e, f = m
    out = []
    for cmd, args in cmds:
        if not args:
            out.append((cmd, []))
            continue
        rel = cmd.islower()
        up = cmd.upper()
        new = list(args)

        if up in ('M', 'L', 'T'):
            pairs = SINGLE_PAIR
        elif up == 'C' or up == 'S' or up == 'Q':
            pairs = CURVE_PAIRS[up]
        elif up == 'A':
            new[0] = a * args[0] + c * args[1]
            new[1] = b * args[0] + d * args[1]
            if d < 0:
                # A negative Y scale mirrors the arc, so the sweep has to flip
                # or the bulge lands on the wrong side.
                new[4] = 1 - args[4]
            pairs = [(5, 6)]
        else:
            out.append((cmd, new))
            continue

        for i, j in pairs:
            if rel:
                x = a * args[i] + c * args[j]
                y = b * args[i] + d * args[j]
            else:
                x = a * args[i] + c * args[j] + e
                y = b * args[i] + d * args[j] + f
            new[i] = x
            new[j] = y
        out.append((cmd, new))
    return out


def _bezier(p0, p1, p2, p3, t):
    mt = 1 - t
    return (
        mt ** 3 * p0[0] + 3 * mt ** 2 * t * p1[0] + 3 * mt * t ** 2 * p2[0] + t ** 3 * p3[0],
        mt ** 3 * p0[1] + 3 * mt ** 2 * t * p1[1] + 3 * mt * t ** 2 * p2[1] + t ** 3 * p3[1],
    )


def bbox(cmds, samples=32):
    """
    Flatten to points so bounds include curve extremes, not just anchors.

    Relative arguments are deltas and are accumulated against the current
    point; reading them as absolute positions silently shrinks the box, which
    then makes the fit matrix push the art outside the viewport.
    """
    xs, ys = [], []
    cx = cy = 0.0
    sx = sy = None

    for cmd, a in cmds:
        if not a:
            continue
        c = cmd.upper()
        rel = cmd.islower()

        def pt(x, y, _cx=cx, _cy=cy):
            return (_cx + x, _cy + y) if rel else (x, y)

        if c == 'M':
            cx, cy = pt(a[0], a[1])
            sx, sy = cx, cy
            xs.append(cx)
            ys.append(cy)
        elif c == 'L':
            cx, cy = pt(a[0], a[1])
            xs.append(cx)
            ys.append(cy)
        elif c == 'H':
            cx = cx + a[0] if rel else a[0]
            xs.append(cx)
            ys.append(cy)
        elif c == 'V':
            cy = cy + a[0] if rel else a[0]
            xs.append(cx)
            ys.append(cy)
        elif c in ('C', 'S', 'Q'):
            if c == 'C':
                p1, p2, p3 = pt(a[0], a[1]), pt(a[2], a[3]), pt(a[4], a[5])
            elif c == 'S':
                p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
                p2 = pt(a[0], a[1])
                p3 = pt(a[2], a[3])
            else:
                q1 = pt(a[0], a[1])
                q2 = pt(a[2], a[3])
                p1 = (cx + 2.0 / 3 * (q1[0] - cx), cy + 2.0 / 3 * (q1[1] - cy))
                p2 = (q2[0] + 2.0 / 3 * (q1[0] - q2[0]), q2[1] + 2.0 / 3 * (q1[1] - q2[1]))
                p3 = q2
            for i in range(samples + 1):
                x, y = _bezier((cx, cy), p1, p2, p3, i / samples)
                xs.append(x)
                ys.append(y)
            cx, cy = p3
        elif c == 'T':
            p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
            p3 = pt(a[0], a[1])
            p2 = ((cx + 2 * p3[0]) / 3, (cy + 2 * p3[1]) / 3)
            for i in range(samples + 1):
                x, y = _bezier((cx, cy), p1, p2, p3, i / samples)
                xs.append(x)
                ys.append(y)
            cx, cy = p3

        if c not in ('C', 'S', 'Q', 'T'):
            sx, sy = cx, cy

    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def mat_mul(m1, m2):
    a1, b1, c1, d1, e1, f1 = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (a1 * a2 + c1 * b2,
            b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2,
            b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1,
            b1 * e2 + d1 * f2 + f1)


def parse_transform(tr):
    """Read an SVG transform list into one 2x3 matrix, composed left to right."""
    m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for name, args in re.findall(r'(\w+)\s*\(([^)]*)\)', tr or ''):
        nums = [float(x) for x in re.findall(r'-?[\d.]+(?:[eE][-+]?\d+)?', args)]
        if name == 'translate':
            m = mat_mul(m, (1, 0, 0, 1, nums[0], nums[1] if len(nums) > 1 else 0))
        elif name == 'scale':
            sx = nums[0]
            sy = nums[1] if len(nums) > 1 else sx
            m = mat_mul(m, (sx, 0, 0, sy, 0, 0))
        elif name == 'matrix':
            m = mat_mul(m, tuple(nums[:6]))
    return m


def points(cmds, samples=32):
    """Flattened point list, so extents can be measured beyond the bbox corners."""
    b = bbox(cmds, samples)
    xs, ys = [], []
    cx = cy = 0.0
    sx = sy = None
    for cmd, a in cmds:
        if not a:
            continue
        c = cmd.upper()
        rel = cmd.islower()

        def pt(x, y, _cx=cx, _cy=cy):
            return (_cx + x, _cy + y) if rel else (x, y)

        if c == 'M':
            cx, cy = pt(a[0], a[1])
            sx, sy = cx, cy
            xs.append(cx)
            ys.append(cy)
        elif c == 'L':
            cx, cy = pt(a[0], a[1])
            xs.append(cx)
            ys.append(cy)
        elif c == 'H':
            cx = cx + a[0] if rel else a[0]
            xs.append(cx)
            ys.append(cy)
        elif c == 'V':
            cy = cy + a[0] if rel else a[0]
            xs.append(cx)
            ys.append(cy)
        elif c in ('C', 'S', 'Q'):
            if c == 'C':
                p1, p2, p3 = pt(a[0], a[1]), pt(a[2], a[3]), pt(a[4], a[5])
            elif c == 'S':
                p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
                p2 = pt(a[0], a[1])
                p3 = pt(a[2], a[3])
            else:
                q1 = pt(a[0], a[1])
                q2 = pt(a[2], a[3])
                p1 = (cx + 2.0 / 3 * (q1[0] - cx), cy + 2.0 / 3 * (q1[1] - cy))
                p2 = (q2[0] + 2.0 / 3 * (q1[0] - q2[0]), q2[1] + 2.0 / 3 * (q1[1] - q2[1]))
                p3 = q2
            for i in range(samples + 1):
                xs.append(_bezier((cx, cy), p1, p2, p3, i / samples)[0])
                ys.append(_bezier((cx, cy), p1, p2, p3, i / samples)[1])
            cx, cy = p3
        elif c == 'T':
            p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
            p3 = pt(a[0], a[1])
            p2 = ((cx + 2 * p3[0]) / 3, (cy + 2 * p3[1]) / 3)
            for i in range(samples + 1):
                xs.append(_bezier((cx, cy), p1, p2, p3, i / samples)[0])
                ys.append(_bezier((cx, cy), p1, p2, p3, i / samples)[1])
            cx, cy = p3
        if c not in ('C', 'S', 'Q', 'T'):
            sx, sy = cx, cy
    return xs, ys


def fmt(v):
    s = '%.2f' % v
    if s.endswith('.00'):
        s = s[:-3]
    return '0' if s == '-0' else s


def serialise(cmds):
    parts = []
    for cmd, a in cmds:
        parts.append(cmd)
        if a:
            parts.append(' '.join(fmt(v) for v in a))
    return ' '.join(parts)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    target = float(sys.argv[3])          # viewport size
    crop = [float(v) for v in sys.argv[4:8]]   # x0 y0 x1 y1, fractions of bbox
    fit = float(sys.argv[8]) if len(sys.argv) > 8 else 1.0
    radius = float(sys.argv[9]) if len(sys.argv) > 9 else 0.0   # safe-circle cap
    cover = int(sys.argv[10]) if len(sys.argv) > 10 else 0  # 1 = fill, 0 = fit
    # RDP tolerance in viewport units; 0 keeps the original curves. Anything
    # below ~0.5 is invisible at the size these are drawn.
    tol = float(sys.argv[11]) if len(sys.argv) > 11 else 0.0

    root = ET.parse(src).getroot()
    ns = '{http://www.w3.org/2000/svg}'

    svg_m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for g in root.iter(ns + 'g'):
        svg_m = mat_mul(svg_m, parse_transform(g.get('transform')))
        break

    paths = [transform_all(parse(p.get('d')), svg_m)
             for p in root.iter(ns + 'path') if p.get('d')]
    if not paths:
        raise SystemExit('no geometry in %s' % src)

    box = None
    for cmds in paths:
        b = bbox(cmds)
        if b:
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]),
                                         max(box[2], b[2]), max(box[3], b[3]))

    x0 = box[0] + (box[2] - box[0]) * crop[0]
    y0 = box[1] + (box[3] - box[1]) * crop[1]
    x1 = box[0] + (box[2] - box[0]) * crop[2]
    y1 = box[1] + (box[3] - box[1]) * crop[3]
    w, h = x1 - x0, y1 - y0

    scale = min(target * fit / w, target * fit / h)
    if cover:
        # Cover rather than contain, for art clipped to a shape such as a
        # circular avatar: the art must reach every edge or the clip shows gaps.
        scale = max(target / w, target / h)

    if radius > 0:
        # Keep the art inside a circle of the given radius, measured as the
        # farthest real point rather than the bbox corner. A wide shape like the
        # duck head fits a square box easily yet still pokes outside the adaptive
        # icon's 72dp safe circle, where a circular launcher mask crops it.
        mid_x = (x0 + x1) / 2.0
        mid_y = (y0 + y1) / 2.0
        worst = 0.0
        for cmds in paths:
            xs, ys = points(cmds)
            for px, py in zip(xs, ys):
                d = ((px - mid_x) ** 2 + (py - mid_y) ** 2) ** 0.5
                if d > worst:
                    worst = d
        if worst > 0:
            scale = min(scale, radius / worst)

    m = (scale, 0.0, 0.0, scale,
         (target - w * scale) / 2.0 - x0 * scale,
         (target - h * scale) / 2.0 - y0 * scale)
    final = [transform_all(c, m) for c in paths]

    if tol > 0:
        final = [simplify(c, tol) for c in final]

    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="%ddp"' % round(target),
           '    android:height="%ddp"' % round(target),
           '    android:viewportWidth="%g"' % target,
           '    android:viewportHeight="%g">' % target]
    for cmds in final:
        out.append('    <path')
        out.append('        android:fillColor="#FFFFFFFF"')
        out.append('        android:pathData="%s" />' % serialise(cmds))
    out.append('</vector>')

    # A single pathData attribute cannot exceed the ResStringPool limit of
    # 0x7FFF bytes. Past that aapt2 reports STRING_TOO_LARGE and silently writes
    # a truncated resource, so the drawable renders as nothing at all. Fail here
    # instead, where the cause is obvious.
    for i, cmds in enumerate(final):
        n = len(serialise(cmds))
        if n > 32000:
            sys.stderr.write('  ERROR: path %d is %d chars, over the 32000 limit.\n'
                             '         Re-run with a larger tolerance.\n' % (i, n))
            return 1

    with open(dst, 'w') as fh:
        fh.write('\n'.join(out) + '\n')

    sys.stderr.write('%-14s %2d paths  bbox %.1f %.1f %.1f %.1f  ->  %s\n'
                     % (src.rsplit('/', 1)[-1], len(final),
                        box[0], box[1], box[2], box[3], dst.rsplit('/', 1)[-1]))




# --------------------------------------------------------------- simplification

def _sample(cmds, per_seg=14):
    """
    Convert to one closed polyline per subpath.

    Potrace emits thousands of tiny curves. The detail is invisible at the sizes
    these drawables are actually shown, and a very long pathData attribute does
    not survive aapt2: it reports STRING_TOO_LARGE and the compiled resource
    comes out truncated, so the icon renders as nothing.
    """
    subpaths = []
    cur = []
    cx = cy = 0.0
    sx = sy = None
    start = None

    for cmd, a in cmds:
        if not a:
            continue
        c = cmd.upper()
        rel = cmd.islower()

        def pt(x, y, _cx=cx, _cy=cy):
            return (_cx + x, _cy + y) if rel else (x, y)

        if c == 'M':
            if len(cur) > 2:
                subpaths.append(cur)
            cx, cy = pt(a[0], a[1])
            sx, sy = cx, cy
            start = (cx, cy)
            cur = [(cx, cy)]
        elif c == 'L':
            cx, cy = pt(a[0], a[1])
            cur.append((cx, cy))
        elif c == 'H':
            cx = cx + a[0] if rel else a[0]
            cur.append((cx, cy))
        elif c == 'V':
            cy = cy + a[0] if rel else a[0]
            cur.append((cx, cy))
        elif c in ('C', 'S', 'Q', 'T'):
            if c == 'C':
                p1, p2, p3 = pt(a[0], a[1]), pt(a[2], a[3]), pt(a[4], a[5])
            elif c == 'S':
                p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
                p2 = pt(a[0], a[1])
                p3 = pt(a[2], a[3])
            elif c == 'Q':
                q1, q2 = pt(a[0], a[1]), pt(a[2], a[3])
                p1 = (cx + 2.0 / 3 * (q1[0] - cx), cy + 2.0 / 3 * (q1[1] - cy))
                p2 = (q2[0] + 2.0 / 3 * (q1[0] - q2[0]), q2[1] + 2.0 / 3 * (q1[1] - q2[1]))
                p3 = q2
            else:
                p1 = (sx * 2 - cx, sy * 2 - cy) if sx is not None else (cx, cy)
                p3 = pt(a[0], a[1])
                p2 = ((cx + 2 * p3[0]) / 3, (cy + 2 * p3[1]) / 3)
            for i in range(1, per_seg + 1):
                cur.append(_bezier((cx, cy), p1, p2, p3, i / per_seg))
            cx, cy = p3
        elif c == 'A':
            # Arcs are not sampled; treat the endpoint as a corner.
            cx, cy = pt(a[5], a[6])
            cur.append((cx, cy))
        if c not in ('C', 'S', 'Q', 'T'):
            sx, sy = cx, cy

    if len(cur) > 2:
        subpaths.append(cur)
    return subpaths


def _rdp(pts, tol):
    """Ramer-Douglas-Peucker on an open polyline."""
    if len(pts) < 3:
        return list(pts)
    x0, y0 = pts[0]
    x1, y1 = pts[-1]
    dx, dy = x1 - x0, y1 - y0
    denom = (dx * dx + dy * dy) ** 0.5
    worst_d = -1.0
    worst_i = -1
    for i in range(1, len(pts) - 1):
        px, py = pts[i]
        if denom < 1e-9:
            d = ((px - x0) ** 2 + (py - y0) ** 2) ** 0.5
        else:
            d = abs(dy * px - dx * py + x1 * y0 - y1 * x0) / denom
        if d > worst_d:
            worst_d = d
            worst_i = i
    if worst_d <= tol:
        return [pts[0], pts[-1]]
    left = _rdp(pts[:worst_i + 1], tol)
    right = _rdp(pts[worst_i:], tol)
    return left[:-1] + right


def simplify(cmds, tol, per_seg=14):
    """
    Fewer, smoother segments: simplify each closed subpath, then re-fit it with
    Catmull-Rom style cubics so it stays curved rather than faceted.
    """
    out = []
    for poly in _sample(cmds, per_seg):
        closed = poly + [poly[0]]
        pts = _rdp(closed, tol)
        if pts[0] == pts[-1]:
            pts = pts[:-1]
        if len(pts) < 3:
            out.append(('M', [pts[0][0], pts[0][1]]))
            out.append(('L', [pts[1][0], pts[1][1]]))
            out.append(('Z', []))
            continue

        out.append(('M', [pts[0][0], pts[0][1]]))
        n = len(pts)
        for i in range(n):
            p0 = pts[(i - 1) % n]
            p1 = pts[i]
            p2 = pts[(i + 1) % n]
            p3 = pts[(i + 2) % n]
            c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
            c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
            out.append(('C', [c1[0], c1[1], c2[0], c2[1], p2[0], p2[1]]))
        out.append(('Z', []))
    return out


if __name__ == '__main__':
    sys.exit(main() or 0)
