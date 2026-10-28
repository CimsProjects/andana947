"""Registre de fonts (dades/fonts.json) i descàrrega a font/."""

import json
import os
import pathlib
import shutil
import urllib.parse
import urllib.request

ARREL = pathlib.Path(__file__).resolve().parent.parent.parent
FITXER_FONTS = ARREL / "dades" / "fonts.json"
CARPETA_FONT = ARREL / "font"
AGENT = "Andana947-dades/1.0 (+https://github.com/CimsProjects/andana947)"


def llegeix(fitxer=FITXER_FONTS):
    return json.loads(pathlib.Path(fitxer).read_text(encoding="utf-8"))


def per_id(id_font, fitxer=FITXER_FONTS):
    for font in llegeix(fitxer)["fonts"]:
        if font["id"] == id_font:
            return font
    raise KeyError(f"No hi ha cap font amb l'identificador {id_font}")


def url_de(font, entorn=None):
    """URL que cal baixar. Si la font té una URL oficial amb claus i les claus són a l'entorn, la fa servir."""
    entorn = os.environ if entorn is None else entorn
    claus = font.get("parametresClau", {})
    if font.get("urlOficial") and claus and all(entorn.get(v) for v in claus.values()):
        return font["urlOficial"] + "?" + urllib.parse.urlencode({p: entorn[v] for p, v in claus.items()})
    return font["url"]


def cami_local(font, carpeta=CARPETA_FONT):
    extensio = {"gtfs": ".zip", "geojson": ".geojson", "csv": ".csv", "ckan": ".csv"}.get(font["tipus"], "")
    return pathlib.Path(carpeta) / f"{font['id']}{extensio}"


def baixa_url(url, desti):
    desti = pathlib.Path(desti)
    desti.parent.mkdir(parents=True, exist_ok=True)
    temporal = desti.with_suffix(desti.suffix + ".part")
    peticio = urllib.request.Request(url, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(peticio, timeout=600) as resposta, open(temporal, "wb") as f:
        shutil.copyfileobj(resposta, f)
    temporal.replace(desti)
    return desti
