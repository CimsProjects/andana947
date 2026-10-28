#!/usr/bin/env python3
"""Simulació del primer mes d'un jugador d'Andana 947 (GDD, secció 8).

Reprodueix les regles de concessió i d'accionariat del GDD amb dades reals
d'afluència d'estacions. És una eina de disseny de la fase 1: a la fase 2 la
lògica passarà a `joc/` (Kotlin, amb tests) i els valors a
`dades/configuracio_joc.json`.

Només fa servir la biblioteca estàndard:

    python3 scripts/simulacio_primer_mes.py            # resum i taula diària
    python3 scripts/simulacio_primer_mes.py --corba    # ingrés segons l'afluència

Hipòtesis (vegeu docs/gdd.md):
- Un sol jugador. Amb el model híbrid ningú li pot prendre concessions; aquí
  tampoc no hi ha altres accionistes a les grans estacions.
- No hi ha esdeveniments de caos ni bonificacions d'intermodalitat.
- Només hi ha estacions amb xifres reals publicades; les parades de bus
  s'hi afegiran quan existeixi el model d'estimació de la fase 2.
"""

import argparse
import datetime as dt

# --- Paràmetres (proposta del GDD; a la fase 2 aniran a configuracio_joc.json) ---

CONFIG = {
    # Real: T-casual 1 zona 2026 = 13,00 € per a 10 viatges (ATM, gener de 2026).
    # La moneda del joc són les monedes, amb 1 € real = 1 moneda.
    "tarifa_base": 1.30,
    # Joc: part de l'ingrés per viatger que correspon al concessionari.
    "quota_concessio": 0.01,
    # Joc: compressió de l'afluència perquè una estació gran no valgui mil parades.
    "afluencia_referencia": 1000,
    "exponent_afluencia": 0.6,
    # Joc: el preu d'una concessió equival a tants dies del seu ingrés.
    "dies_retorn": 10,
    "preu_minim": 10.0,
    "capital_inicial": 200.0,
    # Joc: a partir d'aquests viatgers diaris (feiner), l'estació va per accions.
    "llindar_accionariat": 20000,
    "accions_per_estacio": 100,
    "max_accions_jugador": 20,
    "max_accions_per_checkin": 5,
    # Joc: dies que dura una concessió; es renova fent-hi check-in.
    "durada_concessio": 14,
    # Estimat: afluència de dissabte i festiu respecte d'un feiner (a calibrar
    # a la fase 2 amb les dades de feiner i anuals de TMB).
    "factor_no_feiner": 0.6,
    # 2026: 251 dies feiners i 114 dissabtes, diumenges i festius a Catalunya.
    "dies_feiners_any": 251,
    "dies_no_feiners_any": 114,
}

# --- Estacions amb dades reals ---
# tipus "anual": entrades a l'estació en tot l'any (TMB, 2024).
# tipus "feiner": usuaris en un dia feiner (Renfe, Rodalies, 2018).
ESTACIONS = {
    "Baró de Viver (L1)": ("anual", 477_756),
    "Casa de l'Aigua (L11)": ("anual", 46_884),
    "Catalunya (L1, L3)": ("anual", 17_501_208),
    "Diagonal (L3, L5)": ("anual", 16_207_348),
    "Espanya (L1, L3)": ("anual", 15_135_060),
    "Sants (Rodalies)": ("feiner", 101_006),
    "Plaça de Catalunya (Rodalies)": ("feiner", 67_866),
    "Passeig de Gràcia (Rodalies)": ("feiner", 38_791),
    "Clot-Aragó (Rodalies)": ("feiner", 27_284),
    "Arc de Triomf (Rodalies)": ("feiner", 26_539),
}


def viatgers_feiner(nom):
    tipus, valor = ESTACIONS[nom]
    if tipus == "feiner":
        return valor
    c = CONFIG
    dies_equivalents = c["dies_feiners_any"] + c["factor_no_feiner"] * c["dies_no_feiners_any"]
    return valor / dies_equivalents


def ingres_diari(viatgers):
    """Ingrés d'una concessió sencera en un dia (fórmula 4.1 del GDD)."""
    c = CONFIG
    per_viatger = c["tarifa_base"] * c["quota_concessio"]
    relatiu = viatgers / c["afluencia_referencia"]
    return per_viatger * c["afluencia_referencia"] * relatiu ** c["exponent_afluencia"]


def es_accionariat(nom):
    return viatgers_feiner(nom) >= CONFIG["llindar_accionariat"]


def preu_concessio(nom):
    return max(CONFIG["preu_minim"], CONFIG["dies_retorn"] * ingres_diari(viatgers_feiner(nom)))


def preu_accio(nom):
    return max(1.0, preu_concessio(nom) / CONFIG["accions_per_estacio"])


# --- Recorregut del jugador: la Laia viu al Bon Pastor i treballa a prop de Diagonal ---

def checkins_del_dia(data, dia):
    """Llista d'estacions on la jugadora fa check-in aquell dia."""
    setmana = data.weekday()  # 0 = dilluns
    llista = []
    if setmana < 5:
        llista += ["Baró de Viver (L1)", "Clot-Aragó (Rodalies)",
                   "Passeig de Gràcia (Rodalies)", "Diagonal (L3, L5)"]
    if setmana == 1:
        llista.append("Arc de Triomf (Rodalies)")
    if setmana == 2:
        llista.append("Sants (Rodalies)")
    if setmana == 4:
        llista += ["Plaça de Catalunya (Rodalies)", "Catalunya (L1, L3)"]
    if setmana == 5:
        llista.append("Espanya (L1, L3)")
    if dia in (12, 24):
        llista.append("Casa de l'Aigua (L11)")
    return llista


def simula(dies=30, inici=dt.date(2026, 11, 2), escriu=True):
    c = CONFIG
    saldo = c["capital_inicial"]
    concessions = {}   # nom -> darrer dia de vigència
    accions = {}       # nom -> nombre d'accions
    files = []
    for dia in range(1, dies + 1):
        data = inici + dt.timedelta(days=dia - 1)
        feiner = data.weekday() < 5
        fets = []
        for nom in checkins_del_dia(data, dia):
            if es_accionariat(nom):
                tinc = accions.get(nom, 0)
                quantes = min(c["max_accions_per_checkin"], c["max_accions_jugador"] - tinc,
                              int(saldo // preu_accio(nom)))
                if quantes > 0:
                    saldo -= quantes * preu_accio(nom)
                    accions[nom] = tinc + quantes
                    fets.append(f"+{quantes} acc. {nom}")
            elif nom in concessions and concessions[nom] >= dia:
                if concessions[nom] - dia < c["durada_concessio"] // 2:
                    concessions[nom] = dia + c["durada_concessio"] - 1
                    fets.append(f"renova {nom}")
            elif saldo >= preu_concessio(nom):
                saldo -= preu_concessio(nom)
                concessions[nom] = dia + c["durada_concessio"] - 1
                fets.append(f"conquereix {nom}")
        # Tic econòmic de les 04:00: liquida el dia.
        factor = 1.0 if feiner else c["factor_no_feiner"]
        ingres = 0.0
        for nom, fins in list(concessions.items()):
            if fins < dia:
                del concessions[nom]
                fets.append(f"caduca {nom}")
                continue
            ingres += ingres_diari(viatgers_feiner(nom) * factor)
        for nom, n in accions.items():
            ingres += ingres_diari(viatgers_feiner(nom) * factor) * n / c["accions_per_estacio"]
        saldo += ingres
        files.append((dia, data, ingres, saldo, len(concessions), sum(accions.values()), fets))
    if escriu:
        print("dia  data        ingrés/dia    saldo  conc.  accions  accions del dia")
        for dia, data, ingres, saldo_dia, n_conc, n_acc, fets in files:
            print(f"{dia:>3}  {data}  {ingres:>9.2f}  {saldo_dia:>8.2f}  {n_conc:>5}  {n_acc:>7}  "
                  + "; ".join(fets))
        total = sum(f[2] for f in files)
        print(f"\nIngressos del mes: {total:.2f} monedes. Saldo final: {files[-1][3]:.2f} monedes.")
        print("Accions:", ", ".join(f"{k} {v}" for k, v in sorted(accions.items())))
    return files


def corba():
    print("Viatgers/dia   ingrés/dia   preu   ingrés per viatger (cèntims)")
    for p in (10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000):
        i = ingres_diari(p)
        print(f"{p:>12}   {i:>9.2f}   {max(CONFIG['preu_minim'], CONFIG['dies_retorn'] * i):>6.1f}"
              f"   {100 * i / p:>8.3f}")
    print("\nEstacions reals:")
    for nom in ESTACIONS:
        p = viatgers_feiner(nom)
        tipus = "accions" if es_accionariat(nom) else "concessió"
        preu = preu_accio(nom) if es_accionariat(nom) else preu_concessio(nom)
        print(f"  {nom:<32} {p:>9.0f} viat./feiner  {ingres_diari(p):>7.2f} monedes/dia  "
              f"{tipus}: {preu:.2f} monedes")


if __name__ == "__main__":
    arguments = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    arguments.add_argument("--corba", action="store_true", help="mostra l'ingrés segons l'afluència")
    if arguments.parse_args().corba:
        corba()
    else:
        simula()
