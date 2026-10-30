#!/usr/bin/env python3
"""Genera la flota de cada línia per a la fase 5 (scripts/andana/flota.py).

    python3 scripts/genera_flota.py

Llegeix dades/generades/{linies,linia_nodes}.csv i dades/generades/app/circulacions.json i escriu:
    dades/generades/flota_linies.csv    linia (mode:codi), mode, codi, vehicles en hora punta, expedicions, km i viatgers
                                        d'un feiner i nodes de la línia
    dades/generades/informe_flota.md    recomptes
"""

import csv
import datetime as dt
import json
import pathlib
import sys
from collections import defaultdict

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana.flota import flota_per_linia  # noqa: E402

GENERADES = ARREL / "dades" / "generades"


def main():
    with open(GENERADES / "app" / "circulacions.json", encoding="utf-8") as f:
        circulacions = json.load(f)
    flota = flota_per_linia(circulacions)

    with open(GENERADES / "linies.csv", encoding="utf-8") as f:
        linies = list(csv.DictReader(f))
    clau_de = {l["id"]: f"{l['mode']}:{l['codi']}" for l in linies}
    # L'operador de la línia (el de la ruta amb més expedicions): tria el model de vehicle.
    operador = {}
    for l in sorted(linies, key=lambda l: -int(l["expedicions_feiner"] or 0)):
        operador.setdefault(clau_de[l["id"]], l["operador"])
    viatgers = defaultdict(float)
    for l in linies:
        viatgers[clau_de[l["id"]]] += float(l["viatgers_feiner"] or 0)
    # Les línies de tren no tenen viatgers propis (l'afluència és de cada estació): els de la línia són els de les seves
    # estacions, repartits entre les línies que hi paren segons les expedicions de cadascuna [estimat].
    with open(GENERADES / "nodes.csv", encoding="utf-8") as f:
        node = {r["id"]: (float(r["viatgers_feiner"] or 0), int(r["expedicions_feiner"] or 0)) for r in csv.DictReader(f)}
    nodes = defaultdict(set)
    per_estacions = defaultdict(float)
    with open(GENERADES / "linia_nodes.csv", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["linia_id"] not in clau_de or r["node_id"] not in node:
                continue
            clau = clau_de[r["linia_id"]]
            nodes[clau].add(r["node_id"])
            v, exp = node[r["node_id"]]
            if exp > 0:
                per_estacions[clau] += v * int(r["expedicions_feiner"]) / exp
    for clau, v in per_estacions.items():
        if viatgers[clau] <= 0:
            viatgers[clau] = v

    files = []
    for clau, f in sorted(flota.items()):
        if f["vehicles_punta"] <= 0 or clau not in nodes:
            continue
        mode, codi = clau.split(":", 1)
        files.append([clau, mode, codi, operador.get(clau, ""), f["vehicles_punta"], f["expedicions"], round(f["km"], 1),
                      round(viatgers[clau], 1), len(nodes[clau])])
    with open(GENERADES / "flota_linies.csv", "w", encoding="utf-8", newline="") as fitxer:
        w = csv.writer(fitxer, lineterminator="\n")
        w.writerow(["linia", "mode", "codi", "operador", "vehicles_punta", "expedicions_feiner", "km_feiner", "viatgers_feiner", "nodes"])
        w.writerows(files)

    per_mode = defaultdict(int)
    for r in files:
        per_mode[r[1]] += r[4]
    informe = [
        "# Flota de les línies",
        "",
        f"Generat el {dt.date.today():%d/%m/%Y} amb `scripts/genera_flota.py` a partir de `circulacions.json` "
        f"(generat el {circulacions.get('generat', '?')}). Vehicles en hora punta *estimats*: el màxim d'expedicions "
        "d'un feiner que circulen alhora.",
        "",
        f"- {len(files)} línies.",
    ] + [f"- {mode}: {n} vehicles en hora punta." for mode, n in sorted(per_mode.items())] + [""]
    (GENERADES / "informe_flota.md").write_text("\n".join(informe), encoding="utf-8")
    print("\n".join(informe))


if __name__ == "__main__":
    main()
