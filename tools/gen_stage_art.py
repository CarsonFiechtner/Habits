#!/usr/bin/env python3
"""Generates KeepUp's streak-stage art as Android VectorDrawables.

One spec (below) produces:
  app/src/main/res/drawable/stage_N.xml                 in-app art, 120x120 viewport, with background
  app/src/main/res/drawable/ic_launcher_stage_N_fg.xml   adaptive-icon foreground (art without background)
  app/src/main/res/drawable/ic_launcher_stage_N_bg.xml   adaptive-icon background
  app/src/main/res/mipmap-anydpi/ic_launcher_stage_N.xml adaptive icon

Run from the repo root:  python3 tools/gen_stage_art.py
"""
import os

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'


def circle(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy}a{rx},{ry} 0 1,0 {2 * rx},0a{rx},{ry} 0 1,0 {-2 * rx},0z"


def argb(hex_rgb, opacity=1.0):
    a = round(opacity * 255)
    return f"#{a:02X}{hex_rgb.lstrip('#').upper()}"


def radial(cx, cy, r, stops):
    return {"type": "radial", "cx": cx, "cy": cy, "r": r, "stops": stops}


def linear(x1, y1, x2, y2, stops):
    return {"type": "linear", "x1": x1, "y1": y1, "x2": x2, "y2": y2, "stops": stops}


def el(d, fill=None, stroke=None, width=None, opacity=1.0, cap=None, rotate=None, bg=False):
    return {"d": d, "fill": fill, "stroke": stroke, "width": width, "opacity": opacity, "cap": cap, "rotate": rotate, "bg": bg}


BG_RECT = "M0,0h120v120h-120z"
LIGHT_BG = "#EEF6EC"


def glow(cx, cy, r, color, peak):
    return el(circle(cx, cy, r), fill=radial(cx, cy, r, [(0, argb(color, peak)), (1, argb(color, 0))]))


def dot(cx, cy, r, color):
    return el(circle(cx, cy, r), fill=color)


SEED = [
    el(BG_RECT, fill=LIGHT_BG, bg=True),
    el("M0 96 Q60 84 120 96 L120 120 L0 120Z", fill="#B7A88A"),
    el(ellipse(60, 92, 26, 7), fill="#8F7D5C"),
    glow(60, 80, 24, "#C9F28F", 0.9),
    el(ellipse(60, 83, 7.5, 9.5), fill="#5E9E33"),
    el(ellipse(57.5, 79.5, 2.2, 3.2), fill="#B5E58A"),
    dot(40, 62, 1.6, "#8CCB5A"), dot(80, 56, 1.8, "#8CCB5A"), dot(68, 44, 1.2, "#8CCB5A"),
]

SPROUT = [
    el(BG_RECT, fill=LIGHT_BG, bg=True),
    el("M0 96 Q60 84 120 96 L120 120 L0 120Z", fill="#B7A88A"),
    glow(60, 58, 34, "#D4F7A1", 0.85),
    el("M60 93 C60 80 57 70 60 56", stroke="#3F7F24", width=3.2, cap="round"),
    el("M59 70 C46 69 38 60 37 49 C49 50 57 58 59 70Z", fill="#7CC24F"),
    el("M60 62 C72 59 81 50 83 38 C70 39 61 48 60 62Z", fill="#4E9E33"),
    el("M60 56 C57 50 58 44 62 40 C64 46 63 51 60 56Z", fill="#A5DB72"),
    dot(36, 34, 1.6, "#8CCB5A"), dot(88, 60, 1.8, "#8CCB5A"), dot(74, 24, 1.3, "#8CCB5A"),
]

SAPLING = [
    el(BG_RECT, fill=LIGHT_BG, bg=True),
    el("M0 98 Q60 86 120 98 L120 120 L0 120Z", fill="#B7A88A"),
    glow(60, 46, 40, "#D4F7A1", 0.75),
    el("M60 96 C60 80 58 64 61 38 M60 70 C54 64 48 62 42 62 M61 58 C66 52 72 50 78 50", stroke="#7A5A3A", width=3.5, cap="round"),
    dot(44, 58, 10, "#4E9E33"), dot(78, 46, 11, "#3F8F2C"), dot(60, 34, 13, "#6DBB45"),
    dot(50, 42, 8, "#7CC24F"), dot(70, 58, 7, "#5FAE3A"), dot(56, 29, 4, "#B5E58A"),
    dot(30, 40, 1.6, "#8CCB5A"), dot(92, 70, 1.8, "#8CCB5A"),
]

OAK = [
    el(BG_RECT, fill=linear(0, 0, 0, 120, [(0, "#FFE3F2DF"), (1, "#FFF4FAF2")]), bg=True),
    el("M0 100 Q60 90 120 100 L120 120 L0 120Z", fill="#9DB57F"),
    el("M53 102 C56 88 55 78 47 68 L56 70 L60 56 L64 70 L73 66 C66 77 64 88 68 102Z", fill="#6D4C33"),
    dot(36, 52, 19, "#2E7D32"), dot(84, 52, 19, "#2E7D32"), dot(60, 34, 24, "#2E7D32"),
    dot(46, 44, 16, "#43A047"), dot(74, 42, 16, "#43A047"), dot(60, 56, 17, "#3B9440"),
    dot(51, 31, 9, "#7CC24F"), dot(72, 28, 7, "#7CC24F"), dot(32, 46, 6, "#66B348"),
    glow(22, 30, 5, "#EAFBA0", 1), dot(22, 30, 1.4, "#F4FFC4"),
    glow(98, 70, 5, "#EAFBA0", 1), dot(98, 70, 1.4, "#F4FFC4"),
    glow(90, 20, 4, "#EAFBA0", 1), dot(90, 20, 1.1, "#F4FFC4"),
]


def bloom(cx, cy, r):
    return el(circle(cx, cy, r), fill=radial(cx, cy, r, [(0, "#FFFFFFFF"), (0.45, argb("#F2F6FF", 0.9)), (1, argb("#F2F6FF", 0))]))


STARS = ("M20 18 l1 3 3 1 -3 1 -1 3 -1 -3 -3 -1 3 -1z M38 10 l.7 2 2 .7 -2 .7 -.7 2 -.7 -2 -2 -.7 2 -.7z "
         "M78 12 l.8 2.4 2.4 .8 -2.4 .8 -.8 2.4 -.8 -2.4 -2.4 -.8 2.4 -.8z M106 44 l.6 1.8 1.8 .6 -1.8 .6 -.6 1.8 -.6 -1.8 -1.8 -.6 1.8 -.6z "
         "M12 52 l.6 1.8 1.8 .6 -1.8 .6 -.6 1.8 -.6 -1.8 -1.8 -.6 1.8 -.6z")

SILVERBOUGH = [
    el(BG_RECT, fill=linear(0, 0, 0, 120, [(0, "#FF1C2740"), (1, "#FF090D16")]), bg=True),
    glow(60, 50, 46, "#DDE8FF", 0.55),
    el("M95 15 a10 10 0 1 0 8 14 a8 8 0 1 1 -8 -14z", fill="#EEF2F8"),
    el(STARS, fill="#FFFFFF", opacity=0.85),
    el(ellipse(60, 106, 40, 6), fill="#26324A"),
    el("M55 106 C57 92 56 80 49 70 C44 63 36 58 27 56 C37 55 45 58 51 63 C52 55 50 47 45 39 C52 45 56 53 57 61 "
       "C58 51 61 41 66 32 C66 43 63 52 63 61 C68 54 76 49 87 47 C78 53 71 59 67 66 C62 76 63 90 66 106Z",
       fill=linear(40, 0, 80, 0, [(0, "#FFB7C2D3"), (0.5, "#FFF7FAFD"), (1, "#FFA9B5C8")])),
    bloom(27, 53, 11), bloom(45, 37, 12), bloom(66, 29, 14), bloom(87, 45, 12), bloom(56, 46, 9), bloom(76, 36, 8),
    dot(34, 70, 1.2, "#FFFFFF"), dot(90, 66, 1.4, "#FFFFFF"), dot(80, 84, 1, "#FFFFFF"), dot(42, 88, 1.1, "#FFFFFF"),
]

RAYS = "M60 44 L60 2 M60 44 L92 6 M60 44 L116 28 M60 44 L118 60 M60 44 L28 6 M60 44 L4 28 M60 44 L2 60 M60 44 L76 2 M60 44 L44 2"

WORLD_TREE = [
    el(BG_RECT, fill=linear(0, 0, 0, 120, [(0, "#FF2B1B08"), (1, "#FF0B0705")]), bg=True),
    el(RAYS, stroke="#FFD77A", width=1.4, opacity=0.35),
    el(circle(60, 44, 50), fill=radial(60, 44, 50, [(0, "#FFFFF6D2"), (0.3, argb("#FFD54F", 0.95)), (0.65, argb("#F9A825", 0.55)), (1, argb("#F9A825", 0))])),
    el(circle(40, 44, 15), fill="#FFC23D", opacity=0.85), el(circle(80, 44, 15), fill="#FFC23D", opacity=0.85),
    el(circle(60, 32, 19), fill="#FFD25A", opacity=0.9),
    el(circle(50, 52, 12), fill="#FFB52E", opacity=0.85), el(circle(70, 52, 12), fill="#FFB52E", opacity=0.85),
    el(circle(58, 28, 8), fill="#FFF0B3", opacity=0.9),
    el(ellipse(60, 108, 44, 7), fill="#3A250C"),
    el(ellipse(60, 106, 30, 3), fill="#FFCF5C", opacity=0.35),
    el("M49 108 C53 92 52 79 45 68 L55 71 L60 56 L65 71 L75 68 C68 79 67 92 71 108Z",
       fill=linear(0, 56, 0, 108, [(0, "#FFE9B04A"), (1, "#FF6E4210")])),
    el("M60 106 C59 94 61 84 59 74 C58 68 60 62 60 58", stroke="#FFE9A3", width=1.3, cap="round"),
    el(ellipse(24, 78, 2.4, 1.2), fill="#FFCC4D", rotate=(-30, 24, 78)),
    el(ellipse(94, 84, 2.4, 1.2), fill="#FFCC4D", rotate=(25, 94, 84)),
    el(ellipse(86, 96, 2, 1), fill="#FFCC4D", rotate=(-15, 86, 96)),
    el(ellipse(32, 94, 2, 1), fill="#FFCC4D", rotate=(40, 32, 94)),
    dot(16, 66, 1.2, "#FFF3C4"), dot(104, 70, 1.2, "#FFF3C4"),
]

STAGES = [SEED, SPROUT, SAPLING, OAK, SILVERBOUGH, WORLD_TREE]


def gradient_xml(g, indent):
    pad = " " * indent
    if g["type"] == "radial":
        head = f'<gradient android:type="radial" android:centerX="{g["cx"]}" android:centerY="{g["cy"]}" android:gradientRadius="{g["r"]}">'
    else:
        head = f'<gradient android:type="linear" android:startX="{g["x1"]}" android:startY="{g["y1"]}" android:endX="{g["x2"]}" android:endY="{g["y2"]}">'
    items = "".join(f'\n{pad}    <item android:offset="{o}" android:color="{c}" />' for o, c in g["stops"])
    return f"{pad}{head}{items}\n{pad}</gradient>"


def path_xml(e, indent=4):
    pad = " " * indent
    attrs = [f'android:pathData="{e["d"]}"']
    children = ""
    fill = e["fill"]
    if isinstance(fill, str):
        attrs.append(f'android:fillColor="{fill if len(fill) == 9 else fill.upper()}"')
    elif isinstance(fill, dict):
        children = f'\n{pad}    <aapt:attr name="android:fillColor">\n{gradient_xml(fill, indent + 8)}\n{pad}    </aapt:attr>\n{pad}'
    if e["stroke"]:
        attrs.append(f'android:strokeColor="{e["stroke"]}"')
        attrs.append(f'android:strokeWidth="{e["width"]}"')
        if e["cap"]:
            attrs.append(f'android:strokeLineCap="{e["cap"]}"')
            attrs.append('android:strokeLineJoin="round"')
        if e["opacity"] != 1.0:
            attrs.append(f'android:strokeAlpha="{e["opacity"]}"')
    if e["opacity"] != 1.0 and e["fill"] is not None:
        attrs.append(f'android:fillAlpha="{e["opacity"]}"')
    attr_text = ("\n" + pad + "    ").join(attrs)
    body = f"{pad}<path\n{pad}    {attr_text}"
    body += f">{children}</path>" if children else " />"
    if e["rotate"]:
        deg, px, py = e["rotate"]
        body = (f'{pad}<group android:rotation="{deg}" android:pivotX="{px}" android:pivotY="{py}">\n'
                + body.replace("\n", "\n    ").replace(pad + "<path", pad + "    <path", 1) + f"\n{pad}</group>")
    return body


def vector(elements, size_dp, viewport, scale=1.0, translate=0.0):
    paths = "\n".join(path_xml(e, 8) for e in elements)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/gen_stage_art.py. Do not edit by hand. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android" {AAPT}\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            f'    <group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{translate}" android:translateY="{translate}">\n'
            f'{paths}\n    </group>\n</vector>\n')


def write(rel, text):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    for n, elements in enumerate(STAGES, start=1):
        write(f"drawable/stage_{n}.xml", vector(elements, 120, 120))
        # Adaptive icons: 108dp canvas, only the middle ~72dp is visible after masking.
        fg = [e for e in elements if not e["bg"]]
        bg = [e for e in elements if e["bg"]]
        write(f"drawable/ic_launcher_stage_{n}_fg.xml", vector(fg, 108, 108, scale=0.55, translate=21))
        write(f"drawable/ic_launcher_stage_{n}_bg.xml", vector(bg, 108, 108, scale=0.9))
        write(f"mipmap-anydpi/ic_launcher_stage_{n}.xml",
              '<?xml version="1.0" encoding="utf-8"?>\n'
              '<!-- Generated by tools/gen_stage_art.py. Do not edit by hand. -->\n'
              '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
              f'    <background android:drawable="@drawable/ic_launcher_stage_{n}_bg" />\n'
              f'    <foreground android:drawable="@drawable/ic_launcher_stage_{n}_fg" />\n'
              '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
              '</adaptive-icon>\n')
    print("generated", len(STAGES), "stages")


if __name__ == "__main__":
    main()
