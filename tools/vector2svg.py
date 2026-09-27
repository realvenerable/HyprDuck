#!/usr/bin/env python3
"""
Render an Android VectorDrawable back to SVG, so a converted icon can be
compared against the source it came from. Verification only; not shipped.
"""
import re
import sys
import xml.etree.ElementTree as ET

AND = '{http://schemas.android.com/apk/res/android}'


def main():
    src, dst = sys.argv[1], sys.argv[2]
    size = float(sys.argv[3]) if len(sys.argv) > 3 else 240
    root = ET.parse(src).getroot()
    vb = root.get(AND + 'viewportWidth')
    vbh = root.get(AND + 'viewportHeight')
    out = ['<svg xmlns="http://www.w3.org/2000/svg" width="%g" height="%g" viewBox="0 0 %s %s">'
           % (size, size, vb, vbh)]
    # <vector> and <path> are unprefixed elements, so their tags are plain;
    # only the android: attributes are namespace-qualified.
    for p in root.iter('path'):
        d = p.get(AND + 'pathData')
        if d:
            out.append('  <path fill="#000" d="%s"/>' % d)
    out.append('</svg>')
    open(dst, 'w').write('\n'.join(out))


if __name__ == '__main__':
    main()
