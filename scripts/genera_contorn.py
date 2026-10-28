#!/usr/bin/env python3
"""Genera el contorn de Catalunya per al mapa de l'app a partir dels límits municipals de dades/territori/.

    python3 scripts/genera_contorn.py

Escriu app/src/main/assets/mapa/catalunya.geojson: un sol polígon que cobreix tot el món menys Catalunya (els forats
són el territori, Llívia inclosa). L'estil del mapa el fa servir per tapar el que queda fora i per dibuixar-ne la vora.
A les propietats hi ha la capsa (oest, sud, est, nord) que l'app fa servir per limitar la càmera.
"""

import json
import pathlib

from shapely.geometry import shape
from shapely.ops import unary_union

ARREL = pathlib.Path(__file__).resolve().parent.parent
MUNICIPIS = ARREL / "dades" / "territori" / "municipis_catalunya.geojson"
SORTIDA = ARREL / "app" / "src" / "main" / "assets" / "mapa" / "catalunya.geojson"

# Uns 100 m: a l'escala de tot Catalunya no es nota i el fitxer queda petit.
TOLERANCIA_GRAUS = 0.001
DECIMALS = 4
# Les illes i els trossos de menys d'1 ha (en graus quadrats, aproximat) són soroll de la unió.
AREA_MINIMA = 1e-6
# El món sencer en Web Mercator (MapLibre no dibuixa més enllà de ±85,0511°).
MON = [[-180.0, -85.0511], [180.0, -85.0511], [180.0, 85.0511], [-180.0, 85.0511], [-180.0, -85.0511]]


def contorn(municipis):
    """La unió dels municipis, simplificada: una llista de polígons."""
    unio = unary_union([shape(f["geometry"]).buffer(0) for f in municipis["features"]])
    simplificat = unio.simplify(TOLERANCIA_GRAUS, preserve_topology=True)
    parts = list(simplificat.geoms) if simplificat.geom_type == "MultiPolygon" else [simplificat]
    return [p for p in parts if p.area >= AREA_MINIMA]


def anell(coordenades):
    return [[round(x, DECIMALS), round(y, DECIMALS)] for x, y in coordenades]


def mascara(parts):
    """El món amb Catalunya com a forats. Els forats interiors de Catalunya (si n'hi ha) no es tapen."""
    oest, sud, est, nord = unary_union(parts).bounds
    return {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {"capsa": [round(oest, DECIMALS), round(sud, DECIMALS), round(est, DECIMALS), round(nord, DECIMALS)]},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [MON] + [anell(p.exterior.coords) for p in parts],
                },
            }
        ],
    }


def main():
    municipis = json.loads(MUNICIPIS.read_text(encoding="utf-8"))
    resultat = mascara(contorn(municipis))
    SORTIDA.write_text(json.dumps(resultat, separators=(",", ":")) + "\n", encoding="utf-8")
    capsa = resultat["features"][0]["properties"]["capsa"]
    forats = len(resultat["features"][0]["geometry"]["coordinates"]) - 1
    print(f"{SORTIDA.relative_to(ARREL)}: {forats} parts, capsa {capsa}, {SORTIDA.stat().st_size} bytes")


if __name__ == "__main__":
    main()
