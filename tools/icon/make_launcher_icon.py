"""KaizoCore's launcher icon, built from Blake's master (kaizocore-icon-master.webp beside this file, 1280 square,
2026-10-01: the cracked ball, "This one with white background", "Yes, remove background"; asked whether it could be
transparent, then "You choose the backplate color": white).

The master's own background is near white (253 to 255), which would show as a square on pure white, so it is cut
away: the near-white pixels joined to the image's edge become transparent, and the ball, inside its black outline,
stays whole. It sits centred on the icon's white background layer (values/colors.xml's ic_launcher_background), as
wide a share of the 72dp an adaptive icon shows as it is of the master, about two thirds, inside the 66dp safe zone.
A transparent background was tried: Android fills an adaptive icon's shape, and the emulator's launcher drew the
transparent part black.

Writes app/src/main/res/mipmap-*/ic_launcher_foreground.png at the five densities, play-store-512.png beside this
file (the same on a full white square: Play rounds the corners itself) and previews.png (the icon under three
launcher shapes, then the master).

    python tools/icon/make_launcher_icon.py
"""
import os

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
MASTER = os.path.join(HERE, 'kaizocore-icon-master.webp')
RES = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res')
DENSITIES = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
# Every channel at least this, and joined to the image's edge: the master's background.
NEAR_WHITE = 232
# The ball's width over the 72dp an adaptive icon shows: the share of the master it is there (845 px of 1280), on the
# home screen and on Play's square alike.
LAUNCHER_SHARE = 0.66
PLAY_SHARE = 0.66


def cut_out(px):
    """The master as RGBA with its background transparent (and white, so a resize bleeds only white into the edge)."""
    near_white = px.min(axis=2) >= NEAR_WHITE
    labels, _ = ndimage.label(near_white)
    edge = np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))
    background = np.isin(labels, edge[edge != 0])
    rgba = np.dstack([px, np.where(background, 0, 255)]).astype(np.uint8)
    rgba[background, :3] = 255
    return rgba, ~background


def main():
    px = np.asarray(Image.open(MASTER).convert('RGB'))
    rgba, ball = cut_out(px)
    ys, xs = np.nonzero(ball)
    left, right, top, bottom = xs.min(), xs.max(), ys.min(), ys.max()
    width = max(right - left, bottom - top) + 1
    centre = ((left + right) / 2, (top + bottom) / 2)
    ball_img = Image.fromarray(rgba, 'RGBA')

    def layer(share):
        """The 108dp layer with the ball centred: the 72dp viewport is width / share, the layer half as much again."""
        side = int(round(width / share * 108 / 72))
        out = Image.new('RGBA', (side, side), (255, 255, 255, 0))
        out.paste(ball_img, (int(round(side / 2 - centre[0])), int(round(side / 2 - centre[1]))))
        return out

    launcher = layer(LAUNCHER_SHARE)
    for density, size in DENSITIES.items():
        out = os.path.join(RES, 'mipmap-' + density)
        os.makedirs(out, exist_ok=True)
        launcher.resize((size, size), Image.LANCZOS).save(os.path.join(out, 'ic_launcher_foreground.png'), optimize=True)
    # Play: the 72dp viewport of a PLAY_SHARE layer, on white.
    play = layer(PLAY_SHARE)
    flat = Image.new('RGB', play.size, (255, 255, 255))
    flat.paste(play, (0, 0), play)
    cut = play.size[0] // 6
    flat.crop((cut, cut, play.size[0] - cut, play.size[0] - cut)).resize((512, 512), Image.LANCZOS).save(
        os.path.join(HERE, 'play-store-512.png'), optimize=True)
    # Previews: the launcher's 72dp on its white background under a circle, a squircle and a rounded square, on a
    # mid-grey page, then the master.
    size, gap = 192, 24
    edge = launcher.size[0] // 6
    flat_launcher = Image.new('RGB', launcher.size, (255, 255, 255))
    flat_launcher.paste(launcher, (0, 0), launcher)
    small = flat_launcher.crop((edge, edge, launcher.size[0] - edge, launcher.size[0] - edge)).resize((size, size), Image.LANCZOS)
    sheet = Image.new('RGB', (gap + 4 * (size + gap), size + 2 * gap), (120, 124, 136))
    circle = Image.new('L', (size, size), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, size - 1, size - 1), fill=255)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    u, v = (xx + 0.5) / (size / 2) - 1, (yy + 0.5) / (size / 2) - 1
    squircle = Image.fromarray(((np.abs(u) ** 5 + np.abs(v) ** 5) <= 1).astype(np.uint8) * 255, 'L')
    rounded = Image.new('L', (size, size), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, size - 1, size - 1), radius=size // 5, fill=255)
    for i, mask in enumerate((circle, squircle, rounded)):
        sheet.paste(small, (gap + i * (size + gap), gap), mask)
    sheet.paste(Image.open(MASTER).convert('RGB').resize((size, size), Image.LANCZOS), (gap + 3 * (size + gap), gap))
    sheet.save(os.path.join(HERE, 'previews.png'))
    print('ball %d px across, centred at (%.0f, %.0f) in the master' % (width, centre[0], centre[1]))

if __name__ == '__main__':
    main()
