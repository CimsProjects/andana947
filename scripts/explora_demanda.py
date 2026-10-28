#!/usr/bin/env python3
"""Exploració temporal de les fonts de demanda (PDF de TMB, pàgines de l'OMC, recurs de Renfe del 2018)."""

import io
import json
import pathlib
import re
import urllib.request

from pypdf import PdfReader


def baixa(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 Andana947"})
    return urllib.request.urlopen(req, timeout=120).read()


sortida = pathlib.Path("dades/generades/exploracio")
sortida.mkdir(parents=True, exist_ok=True)
for nom, url in [
    ("tmb_2026", "https://www.tmb.cat/documents/525003/1436181/Dades+b%C3%A0siques+viatgers+bus+metro+2026_CA_EN.pdf/1a89fbd6-773c-2da5-6d98-dd96d6db9556"),
    ("tmb_2025", "https://www.tmb.cat/documents/525003/1436181/Dades+b%C3%A0siques+viatgers+bus+metro+2025_CA_EN.pdf/f1d79e3b-3e0a-cfbc-b570-059359ea8170"),
]:
    try:
        pdf = PdfReader(io.BytesIO(baixa(url)))
        text = "\n".join(f"--- pàgina {i + 1}\n" + (p.extract_text() or "") for i, p in enumerate(pdf.pages))
    except Exception as e:  # noqa: BLE001
        text = f"ERROR {e}"
    (sortida / f"{nom}.txt").write_text(text)

linies = []
for url in [
    "https://omc.cat/en/w/demand-at-railway-stations",
    "https://omc.cat/en/w/demand-per-line-and-railway-operator",
    "https://omc.cat/en/web/observatori/w/annual-demand-on-the-bus-lines-operated-by-tmb",
    "https://omc.cat/w/demand-month-railway-stibcn",
]:
    linies.append(f"=== {url}")
    try:
        html = baixa(url).decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        linies.append(f"ERROR {e}")
        continue
    linies.append(f"{len(html)} caràcters")
    for m in re.finditer(r"[\"']([^\"'\s]*(?:json|csv|xlsx|download|api/)[^\"'\s]*)[\"']", html, re.I):
        linies.append("  " + m.group(1)[:300])
    for m in re.finditer(r".{0,200}(?:jsonData|dataUrl|fetch\(|\.json).{0,400}", html):
        linies.append("  [codi] " + m.group(0).replace("\n", " ")[:600])

r = json.loads(baixa("https://data.renfe.com/api/3/action/resource_show?id=10c14d86-c031-4c99-b8e3-bbc8994b6024"))["result"]
linies.append("=== Renfe 2018: " + r["url"] + " " + r.get("format", ""))
try:
    linies += baixa(r["url"]).decode("utf-8-sig", "replace").splitlines()[:8]
except Exception as e:  # noqa: BLE001
    linies.append(f"ERROR {e}")
(sortida / "enllacos.txt").write_text("\n".join(linies) + "\n")
