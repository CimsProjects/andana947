#!/usr/bin/env python3
"""Prepara l'escenari de la simulació del primer mes (GDD, secció 8) amb les dades generades.

    python3 scripts/prepara_simulacio.py      # escriu dades/generades/escenari_primer_mes.json
    gradle -p joc simulacio                   # la simula amb les regles de joc/

La Laia viu al Bon Pastor i treballa a prop de Diagonal (el perfil de la fase 1). Respecte de la fase 1, cada
feiner també fa check-in a les parades de bus més properes a Baró de Viver i a Diagonal, que és el que trobaria
un jugador real al voltant de casa i de la feina.
"""

import csv
import json
import pathlib
import sys

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana.territori import distancia  # noqa: E402
from andana.text import normalitza  # noqa: E402

GENERADES = ARREL / "dades" / "generades"
FEINERS = [1, 2, 3, 4, 5]

# (mode, nom, dies de la setmana, dies concrets de la simulació). L'ordre és el del recorregut.
RUTINA = [
    ("metro", "Baró de Viver", FEINERS, []),
    ("rodalies", "Clot-Aragó", FEINERS, []),
    ("rodalies", "Passeig de Gràcia", FEINERS, []),
    ("metro", "Diagonal", FEINERS, []),
    ("rodalies", "Arc de Triomf", [2], []),
    ("rodalies", "Sants", [3], []),
    ("rodalies", "Plaça de Catalunya", [5], []),
    ("metro", "Catalunya", [5], []),
    ("metro", "Espanya", [6], []),
    ("metro", "Casa de l'Aigua", [], [12, 24]),
]
# Parades de bus: les N més properes a cada una d'aquestes estacions, a menys del radi.
PARADES_BUS = [("Baró de Viver", 3), ("Diagonal", 3)]
RADI_BUS_METRES = 250


def troba(nodes, mode, nom):
    clau = normalitza(nom)
    candidats = [n for n in nodes if n["mode"] == mode]
    for criteri in (lambda c: c == clau, lambda c: c.endswith(" " + clau), lambda c: clau in c):
        trobats = [n for n in candidats if criteri(normalitza(n["nom"]))]
        if trobats:
            return max(trobats, key=lambda n: float(n["viatgers_feiner"]))
    raise SystemExit(f"No trobo el node {nom} ({mode}) a nodes.csv")


def node_escenari(n, municipis, etiqueta):
    municipi = municipis.get(n["municipi"], {})
    return {
        "id": n["id"],
        "nom": etiqueta,
        "mode": n["mode"],
        "lat": float(n["lat"]),
        "lon": float(n["lon"]),
        "viatgersFeiner": round(float(n["viatgers_feiner"]), 1),
        "font": n["font_afluencia"],
        "municipi": n["municipi"] or None,
        "comarca": n["comarca"] or None,
        "densitatMunicipi": float(municipi["densitat"]) if municipi else None,
        "linies": [l for l in n["linies"].split(";") if l],
    }


def etiqueta(n):
    linies = [l for l in n["linies"].split(";") if l]
    if n["mode"] == "bus":
        return f"{n['nom']} (bus {', '.join(linies[:4])}{'…' if len(linies) > 4 else ''})"
    if n["mode"] == "metro":
        return f"{n['nom']} ({', '.join(linies)})"
    return f"{n['nom']} ({n['mode'].capitalize()})"


def main():
    with open(GENERADES / "nodes.csv", encoding="utf-8") as f:
        nodes = list(csv.DictReader(f))
    with open(GENERADES / "municipis.csv", encoding="utf-8") as f:
        municipis = {m["codi"]: m for m in csv.DictReader(f)}
    escollits, visites = {}, []
    for mode, nom, dies_setmana, dies_simulacio in RUTINA:
        n = troba(nodes, mode, nom)
        escollits[n["id"]] = n
        visites.append({"node": n["id"], "diesSetmana": dies_setmana, "diesSimulacio": dies_simulacio})
    busos = [n for n in nodes if n["mode"] == "bus"]
    for nom_estacio, quantes in PARADES_BUS:
        estacio = troba(nodes, "metro", nom_estacio)
        lat, lon = float(estacio["lat"]), float(estacio["lon"])
        a_prop = sorted((distancia(lat, lon, float(b["lat"]), float(b["lon"])), b["id"], b) for b in busos)
        for d, _, b in [x for x in a_prop if x[0] <= RADI_BUS_METRES][:quantes]:
            escollits[b["id"]] = b
            visites.append({"node": b["id"], "diesSetmana": FEINERS, "diesSimulacio": []})
    escenari = {
        "nom": "Simulació del primer mes (fase 2): la Laia, amb parades de bus",
        "descripcio": (
            "La Laia viu al Bon Pastor i treballa a prop de Diagonal. Cada feiner fa check-in a Baró de Viver, "
            "Clot-Aragó, Passeig de Gràcia i Diagonal, i a les tres parades de bus més properes a Baró de Viver "
            f"i a Diagonal (a menys de {RADI_BUS_METRES} m). Els dimarts passa per Arc de Triomf, els dimecres per "
            "Sants, els divendres per Plaça de Catalunya i Catalunya, els dissabtes per Espanya, i els dies 12 i "
            "24 visita Casa de l'Aigua. Comença el dilluns 2 de novembre de 2026 amb el capital inicial. Sense "
            "caos ni altres accionistes. Afluència: dades/generades/nodes.csv."
        ),
        "inici": "2026-11-02",
        "dies": 30,
        "nodes": [node_escenari(n, municipis, etiqueta(n)) for n in escollits.values()],
        "visites": visites,
    }
    sortida = GENERADES / "escenari_primer_mes.json"
    sortida.write_text(json.dumps(escenari, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Escenari desat a {sortida.relative_to(ARREL)}: {len(escenari['nodes'])} nodes.")
    for n in escenari["nodes"]:
        print(f"  {n['nom']}: {n['viatgersFeiner']} viatgers/feiner ({n['font']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
