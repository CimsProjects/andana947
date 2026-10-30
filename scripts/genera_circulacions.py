#!/usr/bin/env python3
"""Genera els horaris i els traçats dels trens i dels busos que l'app mou pel mapa (docs/vehicles.md).

    python3 scripts/baixa_fonts.py gtfs_tmb gtfs_fgc gtfs_rodalies gtfs_renfe_md gtfs_trambaix gtfs_trambesos \
        gtfs_amb gtfs_interurbans
    python3 scripts/genera_circulacions.py

Fa servir les línies de dades/generades/linies.csv (les mateixes que el joc) dels modes de tren (metro, FGC,
Rodalies, regionals i TRAM) i de bus (TMB, AMB i interurbans de la Generalitat), i només la part dels recorreguts
que és dins de Catalunya. Escriu:
    dades/generades/app/circulacions.json      el fitxer que l'app porta a dins (assets)
    dades/generades/informe_circulacions.md    dies de referència i recomptes
"""

import csv
import datetime as dt
import json
import pathlib
import sys

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from shapely import prepared  # noqa: E402
from shapely.geometry import Point, shape  # noqa: E402
from shapely.ops import unary_union  # noqa: E402

from andana import fonts  # noqa: E402
from andana.circulacions import TIPUS_DIA, Circulacions  # noqa: E402

SORTIDA = ARREL / "dades" / "generades"
MODES_TREN = {"metro", "fgc", "rodalies", "regional", "tram"}
MODES_VEHICLES = MODES_TREN | {"bus"}
# Marge del límit de Catalunya, en graus (uns 200 m): les vies que segueixen la costa o el límit no surten i entren.
MARGE_LIMIT = 0.002


def dins_de_catalunya(cami=ARREL / "dades" / "territori" / "municipis_catalunya.geojson"):
    """Una funció (lat, lon) -> si el punt és a Catalunya, amb els límits municipals de l'IGN."""
    municipis = json.loads(pathlib.Path(cami).read_text(encoding="utf-8"))["features"]
    catalunya = unary_union([shape(f["geometry"]).buffer(0) for f in municipis]).buffer(MARGE_LIMIT)
    prepared.prep(catalunya)
    return lambda lat, lon: catalunya.contains(Point(lon, lat))


def linies_amb_vehicles(cami=SORTIDA / "linies.csv", modes=MODES_VEHICLES):
    """{feed: {route_id: id de línia}} i les files de linies.csv, només dels [modes] que es mouen pel mapa."""
    per_feed, files = {}, {}
    with open(cami, encoding="utf-8", newline="") as f:
        for fila in csv.DictReader(f):
            if fila["mode"] not in modes:
                continue
            prefix, ruta = fila["id"].split(":", 1)
            per_feed.setdefault(f"gtfs_{prefix}", {})[ruta] = fila["id"]
            files[fila["id"]] = fila
    return per_feed, files


def main():
    per_feed, files = linies_amb_vehicles()
    circulacions = Circulacions(linies=files)
    dins = dins_de_catalunya()
    for id_feed, linies in sorted(per_feed.items()):
        cami = fonts.cami_local(fonts.per_id(id_feed))
        if not cami.exists():
            circulacions.diagnosi.append(f"**Falta** {id_feed}: no s'ha baixat.")
            continue
        print(f"{id_feed}: {len(linies)} línies", flush=True)
        circulacions.afegeix_feed(cami, id_feed, linies, des_de=dt.date.today(), dins=dins)

    dades = circulacions.a_json(dt.date.today().isoformat())
    desti = SORTIDA / "app" / "circulacions.json"
    desti.parent.mkdir(parents=True, exist_ok=True)
    desti.write_text(json.dumps(dades, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    informe = [
        "# Circulacions dels trens i dels busos",
        "",
        f"Generat el {dt.date.today():%d/%m/%Y} amb `scripts/genera_circulacions.py`. "
        "Posició *estimada* segons l'horari (docs/vehicles.md).",
        "",
    ]
    informe += [f"- {linia}" for linia in circulacions.diagnosi]
    informe += [
        f"- {len(dades['linies'])} línies, {len(dades['tracats'])} traçats, {len(dades['patrons'])} patrons.",
        "- Expedicions: " + ", ".join(f"{t} {len(dades['expedicions'][t]) // 2}" for t in TIPUS_DIA) + ".",
        f"- `app/circulacions.json`: {desti.stat().st_size / 1e6:.1f} MB.",
        "",
    ]
    (SORTIDA / "informe_circulacions.md").write_text("\n".join(informe), encoding="utf-8")
    print("\n".join(informe))
    return 0


if __name__ == "__main__":
    sys.exit(main())
