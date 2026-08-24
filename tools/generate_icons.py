#!/usr/bin/env python3
"""Generate the Dayboard icon set from one geometric definition.

The mark is the app's own layout used as a glyph: one wide bar (the clock card
that always sits on top) above two blocks (the left and right card columns).
It stays legible at 16px because it is only three shapes with generous gaps.

Colors are derived from the app's design tokens rather than hardcoded hex, so
the icons cannot drift from the coral default theme in `tokens.css`.

Run:  python3 tools/generate_icons.py
Out:  src/jsMain/resources/  (favicon.ico, icon-*.png, og-image.png)
"""

from __future__ import annotations

import colorsys
import os
from PIL import Image, ImageDraw, ImageFont

# --- design tokens (coral theme, from REQUIREMENTS.md 12.2) ------------------

TOKEN_PRIMARY = (350, 91, 60)     # --primary
TOKEN_BACKGROUND = (350, 30, 97)  # --background (light)
TOKEN_FOREGROUND = (350, 25, 15)  # --foreground (light)
TOKEN_MUTED_FG = (350, 12, 42)    # --muted-foreground (light)
GLYPH = (255, 255, 255)

# --- mark geometry, as fractions of the tile edge ---------------------------

TILE_RADIUS = 0.22    # rounded-square tile, iOS-ish proportion
GLYPH_INSET = 0.205   # margin between tile edge and the mark
GLYPH_GAP = 0.058     # gap between the bar and the blocks, and between blocks
BAR_HEIGHT = 0.152    # the clock card
CARD_RADIUS = 0.042   # the mark's own corner rounding

SUPERSAMPLE = 8       # draw large, downsample once: clean edges at every size

# Dakalebi's icon set, which index.html and the Pages deploy expect.
PNG_SIZES = [16, 32, 180, 192, 512]
ICO_SIZES = [48, 32, 16]

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "jsMain", "resources",
)

FONT_CANDIDATES = [
    "/System/Library/Fonts/SFNSDisplay.ttf",
    "/System/Library/Fonts/SFNS.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/Library/Fonts/Arial.ttf",
]


def hsl(token: tuple[int, int, int]) -> tuple[int, int, int]:
    """Convert an `H S% L%` design token to 8-bit RGB."""
    h, s, l = token
    r, g, b = colorsys.hls_to_rgb(h / 360.0, l / 100.0, s / 100.0)
    return (round(r * 255), round(g * 255), round(b * 255))


def draw_tile(size: int) -> Image.Image:
    """Render the app tile: coral rounded square with the white board mark."""
    s = size * SUPERSAMPLE
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=TILE_RADIUS * s, fill=hsl(TOKEN_PRIMARY))

    left = GLYPH_INSET * s
    top = GLYPH_INSET * s
    span = s - 2 * left
    gap = GLYPH_GAP * s
    bar_h = BAR_HEIGHT * s
    card_r = CARD_RADIUS * s

    # the clock card: full width, always on top
    d.rounded_rectangle([left, top, left + span, top + bar_h], radius=card_r, fill=GLYPH)

    # the two card columns below it
    col_w = (span - gap) / 2
    col_top = top + bar_h + gap
    col_bottom = top + span
    d.rounded_rectangle([left, col_top, left + col_w, col_bottom], radius=card_r, fill=GLYPH)
    d.rounded_rectangle(
        [left + col_w + gap, col_top, left + span, col_bottom], radius=card_r, fill=GLYPH
    )

    return img.resize((size, size), Image.LANCZOS)


def load_font(px: int) -> ImageFont.FreeTypeFont | None:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, px)
            except OSError:
                continue
    return None


def draw_og_image() -> Image.Image:
    """1200x630 social card: the tile, the wordmark, and one line of copy."""
    w, h = 1200, 630
    img = Image.new("RGB", (w, h), hsl(TOKEN_BACKGROUND))
    d = ImageDraw.Draw(img)

    tile = 240
    tile_x, tile_y = 110, (h - tile) // 2
    img.paste(draw_tile(tile), (tile_x, tile_y), draw_tile(tile))

    text_x = tile_x + tile + 70
    title_font = load_font(104)
    body_font = load_font(38)

    if title_font and body_font:
        d.text((text_x, 236), "Dayboard", font=title_font, fill=hsl(TOKEN_FOREGROUND))
        d.text(
            (text_x, 366),
            "Pomodoro timer, tasks, and notes in one board.",
            font=body_font,
            fill=hsl(TOKEN_MUTED_FG),
        )
    else:
        print("  ! no usable system font found, OG image rendered without text")

    return img


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)

    for size in PNG_SIZES:
        path = os.path.join(OUT_DIR, f"icon-{size}.png")
        draw_tile(size).save(path)
        print(f"  {path}")

    # favicon.png duplicates the 512 tile: the service worker and the original's
    # notification options reference a single `favicon.png`.
    favicon_png = os.path.join(OUT_DIR, "favicon.png")
    draw_tile(512).save(favicon_png)
    print(f"  {favicon_png}")

    ico_path = os.path.join(OUT_DIR, "favicon.ico")
    draw_tile(ICO_SIZES[0]).save(
        ico_path, format="ICO", sizes=[(n, n) for n in ICO_SIZES]
    )
    print(f"  {ico_path}")

    og_path = os.path.join(OUT_DIR, "og-image.png")
    draw_og_image().save(og_path)
    print(f"  {og_path}")


if __name__ == "__main__":
    main()
