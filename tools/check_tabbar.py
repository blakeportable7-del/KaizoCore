"""Measure the tab bar at a given system font scale.

Anchors on the six tab NODES from the layout tree, then reads the RENDERED
PIXELS inside each node's own box. Both halves matter:

  * the tree alone cannot see clipping or wrapping - a node reports the box it
    was given, not what fitted in it,
  * the pixels alone cannot tell a tab label from anything else on screen,
    which is exactly how the first version of this script measured the game
    area and declared six failures that were not there.

Per tab it reports the number of text rows (2 means the label wrapped) and how
close the glyphs come to the slot edge (0 or less means clipped).

Usage: python tools/check_tabbar.py <screenshot.png> <uiautomator.xml>
"""
import re
import sys

from PIL import Image

LABELS = ("PREP", "RUN", "PLAY", "ROMS", "KEYS", "INFO")
# 300, not 600: the ACTIVE tab is gold (255,235,0), whose channel sum is 490.
# At 600 the selected tab read as zero text rows and the check failed on the
# one tab that was rendering perfectly. The dark ground sums to about 105.
BRIGHT = 300


def tab_nodes(xml):
    found = {}
    pat = (r'text="([^"]*)"[^>]*bounds="' + re.escape("[") +
           r'(\d+),(\d+)' + re.escape("][") + r'(\d+),(\d+)' + re.escape("]") + r'"')
    for m in re.finditer(pat, xml):
        text = m.group(1).strip()
        box = tuple(int(g) for g in m.groups()[1:])
        for lab in LABELS:
            # The active tab carries a selector triangle; match on the word.
            if text.replace(chr(0x25B6), "").strip() == lab:
                # Lowest match wins: the tab bar is at the bottom.
                if lab not in found or box[1] > found[lab][1]:
                    found[lab] = box
    return found


def rows_and_edge(im, box):
    l, t, r, b = box
    lit_rows, rightmost = [], None
    for y in range(t, b):
        lit = [x for x in range(l, r) if sum(im.getpixel((x, y))) > BRIGHT]
        if lit:
            lit_rows.append(y)
            rightmost = max(rightmost or 0, max(lit))
    # Group contiguous lit rows into text lines.
    lines = 0
    prev = None
    for y in lit_rows:
        if prev is None or y - prev > 2:
            lines += 1
        prev = y
    return lines, rightmost


def main(png, xml_path):
    im = Image.open(png).convert("RGB")
    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    nodes = tab_nodes(xml)

    ok = True
    if len(nodes) != 6:
        print("FAIL: found %d tab nodes, expected 6 (%s)"
              % (len(nodes), sorted(nodes)))
        ok = False

    for lab in LABELS:
        if lab not in nodes:
            continue
        box = nodes[lab]
        lines, rightmost = rows_and_edge(im, box)
        slack = (box[2] - rightmost) if rightmost else None
        print("%-5s slot %4d..%4d  lines=%d  right-slack=%s"
              % (lab, box[0], box[2], lines,
                 "n/a" if slack is None else slack))
        if lines != 1:
            print("      FAIL: label occupies %d text rows (wrapped)" % lines)
            ok = False
        if slack is not None and slack <= 0:
            print("      FAIL: glyphs reach or pass the slot edge")
            ok = False

    heights = {b[3] - b[1] for b in nodes.values()}
    print("tab heights: %s px" % sorted(heights))
    print("PASS" if ok else "FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
