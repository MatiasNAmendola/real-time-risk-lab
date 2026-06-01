#!/usr/bin/env python3
"""Small local exporter for the process-map Excalidraw JSON.

It intentionally supports only the subset used by
product-architecture-process-map.excalidraw: rectangles, ellipses, arrows and
plain text. The goal is a stable static SVG preview without pulling a heavy
browser/canvas dependency into the repo.
"""
from __future__ import annotations

import html
import json
import textwrap
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
EXCALIDRAW = ROOT / "product-architecture-process-map.excalidraw"
SVG = ROOT / "product-architecture-process-map.svg"
PAD = 40


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def wrap_lines(text: str, width: float, font_size: float) -> list[str]:
    max_chars = max(4, int(width / (font_size * 0.54)))
    lines: list[str] = []
    for raw in text.split("\n"):
        raw = raw.strip()
        if not raw:
            lines.append("")
            continue
        lines.extend(textwrap.wrap(raw, width=max_chars, break_long_words=False, replace_whitespace=False) or [""])
    return lines


def main() -> None:
    scene = json.loads(EXCALIDRAW.read_text())
    elements = [e for e in scene["elements"] if not e.get("isDeleted")]
    min_x = min(e["x"] for e in elements)
    min_y = min(e["y"] for e in elements)
    max_x = max(e["x"] + e.get("width", 0) for e in elements)
    max_y = max(e["y"] + e.get("height", 0) for e in elements)
    width = int(max_x - min_x + PAD * 2)
    height = int(max_y - min_y + PAD * 2)

    def sx(x: float) -> float:
        return x - min_x + PAD

    def sy(y: float) -> float:
        return y - min_y + PAD

    svg: list[str] = []
    svg.append(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" preserveAspectRatio="xMinYMin meet" '
        'style="max-width:none;background:#fff" role="img" '
        'aria-label="Mapa visual de proceso producto arquitectura">'
    )
    svg.append("<defs>")
    svg.append(
        '<marker id="arrowhead" markerWidth="10" markerHeight="10" refX="9" refY="3" '
        'orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="context-stroke"/></marker>'
    )
    svg.append(
        '<filter id="shadow" x="-10%" y="-10%" width="120%" height="120%">'
        '<feDropShadow dx="0" dy="4" stdDeviation="6" flood-color="#000" flood-opacity="0.08"/></filter>'
    )
    svg.append("</defs>")
    svg.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    svg.append(
        "<style><![CDATA[text{font-family:Inter,Arial,sans-serif;dominant-baseline:middle}"
        ".label{font-weight:700}.small{font-weight:700}.phase-title{font-weight:800}]]></style>"
    )

    for e in elements:
        if e["type"] == "rectangle":
            fill = e.get("backgroundColor", "transparent")
            stroke = e.get("strokeColor", "#111")
            fill = "none" if fill == "transparent" else fill
            stroke = "none" if stroke == "transparent" else stroke
            rx = 20 if e.get("roundness") else 0
            filt = (
                ' filter="url(#shadow)"'
                if e.get("width", 0) > 500 and e.get("height", 0) > 40 and fill != "none"
                else ""
            )
            svg.append(
                f'<rect x="{sx(e["x"]):.1f}" y="{sy(e["y"]):.1f}" width="{e["width"]:.1f}" '
                f'height="{e["height"]:.1f}" rx="{rx}" fill="{fill}" stroke="{stroke}" '
                f'stroke-width="{e.get("strokeWidth", 1)}"{filt}/>'
            )
        elif e["type"] == "ellipse":
            fill = e.get("backgroundColor", "transparent")
            fill = "none" if fill == "transparent" else fill
            svg.append(
                f'<ellipse cx="{sx(e["x"] + e["width"] / 2):.1f}" cy="{sy(e["y"] + e["height"] / 2):.1f}" '
                f'rx="{e["width"] / 2:.1f}" ry="{e["height"] / 2:.1f}" fill="{fill}" '
                f'stroke="{e.get("strokeColor", "#111")}" stroke-width="{e.get("strokeWidth", 1)}"/>'
            )

    for e in elements:
        if e["type"] != "arrow":
            continue
        x1, y1 = sx(e["x"]), sy(e["y"])
        x2, y2 = sx(e["x"] + e.get("width", 0)), sy(e["y"] + e.get("height", 0))
        svg.append(
            f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
            f'stroke="{e.get("strokeColor", "#999")}" stroke-width="{e.get("strokeWidth", 2)}" '
            'stroke-linecap="round" marker-end="url(#arrowhead)"/>'
        )

    for e in elements:
        if e["type"] != "text":
            continue
        font_size = float(e.get("fontSize", 12))
        x, y = sx(e["x"]), sy(e["y"])
        box_width = float(e.get("width", 100))
        box_height = float(e.get("height", font_size * 1.2))
        lines = wrap_lines(e.get("text", ""), box_width, font_size)
        line_height = font_size * 1.18
        max_lines = max(1, int(box_height / line_height))
        if len(lines) > max_lines:
            lines = lines[:max_lines]
            lines[-1] = lines[-1][: max(1, int(box_width / (font_size * 0.6)) - 1)] + "…"
        total_height = line_height * len(lines)
        start_y = y + box_height / 2 - total_height / 2 + line_height / 2
        text_x = x + box_width / 2
        css_class = "phase-title" if font_size >= 18 else ("small" if font_size <= 11 else "label")
        color = e.get("strokeColor", "#111")
        for index, line in enumerate(lines):
            svg.append(
                f'<text class="{css_class}" x="{text_x:.1f}" y="{start_y + index * line_height:.1f}" '
                f'text-anchor="middle" font-size="{font_size:.1f}" fill="{color}">{esc(line)}</text>'
            )

    svg.append("</svg>")
    SVG.write_text("\n".join(svg))
    ET.parse(SVG)
    print(f"Wrote {SVG} ({width}x{height}) from {len(elements)} elements")


if __name__ == "__main__":
    main()
