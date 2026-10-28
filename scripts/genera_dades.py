#!/usr/bin/env python3
"""Genera els nodes, les línies i l'afluència del joc a partir de les fonts baixades a font/.

    python3 scripts/baixa_fonts.py
    python3 scripts/genera_dades.py

Escriu a dades/generades/:
    nodes.csv          parades i estacions, amb l'afluència d'un feiner i d'on surt (real o estimat)
    linies.csv         línies, amb les expedicions i els viatgers d'un feiner
    linia_nodes.csv    quines parades té cada línia i quantes expedicions hi paren
    municipis.csv      població (Cens 2021), superfície i densitat de cada municipi
    informe_dades.md   recomptes, aparellaments, comprovacions dels models i calibratges

Els models són els del GDD (seccions 2.2, 2.3 i 4.4) amb els paràmetres de dades/configuracio_joc.json.
"""

import csv
import datetime as dt
import json
import pathlib
import sys
from collections import defaultdict

import openpyxl

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana import afluencia, fonts, gtfs  # noqa: E402
from andana.territori import Territori, distancia  # noqa: E402
from andana.text import normalitza  # noqa: E402
from andana.xarxa import FEEDS, Xarxa  # noqa: E402

SORTIDA = ARREL / "dades" / "generades"


def llegeix_json(cami):
    return json.loads(pathlib.Path(cami).read_text(encoding="utf-8"))


class Informe:
    def __init__(self):
        self.linies = []

    def __call__(self, text=""):
        print(text, flush=True)
        self.linies.append(text)

    def desa(self, cami):
        pathlib.Path(cami).write_text("\n".join(self.linies) + "\n", encoding="utf-8")


def millor_coincidencia(nom, candidats):
    """El node de `candidats` ({nom normalitzat: node}) que millor encaixa amb `nom`, o None."""
    clau = normalitza(nom)
    if clau in candidats:
        return candidats[clau]
    paraules = set(clau.split())
    millor, puntuacio = None, 0.0
    for nom_candidat, node in candidats.items():
        altres = set(nom_candidat.split())
        if not paraules or not altres:
            continue
        comunes = len(paraules & altres) / len(paraules | altres)
        if nom_candidat.endswith(clau) or clau.endswith(nom_candidat):
            comunes = max(comunes, 0.75)
        if comunes > puntuacio:
            millor, puntuacio = node, comunes
    return millor if puntuacio >= 0.5 else None


def main():
    config = llegeix_json(ARREL / "dades" / "configuracio_joc.json")
    model = config["modelAfluencia"]
    calendari = config["calendari"]
    totals = {t["id"]: t for t in llegeix_json(ARREL / "dades" / "totals_control.json")["totals"]}
    informe = Informe()
    informe("# Informe de les dades generades")
    informe()
    informe(f"Generat el {dt.date.today():%d/%m/%Y} amb `scripts/genera_dades.py` (model d'afluència v{model['versio']}).")
    informe()

    def font(id_font):
        return fonts.cami_local(fonts.per_id(id_font))

    # --- Territori -------------------------------------------------------------------------------------------
    territori = Territori(ARREL / "dades/territori/municipis_catalunya.geojson", ARREL / "dades/territori/comarques.json")
    n_seccions = territori.carrega_seccions(font("seccions_ine"), font("cens_2021_seccions"))
    poblacio_total = sum(m.poblacio for m in territori.municipis.values())
    informe("## Territori")
    informe()
    informe(f"- {len(territori.municipis)} municipis; {n_seccions} seccions censals de l'INE, "
            f"{territori.seccions_estimades} sense població al Cens 2021 (estimades amb la densitat del municipi).")
    informe(f"- Població total (Cens 2021): {poblacio_total:,.0f} habitants.".replace(",", "."))
    informe()

    # --- GTFS ------------------------------------------------------------------------------------------------
    xarxa = Xarxa(territori, model["radiFusioParadesMetres"])
    for config_feed in FEEDS:
        cami = font(config_feed.id)
        if not cami.exists():
            informe(f"- **Falta** {config_feed.id}: no s'ha baixat.")
            continue
        feed = gtfs.llegeix(cami, config_feed.id)
        xarxa.afegeix_feed(config_feed, feed)
    xarxa.fusiona_estacions()
    n_bus_abans = sum(1 for n in xarxa.nodes.values() if n.mode == "bus")
    xarxa.fusiona_parades_bus()
    n_bus = sum(1 for n in xarxa.nodes.values() if n.mode == "bus")
    xarxa.assigna_territori()
    informe("## Xarxa (GTFS)")
    informe()
    for linia in xarxa.diagnosi:
        informe(f"- {linia}")
    informe(f"- Parades de bus: {n_bus_abans} abans de fusionar els dos sentits i els operadors, {n_bus} després.")
    per_mode = defaultdict(int)
    for node in xarxa.nodes.values():
        per_mode[node.mode] += 1
    informe("- Nodes per mode: " + ", ".join(f"{m} {n}" for m, n in sorted(per_mode.items())) + ".")
    codis = defaultdict(set)
    for linia in xarxa.linies.values():
        codis[(linia.operador, linia.mode)].add(linia.codi)
    for (operador, mode), valors in sorted(codis.items()):
        mostra = sorted(valors, key=lambda c: (len(c), c))
        text = ", ".join(mostra[:60]) + (f" … ({len(mostra)} en total)" if len(mostra) > 60 else "")
        informe(f"- Línies {operador} ({mode}): {text}")
    informe()

    nodes_linia = xarxa.nodes_de_linia()
    viatgers = {}          # node -> viatgers d'un feiner
    origen = {}            # node -> (real | estimat, mètode, any)
    viatgers_linia = {}    # línia -> (viatgers d'un feiner, real | estimat)

    # --- Metro (TMB): dada directa per estació ----------------------------------------------------------------
    informe("## Afluència")
    informe()
    metro = openpyxl.load_workbook(font("tmb_metro_2025"), read_only=True, data_only=True)
    dades_metro = afluencia.llegeix_tmb_metro(metro)
    nodes_metro = [n for n in xarxa.nodes.values() if n.mode == "metro"]
    per_linia = defaultdict(dict)
    for node in nodes_metro:
        for codi in xarxa.codis_linia(node):
            per_linia[codi.upper().replace(" ", "")][normalitza(node.nom)] = node
    tots_metro = {normalitza(n.nom): n for n in nodes_metro}
    sense_aparellar, suma_feiner, suma_anual = [], 0.0, 0.0
    for (linia, nom), valors in sorted(dades_metro.items()):
        node = millor_coincidencia(nom, per_linia.get(linia, {})) or millor_coincidencia(nom, tots_metro)
        if node is None or "feiner" not in valors:
            sense_aparellar.append(f"{linia} {nom}")
            continue
        viatgers[node.id] = viatgers.get(node.id, 0.0) + valors["feiner"]
        origen[node.id] = ("real", "TMB, mitjana dels feiners del 2025 per estació", 2025)
        suma_feiner += valors["feiner"]
        suma_anual += valors.get("anual", 0.0)
    informe(f"- **Metro (TMB 2025, real):** {len(dades_metro)} parelles línia-estació, "
            f"{len([n for n in nodes_metro if n.id in viatgers])} de {len(nodes_metro)} estacions amb dada.")
    if sense_aparellar:
        informe(f"  - Sense aparellar: {', '.join(sense_aparellar)}")
    sense_dada = [n.nom for n in nodes_metro if n.id not in viatgers]
    if sense_dada:
        informe(f"  - Estacions del GTFS sense dada: {', '.join(sorted(sense_dada))}")
    if suma_feiner and suma_anual:
        # 2025: 250 feiners i 115 dissabtes, diumenges i festius a Barcelona no es fan servir aquí: es calibra
        # amb els dies del calendari de la configuració, que és el que fa servir el joc.
        factor = afluencia.factor_no_feiner_calibrat(suma_feiner, suma_anual, calendari["diesFeiners"],
                                                     calendari["diesNoFeiners"])
        informe(f"  - Calibratge del factor no feiner (GDD 4.4): amb el feiner mitjà i el total anual del metro, "
                f"**{factor:.3f}** (a la configuració: {calendari['factorNoFeiner']}).")

    # --- FGC: dada directa per estació -----------------------------------------------------------------------
    fgc_anys = afluencia.llegeix_fgc(font("fgc_demanda"))
    gencat_anys, posicions_fgc = afluencia.llegeix_fgc_gencat(font("fgc_demanda_gencat"))
    any_fgc = max(fgc_anys)
    any_gencat = max(gencat_anys)
    nodes_fgc = [n for n in xarxa.nodes.values() if n.mode == "fgc"]
    per_nom_fgc = {normalitza(n.nom): n for n in nodes_fgc}
    posicio_per_nom = {normalitza(k): v for k, v in posicions_fgc.items()}

    def node_fgc(nom):
        node = millor_coincidencia(nom, per_nom_fgc)
        if node is None and normalitza(nom) in posicio_per_nom:
            lat, lon, _ = posicio_per_nom[normalitza(nom)]
            propers = sorted(nodes_fgc, key=lambda n: distancia(lat, lon, n.lat, n.lon))
            if propers and distancia(lat, lon, propers[0].lat, propers[0].lon) < 400:
                node = propers[0]
        return node

    sense_fgc = []
    for nom, anual in sorted(fgc_anys[any_fgc].items()):
        node = node_fgc(nom)
        if node is None:
            sense_fgc.append(nom)
            continue
        viatgers[node.id] = viatgers.get(node.id, 0.0) + afluencia.feiner_des_de_anual(anual, calendari)
        origen[node.id] = ("real", f"FGC, demanda de l'any {any_fgc} per estació", any_fgc)
    # La sèrie de la Generalitat inclou la línia Lleida–la Pobla, que no surt a la del portal d'FGC.
    for nom, anual in sorted(gencat_anys[any_gencat].items()):
        node = node_fgc(nom)
        if node is None or node.id in viatgers:
            continue
        viatgers[node.id] = afluencia.feiner_des_de_anual(anual, calendari)
        origen[node.id] = ("real", f"Generalitat, demanda d'FGC de l'any {any_gencat} per estació", any_gencat)
    informe(f"- **FGC ({any_fgc}, real):** {len([n for n in nodes_fgc if n.id in viatgers])} de {len(nodes_fgc)} "
            f"estacions amb dada.")
    if sense_fgc:
        informe(f"  - Sense aparellar: {', '.join(sense_fgc)}")
    sense_dada = [n.nom for n in nodes_fgc if n.id not in viatgers]
    if sense_dada:
        informe(f"  - Estacions del GTFS sense dada: {', '.join(sorted(sense_dada))}")

    # --- Rodalies: repartiment del 2018 escalat (GDD 2.2) --------------------------------------------------------
    renfe_2018 = afluencia.llegeix_renfe_2018(
        openpyxl.load_workbook(font("renfe_viatgers_2018"), read_only=True, data_only=True))
    nodes_renfe = {n.clau: n for n in xarxa.nodes.values() if n.mode in ("rodalies", "regional")}
    factor_xarxa = totals["rodalies_2024"]["viatgers"] / totals["rodalies_2018"]["viatgers"]
    pes_linies = {}
    entrades = {}
    for codi, valors in renfe_2018.items():
        node = nodes_renfe.get(codi.lstrip("0"))
        if node is None:
            continue
        entrades[node.id] = valors["pujats"]
        pes_linies[node.id] = {xarxa.linies[l].codi: n for l, n in node.expedicions.items()
                               if xarxa.linies[l].mode == "rodalies"}
    estimat_rodalies = afluencia.model_rodalies(entrades, pes_linies, {None: factor_xarxa})
    for node_id, valor in estimat_rodalies.items():
        viatgers[node_id] = valor
        origen[node_id] = ("estimat", "Renfe 2018 per estació × factor de Rodalies 2024/2018 (Idescat)", 2024)
    informe(f"- **Rodalies (estimat, GDD 2.2):** {len(renfe_2018)} estacions al fitxer de Renfe del 2018, "
            f"{len(estimat_rodalies)} aparellades pel codi d'estació. Factor de xarxa f = "
            f"{totals['rodalies_2024']['viatgers']:,} / {totals['rodalies_2018']['viatgers']:,} = "
            f"**{factor_xarxa:.4f}**.".replace(",", "."))
    informe("  - Factor per línia: pendent. Les sèries per línia (ATM / OMC) no es poden baixar des de la CI "
            "(el servidor de l'OMC hi respon 404); de moment, un sol factor per a tota la xarxa, de la mateixa "
            "sèrie de l'Idescat. Amb un sol factor, totes les estacions creixen igual i es compleixen les "
            "comprovacions (a) i (b) del GDD 2.2.")
    no_aparellades = [v["nom"] for c, v in renfe_2018.items() if c.lstrip("0") not in nodes_renfe]
    if no_aparellades:
        informe(f"  - Del fitxer del 2018 sense estació al GTFS: {', '.join(sorted(no_aparellades))}")
    quadra = [abs(v["pujats"] - v["baixats"]) / max(v["pujats"], 1) for v in renfe_2018.values() if v["pujats"] > 1000]
    if quadra:
        informe(f"  - Comprovació pujats ≈ baixats (estacions de més de 1.000): diferència mediana "
                f"{sorted(quadra)[len(quadra) // 2]:.1%}.")

    # Regionals i estacions de Renfe sense dada: es reparteix el total de regionals (GDD 2.2).
    pesos_reg = {}
    punts_calibratge = []
    for node in nodes_renfe.values():
        exp_reg = sum(n for l, n in node.expedicions.items() if xarxa.linies[l].mode == "regional")
        municipi = territori.municipis.get(node.municipi)
        poblacio = municipi.poblacio if municipi else 0.0
        if exp_reg > 0:
            pesos_reg[node.id] = afluencia.pes_regional(exp_reg, poblacio, model["exponentPoblacioRegionals"])
        if node.id in entrades:
            punts_calibratge.append((entrades[node.id], node.expedicions_feiner, poblacio))
    total_reg = afluencia.feiner_des_de_anual(totals["regionals_2024"]["viatgers"], calendari)
    for node_id, valor in afluencia.reparteix(total_reg, pesos_reg).items():
        viatgers[node_id] = viatgers.get(node_id, 0.0) + valor
        if node_id in origen:
            metode = origen[node_id][1] + " + part dels regionals (expedicions × població^0,5)"
        else:
            metode = "Regionals 2024 (Idescat) repartits per expedicions × població^0,5"
        origen[node_id] = ("estimat", metode, 2024)
    informe(f"- **Regionals (estimat, GDD 2.2):** {totals['regionals_2024']['viatgers']:,} viatgers el 2024, "
            f"{total_reg:,.0f} en un feiner, repartits entre {len(pesos_reg)} estacions.".replace(",", "."))
    beta = afluencia.ajust_exponent(punts_calibratge)
    if beta is not None:
        informe(f"  - Calibratge de l'exponent de població amb les {len(punts_calibratge)} estacions de "
                f"Rodalies amb dada: **β = {beta:.2f}** (a la configuració: {model['exponentPoblacioRegionals']}).")
    sense = [n.nom for n in nodes_renfe.values() if n.id not in viatgers]
    if sense:
        informe(f"  - Estacions de Renfe sense cap estimació: {', '.join(sorted(sense))}")

    # --- Bus i TRAM: repartiment del total de cada línia (GDD 2.3) -----------------------------------------------
    nodes_carrer = [n for n in xarxa.nodes.values() if n.mode in ("bus", "tram")]
    poblacio_400 = {}
    for node in nodes_carrer:
        poblacio_400[node.id] = territori.poblacio_radi(node.lat, node.lon, model["radiPoblacioParadaMetres"])
    pes = {n.id: afluencia.pes_parada(n.expedicions_feiner, poblacio_400[n.id], model["divisorPoblacioParada"])
           for n in nodes_carrer}

    # TMB: dada real per línia (feiner del 2025).
    dades_bus = afluencia.llegeix_tmb_bus(openpyxl.load_workbook(font("tmb_bus_2025"), read_only=True,
                                                                 data_only=True))
    totals_linia = {}
    linies_tmb = {l.id: l for l in xarxa.linies.values() if l.operador == "TMB" and l.mode == "bus"}
    aparellades, exp_tmb, viat_tmb = set(), 0, 0.0
    per_codi = {l.codi.upper(): l for l in linies_tmb.values()}
    for codi, valors in dades_bus.items():
        linia = per_codi.get(codi.upper())
        if linia is None:
            continue
        totals_linia[linia.id] = valors["feiner"]
        viatgers_linia[linia.id] = (valors["feiner"], "real")
        aparellades.add(linia.id)
        exp_tmb += linia.expedicions_feiner
        viat_tmb += valors["feiner"]
    ocupacio_urbana = viat_tmb / exp_tmb if exp_tmb else 0.0
    informe(f"- **Bus TMB (real per línia, estimat per parada, GDD 2.3):** {len(dades_bus)} línies al fitxer del "
            f"2025, {len(aparellades)} de {len(linies_tmb)} aparellades amb el GTFS.")
    no_trobades = sorted(set(dades_bus) - {l.codi.upper() for l in linies_tmb.values()})
    if no_trobades:
        informe(f"  - Del fitxer sense línia al GTFS: {', '.join(no_trobades)}")
    sense_dada = sorted({l.codi for i, l in linies_tmb.items() if i not in aparellades})
    if sense_dada:
        informe(f"  - Línies del GTFS sense dada (s'estimen amb l'ocupació mitjana): {', '.join(sense_dada)}")
    informe(f"  - Ocupació mitjana d'una expedició de bus urbà (TMB): {ocupacio_urbana:.1f} viatgers.")

    # Interurbans: ocupació mitjana a partir del total de l'estadística de transport per carretera.
    linies_inter = [l for l in xarxa.linies.values() if l.operador == "Generalitat de Catalunya"]
    exp_inter = sum(l.expedicions_feiner for l in linies_inter)
    feiner_inter = afluencia.feiner_des_de_anual(totals["interurbans_2018"]["viatgers"], calendari)
    ocupacio_inter = feiner_inter / exp_inter if exp_inter else 0.0
    informe(f"- **Bus interurbà (estimat, GDD 2.3):** {totals['interurbans_2018']['viatgers']:,} viatgers el "
            f"2018 (Idescat) i {exp_inter:,} expedicions en un feiner al GTFS: ocupació mitjana "
            f"{ocupacio_inter:.1f} viatgers per expedició.".replace(",", "."))

    for linia in xarxa.linies.values():
        if linia.mode != "bus" or linia.id in totals_linia:
            continue
        ocupacio = ocupacio_inter if linia.operador == "Generalitat de Catalunya" else ocupacio_urbana
        totals_linia[linia.id] = linia.expedicions_feiner * ocupacio
        viatgers_linia[linia.id] = (totals_linia[linia.id], "estimat")
    informe("- **Bus AMB (estimat, GDD 2.3):** ocupació mitjana del bus urbà de TMB × expedicions de cada línia.")

    # TRAM: total de la xarxa (Idescat), sense dada per línia.
    linies_tram = [l.id for l in xarxa.linies.values() if l.mode == "tram"]
    total_tram = afluencia.feiner_des_de_anual(totals["tram_2024"]["viatgers"], calendari)
    parades_tram = {n.id for n in nodes_carrer if n.mode == "tram"}
    for node_id, valor in afluencia.reparteix(total_tram, {n: pes[n] for n in parades_tram}).items():
        viatgers[node_id] = valor
        origen[node_id] = ("estimat", "TRAM 2024 (Idescat) repartit per expedicions i població a 400 m", 2024)
    informe(f"- **TRAM (estimat, GDD 2.3):** {totals['tram_2024']['viatgers']:,} viatgers el 2024, "
            f"{total_tram:,.0f} en un feiner, repartits entre {len(parades_tram)} parades de "
            f"{len(linies_tram)} línies (no hi ha dada oberta per línia).".replace(",", "."))

    parades_linia = {l: [n for n in ns if n in pes] for l, ns in nodes_linia.items()}
    for node_id, valor in afluencia.model_parades(totals_linia, pes, parades_linia).items():
        viatgers[node_id] = valor
        origen[node_id] = ("estimat", "Total de les seves línies repartit per expedicions i població a 400 m", 2025)
    informe()

    # --- Comprovacions -------------------------------------------------------------------------------------
    informe("## Comprovacions")
    informe()
    sense_afluencia = [n for n in xarxa.nodes.values() if viatgers.get(n.id, 0.0) <= 0]
    informe(f"- Nodes sense afluència: {len(sense_afluencia)} de {len(xarxa.nodes)}.")
    llindar = config["accionariat"]["llindarViatgersFeiner"]
    grans = sorted((n for n in xarxa.nodes.values() if viatgers.get(n.id, 0) >= llindar),
                   key=lambda n: -viatgers[n.id])
    informe(f"- Grans estacions (≥ {llindar:,.0f} viatgers en un feiner): {len(grans)}: ".replace(",", ".")
            + ", ".join(f"{n.nom} ({n.mode}, {viatgers[n.id]:,.0f})".replace(",", ".") for n in grans) + ".")
    for nom in ("BARO VIVER", "CASA AIGUA", "CATALUNYA", "SANTS"):
        for n in xarxa.nodes.values():
            if n.mode != "bus" and nom in normalitza(n.nom):
                informe(f"- Control: {n.nom} ({n.mode}) {viatgers.get(n.id, 0):,.0f} viatgers/feiner "
                        f"({origen.get(n.id, ('—',))[0]}).".replace(",", "."))
    informe()

    # --- Sortida ---------------------------------------------------------------------------------------------
    SORTIDA.mkdir(parents=True, exist_ok=True)
    with open(SORTIDA / "nodes.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "nom", "mode", "operador", "lat", "lon", "municipi", "comarca", "linies",
                    "expedicions_feiner", "poblacio_400m", "viatgers_feiner", "font_afluencia", "metode", "any_dada"])
        for n in sorted(xarxa.nodes.values(), key=lambda n: n.id):
            font_af, metode, any_ = origen.get(n.id, ("estimat", "Sense dada", ""))
            w.writerow([n.id, n.nom, n.mode, n.operador, f"{n.lat:.6f}", f"{n.lon:.6f}", n.municipi, n.comarca,
                        ";".join(xarxa.codis_linia(n)), n.expedicions_feiner,
                        f"{poblacio_400.get(n.id, 0):.0f}" if n.id in poblacio_400 else "",
                        f"{viatgers.get(n.id, 0.0):.1f}", font_af, metode, any_])
    with open(SORTIDA / "linies.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "operador", "mode", "codi", "nom", "color", "color_text", "expedicions_feiner",
                    "viatgers_feiner", "font_afluencia"])
        for l in sorted(xarxa.linies.values(), key=lambda l: l.id):
            valor, font_af = viatgers_linia.get(l.id, (None, ""))
            w.writerow([l.id, l.operador, l.mode, l.codi, l.nom, l.color, l.color_text, l.expedicions_feiner,
                        "" if valor is None else f"{valor:.1f}", font_af])
    with open(SORTIDA / "linia_nodes.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["linia_id", "node_id", "expedicions_feiner"])
        for n in sorted(xarxa.nodes.values(), key=lambda n: n.id):
            for linia, exp in sorted(n.expedicions.items()):
                w.writerow([linia, n.id, exp])
    with open(SORTIDA / "municipis.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["codi", "nom", "comarca", "poblacio_2021", "superficie_km2", "densitat"])
        for m in sorted(territori.municipis.values(), key=lambda m: m.codi):
            w.writerow([m.codi, m.nom, m.comarca, f"{m.poblacio:.0f}", f"{m.superficie_km2:.3f}", f"{m.densitat:.2f}"])
    informe.desa(SORTIDA / "informe_dades.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
