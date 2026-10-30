#!/usr/bin/env python3
"""Genera els trams de la xarxa: parells d'andanes seguides de cada línia (scripts/andana/trams.py).

    python3 scripts/genera_trams.py

Llegeix dades/generades/{nodes,linia_nodes}.csv i dades/generades/app/circulacions.json (s'han de generar abans
amb genera_dades.py i genera_circulacions.py) i escriu:
    dades/generades/trams.csv            linia_id, node_a, node_b (node_a < node_b)
    dades/generades/informe_trams.md     recomptes
"""

import csv
import datetime as dt
import json
import pathlib
import sys
from collections import defaultdict

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana.trams import trams  # noqa: E402

GENERADES = ARREL / "dades" / "generades"


def llegeix_nodes_per_linia(cami_nodes, cami_linia_nodes):
    with open(cami_nodes, encoding="utf-8") as f:
        posicio = {r["id"]: (float(r["lat"]), float(r["lon"])) for r in csv.DictReader(f)}
    resultat = defaultdict(list)
    with open(cami_linia_nodes, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["node_id"] in posicio:
                resultat[r["linia_id"]].append((r["node_id"], *posicio[r["node_id"]]))
    return resultat


def main():
    nodes_per_linia = llegeix_nodes_per_linia(GENERADES / "nodes.csv", GENERADES / "linia_nodes.csv")
    with open(GENERADES / "app" / "circulacions.json", encoding="utf-8") as f:
        circulacions = json.load(f)
    llista, aparellades, sense_node = trams(circulacions, nodes_per_linia)

    with open(GENERADES / "trams.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["linia_id", "node_a", "node_b"])
        w.writerows(llista)

    linies_amb_trams = {t[0] for t in llista}
    total = aparellades + sense_node
    informe = [
        "# Trams de la xarxa",
        "",
        f"Generat el {dt.date.today():%d/%m/%Y} amb `scripts/genera_trams.py` a partir de `circulacions.json` "
        f"(generat el {circulacions.get('generat', '?')}).",
        "",
        f"- {len(llista)} trams (parells d'andanes seguides) a {len(linies_amb_trams)} línies de {len(nodes_per_linia)}.",
        f"- Parades dels recorreguts aparellades amb un node de la seva línia: {aparellades} de {total}"
        + (f" ({100 * aparellades / total:.1f} %)." if total else "."),
        "",
    ]
    (GENERADES / "informe_trams.md").write_text("\n".join(informe), encoding="utf-8")
    print("\n".join(informe))


if __name__ == "__main__":
    main()
