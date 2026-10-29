#!/usr/bin/env python3
"""Genera els trams del joc (GDD 4.0) a partir de les línies i les parades de dades/generades/.

    python3 scripts/genera_trams.py

Escriu:
    trams.csv           cada tram: les seves línies, parades, arribades d'un feiner, mida i preu
    app/trams.json      el mateix, per a l'app (els dibuixa al mapa amb els traçats de circulacions.json)
    informe_trams.md    els talls de mida i els recomptes
"""

import csv
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from andana.trams import GRAN, MITJA, PETIT, agrupa, classifica  # noqa: E402

ARREL = pathlib.Path(__file__).resolve().parent.parent
GENERADES = ARREL / "dades" / "generades"


def llegeix_csv(nom):
    with open(GENERADES / nom, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def main():
    config = json.loads((ARREL / "dades" / "configuracio_joc.json").read_text(encoding="utf-8"))["trams"]
    recompenses = {PETIT: [config["recompensaPetitMinima"], config["recompensaPetitMaxima"]],
                   MITJA: [config["recompensaMitjaMinima"], config["recompensaMitjaMaxima"]]}
    noms_nodes = {n["id"]: n["nom"] for n in llegeix_csv("nodes.csv")}
    trams = agrupa(llegeix_csv("linies.csv"), llegeix_csv("linia_nodes.csv"), noms_nodes)
    jugables, (tall_mitja, tall_gran) = classifica(trams, config)

    with open(GENERADES / "trams.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "operador", "mode", "codi", "nom", "color", "parades", "arribades_feiner", "mida", "preu",
                    "linies"])
        for t in jugables:
            w.writerow([t.id, t.operador, t.mode, t.codi, t.nom, t.color, t.parades, t.arribades_feiner, t.mida,
                        "" if t.preu is None else t.preu, ";".join(t.linies)])

    app = {
        "versio": 1,
        "descripcio": "Els trams del joc (GDD 4.0), generats per scripts/genera_trams.py. El preu i la mida que "
                      "manen són els del servidor.",
        # Per mostrar-los a la fitxa i per al compte local de prova (sense servidor), de configuracio_joc.json.
        "capitalInicial": config["capitalInicial"],
        "recompenses": recompenses,
        "trams": [
            {"id": t.id, "codi": t.codi, "nom": t.nom, "mode": t.mode, "color": t.color, "mida": t.mida,
             "preu": t.preu, "parades": t.parades, "arribades": t.arribades_feiner, "linies": t.linies}
            for t in jugables
        ],
    }
    (GENERADES / "app" / "trams.json").write_text(json.dumps(app, ensure_ascii=False, separators=(",", ":")) + "\n",
                                                  encoding="utf-8")

    per_mida = {m: [t for t in jugables if t.mida == m] for m in (PETIT, MITJA, GRAN)}
    noms_mida = {PETIT: "Petits", MITJA: "Mitjans", GRAN: "Grans"}
    linies = [
        "# Trams del joc",
        "",
        "Generat per `scripts/genera_trams.py` (GDD 4.0). Un tram és una línia real amb tots els seus sentits i "
        "variants. La mida surt de les arribades a andana d'un feiner, partides en terços.",
        "",
        f"- Trams: **{len(jugables)}** (se'n descarten {len(trams) - len(jugables)} de menys de "
        f"{config['minimParades']} parades o sense servei).",
        f"- Talls: mitjà a partir de **{tall_mitja}** arribades, gran a partir de **{tall_gran}**.",
        "",
        "| Mida | Trams | Preu mínim | Preu màxim |",
        "|---|---:|---:|---:|",
    ]
    for m, del_tipus in per_mida.items():
        preus = [t.preu for t in del_tipus if t.preu is not None]
        linies.append(f"| {noms_mida[m]} | {len(del_tipus)} | {min(preus) if preus else '—'} | "
                      f"{max(preus) if preus else '—'} |")
    (GENERADES / "informe_trams.md").write_text("\n".join(linies) + "\n", encoding="utf-8")
    print(f"{len(jugables)} trams: {len(per_mida[PETIT])} petits, {len(per_mida[MITJA])} mitjans i "
          f"{len(per_mida[GRAN])} grans (talls {tall_mitja} i {tall_gran}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
