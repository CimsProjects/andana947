#!/usr/bin/env python3
"""Baixa totes les fonts de dades/fonts.json a font/ (carpeta que no es puja al repositori).

    python3 scripts/baixa_fonts.py [id ...]          # totes, o només les indicades
    TMB_APP_ID=… TMB_APP_KEY=… python3 scripts/baixa_fonts.py gtfs_tmb

Si hi ha les claus de TMB a l'entorn, el GTFS de TMB es baixa de l'API oficial; si no, de la còpia de la
Mobility Database. Les claus no es desen enlloc.
"""

import json
import pathlib
import sys
import urllib.request

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana import fonts  # noqa: E402


def baixa_geojson_paginat(url, desti):
    """Els serveis OGC API Features retornen pàgines amb un enllaç `next`: les ajunta en una col·lecció."""
    elements = []
    while url:
        peticio = urllib.request.Request(url, headers={"User-Agent": fonts.AGENT, "Accept": "application/geo+json"})
        with urllib.request.urlopen(peticio, timeout=300) as resposta:
            pagina = json.load(resposta)
        elements += pagina.get("features", [])
        url = next((e["href"] for e in pagina.get("links", []) if e.get("rel") == "next"), None)
        print(f"  {len(elements)} elements", flush=True)
    pathlib.Path(desti).write_text(json.dumps({"type": "FeatureCollection", "features": elements}), encoding="utf-8")


def baixa(font):
    desti = fonts.cami_local(font)
    if font["tipus"] == "geojson":
        baixa_geojson_paginat(font["url"], desti)
    elif font["tipus"] == "ckan":
        peticio = urllib.request.Request(font["url"], headers={"User-Agent": fonts.AGENT})
        with urllib.request.urlopen(peticio, timeout=120) as resposta:
            recurs = json.load(resposta)["result"]
        fonts.baixa_url(recurs["url"], desti)
    else:
        url = fonts.url_de(font)
        try:
            fonts.baixa_url(url, desti)
        except Exception as error:  # noqa: BLE001
            if url == font["url"]:
                raise
            print(f"  L'URL oficial ha fallat ({type(error).__name__}); provo la còpia: {font['url']}")
            fonts.baixa_url(font["url"], desti)
    return desti


def main(ids):
    errors = []
    for font in fonts.llegeix()["fonts"]:
        if ids and font["id"] not in ids:
            continue
        print(f"{font['id']}: {font['nom']}", flush=True)
        try:
            desti = baixa(font)
            print(f"  {desti.relative_to(ARREL)}: {desti.stat().st_size:,} bytes".replace(",", "."))
        except Exception as error:  # noqa: BLE001
            errors.append(font["id"])
            print(f"  ERROR: {type(error).__name__}: {error}")
    if errors:
        print("No s'han pogut baixar: " + ", ".join(errors))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
