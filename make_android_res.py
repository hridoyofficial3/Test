"""Run in CI: makes the Android launcher icons + splash from icon-512.png / maskable-512.png."""
import os, sys
from PIL import Image, ImageDraw

res = sys.argv[1]
BG = (12, 11, 22, 255)
icon = Image.open('icon-512.png').convert('RGBA')
mask_src = Image.open('maskable-512.png').convert('RGBA')
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

for d, m in dens.items():
    p = os.path.join(res, 'mipmap-' + d)
    os.makedirs(p, exist_ok=True)
    s = int(48 * m)
    icon.resize((s, s), Image.LANCZOS).save(os.path.join(p, 'ic_launcher.png'))
    r = mask_src.resize((s, s), Image.LANCZOS)
    circle = Image.new('L', (s * 4, s * 4), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
    circle = circle.resize((s, s), Image.LANCZOS)
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    out.paste(r, (0, 0), circle)
    out.save(os.path.join(p, 'ic_launcher_round.png'))
    f = int(108 * m)
    Image.open('adaptive-foreground.png').convert('RGBA').resize((f, f), Image.LANCZOS).save(os.path.join(p, 'ic_launcher_foreground.png'))

v26 = os.path.join(res, 'mipmap-anydpi-v26')
os.makedirs(v26, exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
for n in ('ic_launcher', 'ic_launcher_round'):
    open(os.path.join(v26, n + '.xml'), 'w').write(xml)

vals = os.path.join(res, 'values')
os.makedirs(vals, exist_ok=True)
open(os.path.join(vals, 'ic_launcher_background.xml'), 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#d4fbed</color>\n</resources>\n')

for root, _, files in os.walk(res):
    for fn in files:
        if fn == 'splash.png':
            Image.new('RGB', (64, 64), BG[:3]).save(os.path.join(root, fn))

# small white heart for the notification bar (Android wants a plain white glyph)
def heart(sz):
    S = sz * 8
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = S * 0.22
    cy = S * 0.38
    for cx in (S * 0.36, S * 0.64):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 255, 255, 255))
    d.polygon([(S * 0.145, cy + r * 0.45), (S * 0.855, cy + r * 0.45), (S * 0.5, S * 0.86)], fill=(255, 255, 255, 255))
    return im.resize((sz, sz), Image.LANCZOS)

for d, m in dens.items():
    p = os.path.join(res, 'drawable-' + d)
    os.makedirs(p, exist_ok=True)
    heart(int(24 * m)).save(os.path.join(p, 'ic_stat_heart.png'))

print('android resources ready')
