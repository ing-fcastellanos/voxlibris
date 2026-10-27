#!/usr/bin/env python3
"""Genera la capa de tokens de la app a partir de design/tokens.json.

design/tokens.json es una copia de project/tokens.json del Sistema de diseño VoxLibris
(ver CLAUDE.md). Este script escribe:

  app/src/main/java/com/fcastellanos/voxlibris/ui/theme/Tokens.kt
  app/src/main/res/values/vx_colors.xml        (primer tema, claro)
  app/src/main/res/values-night/vx_colors.xml  (tema oscuro)

Uso:
  python3 tools/gen_tokens.py           regenera los archivos
  python3 tools/gen_tokens.py --check   termina con error si no están al día
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "design" / "tokens.json"
KT = ROOT / "app/src/main/java/com/fcastellanos/voxlibris/ui/theme/Tokens.kt"
XML_DIR = {"light": ROOT / "app/src/main/res/values", "dark": ROOT / "app/src/main/res/values-night"}

HEADER = (
    "Archivo generado por tools/gen_tokens.py a partir de design/tokens.json. No lo edites a mano.\n"
    "Para cambiar un valor, cambialo en el Sistema de diseño VoxLibris, copiá su tokens.json a\n"
    "design/tokens.json y corré: python3 tools/gen_tokens.py"
)


def camel(name):
    parts = re.split(r"[-.]", name)
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def pascal(name):
    c = camel(name)
    return c[:1].upper() + c[1:]


def snake(name):
    return re.sub(r"[-.]", "_", name)


def kdoc(text, indent="    "):
    return f"{indent}/** {text.replace('*/', '* /')} */\n"


def num(x):
    x = float(x)
    return str(int(x)) if x == int(x) else repr(x)


def length(v):
    v = str(v).strip()
    m = re.fullmatch(r"(-?[\d.]+)(px)?", v)
    if not m:
        raise SystemExit(f"Longitud que no entiendo: {v!r}")
    return float(m.group(1))


def parse_color(v):
    """Devuelve (r, g, b, a) con r, g, b en 0..255 y a en 0..1."""
    v = v.strip().lower()
    if v.startswith("#"):
        h = v[1:]
        if len(h) in (3, 4):
            h = "".join(ch * 2 for ch in h)
        if len(h) not in (6, 8):
            raise SystemExit(f"Color que no entiendo: {v!r}")
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a
    m = re.fullmatch(r"rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)", v)
    if m:
        a = float(m.group(4)) if m.group(4) is not None else 1.0
        return int(float(m.group(1))), int(float(m.group(2))), int(float(m.group(3))), a
    raise SystemExit(f"Color que no entiendo: {v!r}")


def argb(rgba):
    r, g, b, a = rgba
    return f"{round(a * 255):02X}{r:02X}{g:02X}{b:02X}"


def kt_color(rgba):
    return f"Color(0x{argb(rgba)})"


def resolve(tokens, themes):
    """Valor de cada token por tema, con los alias {otro} resueltos y la herencia del primer tema."""
    first = themes[0]
    raw = {t["name"]: t["value"] for t in tokens}

    def value(name, theme, seen=()):
        if name in seen:
            raise SystemExit(f"Alias circular en {name}")
        v = raw[name]
        if isinstance(v, dict):
            v = v.get(theme, v.get(first))
        if isinstance(v, str) and v.startswith("{") and v.endswith("}"):
            return value(v[1:-1], theme, seen + (name,))
        return v

    return {t["name"]: {th: value(t["name"], th) for th in themes} for t in tokens}


def parse_shadow(v):
    m = re.fullmatch(r"\s*(-?[\d.]+)(?:px)?\s+(-?[\d.]+)(?:px)?\s+([\d.]+)(?:px)?\s+(.+?)\s*", v)
    if not m:
        raise SystemExit(f"Sombra que no entiendo: {v!r}")
    return float(m.group(1)), float(m.group(2)), float(m.group(3)), parse_color(m.group(4))


def generate(data):
    themes = [t["id"] for t in data["color"]["themes"]]
    theme_names = {t["id"]: t.get("name", t["id"]) for t in data["color"]["themes"]}
    colors = data["color"]["tokens"]
    color_values = resolve(colors, themes)

    out = ["// " + line for line in HEADER.splitlines()]
    out.append("")
    out.append("package com.fcastellanos.voxlibris.ui.theme")
    out.append("")
    for imp in [
        "androidx.compose.runtime.Immutable",
        "androidx.compose.ui.graphics.Color",
        "androidx.compose.ui.text.TextStyle",
        "androidx.compose.ui.text.font.FontWeight",
        "androidx.compose.ui.unit.Dp",
        "androidx.compose.ui.unit.TextUnit",
        "androidx.compose.ui.unit.dp",
        "androidx.compose.ui.unit.sp",
    ]:
        out.append(f"import {imp}")
    out.append("")

    # Colores
    out.append("/** Colores del Sistema de diseño VoxLibris para un tema. */")
    out.append("@Immutable")
    out.append("data class VoxColors(")
    for t in colors:
        out.append(kdoc(t.get("usage", "")).rstrip("\n"))
        out.append(f"    val {camel(t['name'])}: Color,")
    out.append(")")
    for th in themes:
        out.append("")
        out.append(f"/** Tema {theme_names[th]}. */")
        out.append(f"val {pascal(th)}VoxColors = VoxColors(")
        for t in colors:
            out.append(f"    {camel(t['name'])} = {kt_color(parse_color(color_values[t['name']][th]))},")
        out.append(")")

    # Medidas
    out.append("")
    out.append("/** Espacios, radios y tamaños del sistema, en dp. */")
    out.append("object VoxDimens {")
    for family in ("spacing", "radius", "size"):
        for t in data.get(family, {}).get("tokens", []):
            out.append(kdoc(t.get("usage", "")).rstrip("\n"))
            out.append(f"    val {camel(t['name'])}: Dp = {num(length(t['value']))}.dp")
    out.append("}")

    # Sombras
    shadows = data.get("shadow", {}).get("tokens", [])
    if shadows:
        shadow_values = {}
        for t in shadows:
            v = t["value"]
            shadow_values[t["name"]] = {th: (v.get(th, v.get(themes[0])) if isinstance(v, dict) else v) for th in themes}
        out.append("")
        out.append("/** Una sombra del sistema: desplazamiento, desenfoque y color, como en CSS. */")
        out.append("@Immutable")
        out.append("data class VoxShadow(val offsetX: Dp, val offsetY: Dp, val blur: Dp, val color: Color)")
        out.append("")
        out.append("/** Sombras del sistema para un tema. Solo para lo que flota. */")
        out.append("@Immutable")
        out.append("data class VoxShadows(")
        for t in shadows:
            out.append(kdoc(t.get("usage", "")).rstrip("\n"))
            out.append(f"    val {camel(t['name'])}: VoxShadow,")
        out.append(")")
        for th in themes:
            out.append("")
            out.append(f"/** Sombras del tema {theme_names[th]}. */")
            out.append(f"val {pascal(th)}VoxShadows = VoxShadows(")
            for t in shadows:
                x, y, blur, color = parse_shadow(shadow_values[t["name"]][th])
                out.append(f"    {camel(t['name'])} = VoxShadow({num(x)}.dp, {num(y)}.dp, {num(blur)}.dp, {kt_color(color)}),")
            out.append(")")

    # Tipografía
    families = list(data["type"]["families"].keys())
    styles = []
    for group in data["type"]["groups"]:
        for s in group["styles"]:
            styles.append((s, s.get("family", group["family"])))
    out.append("")
    out.append("/** Familia tipográfica de un estilo. Type.kt decide qué fuente usa cada una. */")
    out.append("enum class VoxFontRole { " + ", ".join(pascal(f) for f in families) + " }")
    out.append("")
    out.append("/** Métricas de un estilo de texto del sistema, sin la fuente. */")
    out.append("@Immutable")
    out.append("data class VoxTextSpec(")
    out.append("    val role: VoxFontRole,")
    out.append("    val fontSize: TextUnit,")
    out.append("    val lineHeight: TextUnit,")
    out.append("    val fontWeight: FontWeight,")
    out.append("    val letterSpacing: TextUnit,")
    out.append(")")
    out.append("")
    out.append("/** Estilos de texto del sistema. */")
    out.append("object VoxTextSpecs {")
    for s, fam in styles:
        lh = s["lineHeight"]
        lh_sp = f"{num(length(lh))}.sp" if str(lh).endswith("px") else f"({num(lh)} * {num(length(s['fontSize']))}).sp"
        ls = f"({num(length(s['letterSpacing']))}).sp" if "letterSpacing" in s else "0.sp"
        weight = s.get("fontWeight", 400)
        out.append(kdoc(s.get("usage", s["name"])).rstrip("\n"))
        out.append(
            f"    val {camel(s['name'])} = VoxTextSpec(VoxFontRole.{pascal(fam)}, {num(length(s['fontSize']))}.sp, "
            f"{lh_sp}, FontWeight({int(weight)}), {ls})"
        )
    out.append("}")
    out.append("")
    out.append("/** Estilos de texto del sistema, ya resueltos con su fuente. */")
    out.append("@Immutable")
    out.append("data class VoxTypography(")
    for s, _ in styles:
        out.append(kdoc(s.get("usage", s["name"])).rstrip("\n"))
        out.append(f"    val {camel(s['name'])}: TextStyle,")
    out.append(") {")
    out.append("    companion object {")
    out.append("        /** Arma la tipografía convirtiendo cada [VoxTextSpec] con [style]. */")
    out.append("        fun build(style: (VoxTextSpec) -> TextStyle) = VoxTypography(")
    for s, _ in styles:
        out.append(f"            {camel(s['name'])} = style(VoxTextSpecs.{camel(s['name'])}),")
    out.append("        )")
    out.append("    }")
    out.append("}")
    kt = "\n".join(out) + "\n"

    xmls = {}
    for th in themes:
        if th not in XML_DIR:
            continue
        lines = ['<?xml version="1.0" encoding="utf-8"?>']
        lines.append("<!-- " + HEADER.replace("\n", " ") + " -->")
        lines.append("<resources>")
        for t in colors:
            lines.append(f'    <color name="vx_{snake(t["name"])}">#{argb(parse_color(color_values[t["name"]][th]))}</color>')
        lines.append("</resources>")
        xmls[XML_DIR[th] / "vx_colors.xml"] = "\n".join(lines) + "\n"
    return {KT: kt, **xmls}


def main():
    check = "--check" in sys.argv[1:]
    data = json.loads(SRC.read_text(encoding="utf-8"))
    stale = []
    for path, text in generate(data).items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current == text:
            continue
        if check:
            stale.append(path.relative_to(ROOT))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            print(f"escrito {path.relative_to(ROOT)}")
    if stale:
        print("Desactualizados respecto de design/tokens.json: " + ", ".join(map(str, stale)))
        print("Corré: python3 tools/gen_tokens.py")
        sys.exit(1)


if __name__ == "__main__":
    main()
