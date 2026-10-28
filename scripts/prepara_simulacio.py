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
from andana.aparella import similitud  # noqa: E402

GENERADES = ARREL / "dades" / "generades"
FEINERS = [1, 2, 3, 4, 5]

# Correspondència explícita entre els noms del GDD (secció 8) i l'`id` del node a nodes.csv. Els noms del GTFS
# no són els del GDD (Renfe hi posa «Barcelona»: «Barcelona El Clot» és la Clot-Aragó del GDD), i per això no es
# busca per nom. Els id són estables entre generacions: el codi d'estació d'Adif (Renfe) i l'estació pare del
# GTFS de TMB.
NODES_GDD = {
    "Baró de Viver (L1)": "tmb:P.6660138",
    "Clot-Aragó (Rodalies)": "renfe:79009",
    "Passeig de Gràcia (Rodalies)": "renfe:71802",
    "Diagonal (L3, L5)": "tmb:P.6660328",
    "Arc de Triomf (Rodalies)": "renfe:78804",
    "Sants (Rodalies)": "renfe:71801",
    "Plaça de Catalunya (Rodalies)": "renfe:78805",
    "Catalunya (L1, L3)": "tmb:P.6660126",
    "Espanya (L1, L3)": "tmb:P.6660122",
    "Casa de l'Aigua (L11)": "tmb:P.6661137",
}

# (node del GDD, dies de la setmana, dies concrets de la simulació). L'ordre és el del recorregut.
RUTINA = [
    ("Baró de Viver (L1)", FEINERS, []),
    ("Clot-Aragó (Rodalies)", FEINERS, []),
    ("Passeig de Gràcia (Rodalies)", FEINERS, []),
    ("Diagonal (L3, L5)", FEINERS, []),
    ("Arc de Triomf (Rodalies)", [2], []),
    ("Sants (Rodalies)", [3], []),
    ("Plaça de Catalunya (Rodalies)", [5], []),
    ("Catalunya (L1, L3)", [5], []),
    ("Espanya (L1, L3)", [6], []),
    ("Casa de l'Aigua (L11)", [], [12, 24]),
]
# Parades de bus: les N més properes a cada una d'aquestes estacions, a menys del radi.
PARADES_BUS = [("Baró de Viver (L1)", 3), ("Diagonal (L3, L5)", 3)]
RADI_BUS_METRES = 250


def troba(per_id, nom_gdd):
    """El node del GDD per l'id fixat a NODES_GDD. Si no hi és, l'error diu els candidats més semblants."""
    id_node = NODES_GDD[nom_gdd]
    if id_node in per_id:
        return per_id[id_node]
    nom = nom_gdd.split(" (")[0]
    candidats = sorted(((similitud(nom, n["nom"]), n) for n in per_id.values() if n["mode"] != "bus"),
                       key=lambda x: -x[0])[:5]
    llista = "\n".join(f"  {n['id']}: {n['nom']} ({n['mode']}, {n['linies']})" for _, n in candidats)
    raise SystemExit(f"No trobo el node {id_node} ({nom_gdd}) a nodes.csv. Els més semblants:\n{llista}\n"
                     "Corregeix NODES_GDD a scripts/prepara_simulacio.py.")


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
    per_id = {n["id"]: n for n in nodes}
    escollits, visites = {}, []
    for nom_gdd, dies_setmana, dies_simulacio in RUTINA:
        n = troba(per_id, nom_gdd)
        escollits[n["id"]] = n
        visites.append({"node": n["id"], "diesSetmana": dies_setmana, "diesSimulacio": dies_simulacio})
    busos = [n for n in nodes if n["mode"] == "bus"]
    for nom_estacio, quantes in PARADES_BUS:
        estacio = troba(per_id, nom_estacio)
        lat, lon = float(estacio["lat"]), float(estacio["lon"])
        a_prop = sorted((distancia(lat, lon, float(b["lat"]), float(b["lon"])), b["id"], b) for b in busos)
        for d, _, b in [x for x in a_prop if x[0] <= RADI_BUS_METRES][:quantes]:
            escollits[b["id"]] = b
            visites.append({"node": b["id"], "diesSetmana": FEINERS, "diesSimulacio": []})
    nom_gdd = {v: k for k, v in NODES_GDD.items()}
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
        "nodes": [node_escenari(n, municipis, nom_gdd.get(n["id"]) or etiqueta(n)) for n in escollits.values()],
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
