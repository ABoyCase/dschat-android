# -*- coding: utf-8 -*-
"""按 dschat/icon.svg 的构图生成安卓图标（legacy / round / adaptive 前景背景）。"""
from pathlib import Path
from PIL import Image, ImageDraw

RES = Path(r"C:\Users\WG199\Desktop\AI\app\src\main\res")

C_TOP = (0x5B, 0x78, 0xFF)   # #5b78ff
C_BOT = (0x3B, 0x52, 0xD9)   # #3b52d9
DOT = (0x4A, 0x63, 0xE8)     # #4a63e8

S = 512


def gradient(size=S):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size - 2)
            px[x, y] = (
                int(C_TOP[0] + (C_BOT[0] - C_TOP[0]) * t),
                int(C_TOP[1] + (C_BOT[1] - C_TOP[1]) * t),
                int(C_TOP[2] + (C_BOT[2] - C_TOP[2]) * t),
            )
    return img


def bubble_layer(size=S):
    """白色气泡 + 三个蓝点，透明底。坐标按 512 画布等比缩放。"""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    k = size / 512.0

    def r(*v):
        return tuple(int(x * k) for x in v)

    # 圆角矩形主体
    d.rounded_rectangle(r(136, 136, 376, 280), radius=int(32 * k), fill=(255, 255, 255, 245))
    # 左下角尾巴
    d.polygon([r(232, 278), r(168, 336), r(168, 278)], fill=(255, 255, 255, 245))
    # 三个点
    for cx in (200, 256, 312):
        d.ellipse(r(cx - 13, 224 - 13, cx + 13, 224 + 13), fill=DOT + (255,))
    return layer


def full_icon(size, round_=False):
    """完整图标：渐变底 + 气泡。legacy 用圆角方，round 用圆形。"""
    big = S * 4
    bg = gradient(big).convert("RGBA")
    bg.alpha_composite(bubble_layer(big))

    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if round_:
        md.ellipse((0, 0, big - 1, big - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * 112 / 512), fill=255)
    bg.putalpha(mask)

    return bg.resize((size, size), Image.LANCZOS)


def adaptive_fg(size):
    """自适应前景：只有气泡，居中，占画布约 55%（安全区内）。"""
    big = S * 4
    bub = bubble_layer(big)
    # 气泡实际内容框（含尾巴）
    box = (136 * 4, 136 * 4, 376 * 4, 336 * 4)
    crop = bub.crop(box)

    target_w = int(size * 0.47)
    ratio = target_w / crop.width
    crop = crop.resize((target_w, max(1, int(crop.height * ratio))), Image.LANCZOS)

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(crop, ((size - crop.width) // 2, (size - crop.height) // 2))
    return canvas


def adaptive_bg(size):
    return gradient(S * 4).resize((size, size), Image.LANCZOS).convert("RGBA")


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

for name, px in DENSITIES.items():
    d = RES / f"mipmap-{name}"
    d.mkdir(parents=True, exist_ok=True)

    full_icon(px).save(d / "ic_launcher.png")
    full_icon(px, round_=True).save(d / "ic_launcher_round.png")

    # 自适应图标画布 = 108dp
    canvas = int(px * 108 / 48)
    adaptive_fg(canvas).save(d / "ic_launcher_foreground.png")
    adaptive_bg(canvas).save(d / "ic_launcher_background.png")

    print(f"{name}: icon={px} adaptive={canvas}")

print("done")
