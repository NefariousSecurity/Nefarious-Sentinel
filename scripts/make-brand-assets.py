#!/usr/bin/env python3
"""
Generate social preview and account avatar from Sentinel's EXISTING raven logo.
Use: python3 scripts/make-brand-assets.py
Requires: python3 -m pip show Pillow, or your distro's python3-pil package.
Outputs are local files in branding-assets/, not uploaded automatically.
"""
from pathlib import Path
import sys

try:
    from PIL import Image, ImageDraw, ImageFont, ImageOps
except ImportError:
    sys.exit("STOP: Pillow required (e.g. sudo apt install python3-pil).")

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "controller/src/main/res/drawable-nodpi/sentinel_raven_shield_logo.png"
OUT = ROOT / "branding-assets"
if not ART.is_file():
    sys.exit(f"STOP: Canonical raven artwork missing: {ART}")
OUT.mkdir(parents=True, exist_ok=True)

INK = "#F5F5F7"
MUTED = "#BEC3CB"
RED = "#DC3545"
DARK = "#0B0C10"
PANEL = "#14161C"

def font(size, bold=False):
    filename = ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf")
    for candidate in (
        f"/usr/share/fonts/truetype/dejavu/{filename}",
        f"/usr/share/fonts/truetype/liberation2/{'LiberationSans-Bold.ttf' if bold else 'LiberationSans-Regular.ttf'}",
    ):
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()

def source_art():
    with Image.open(ART) as im:
        return im.convert("RGBA").copy()

logo = source_art()
# GitHub account profile image: a square, centred logo on dark background.
avatar = Image.new("RGBA", (512, 512), DARK)
avatar_logo = ImageOps.contain(logo, (448, 448), Image.Resampling.LANCZOS)
avatar.alpha_composite(avatar_logo, ((512-avatar_logo.width)//2, (512-avatar_logo.height)//2))
avatar_out = OUT / "nefarioussecurity-avatar.png"
avatar.convert("RGB").save(avatar_out, "PNG", optimize=True)

# Recommended GitHub social preview: 1280x640 (2:1 image).
im = Image.new("RGBA", (1280, 640), DARK)
draw = ImageDraw.Draw(im)
draw.rounded_rectangle((48, 48, 1232, 592), radius=28, fill=PANEL, outline="#343743", width=2)
draw.rounded_rectangle((48, 48, 58, 592), radius=4, fill=RED)
img = ImageOps.contain(logo, (326, 326), Image.Resampling.LANCZOS)
im.alpha_composite(img, (232 - img.width//2, 320 - img.height//2))

title1 = "NEFARIOUS"
title2 = "SENTINEL"
draw.text((425, 190), title1, fill=INK, font=font(60, bold=True), stroke_width=0)
draw.text((425, 270), title2, fill=RED, font=font(76, bold=True), stroke_width=0)
draw.rectangle((425, 389, 1155, 393), fill=RED)
draw.text((425, 426), "OPEN-SOURCE ANDROID DEVICE PROTECTION", fill=MUTED, font=font(25, bold=True))
draw.text((425, 478), "LOCAL FIRST  /  OPTIONAL DEVICE OWNER", fill=MUTED, font=font(20))
social_out = OUT / "nefarious-sentinel-social-preview.png"
im.convert("RGB").save(social_out, "PNG", optimize=True)

for result in (avatar_out, social_out):
    print(f"CREATED: {result} ({result.stat().st_size:,} bytes)")
print("Upload manually in GitHub profile Settings and repository Settings > Social preview.")
