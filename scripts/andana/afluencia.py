"""Lectors de les fonts d'afluència i models d'estimació (GDD, seccions 2.2, 2.3 i 4.4)."""

import csv
import math
import re
from collections import defaultdict

from .text import normalitza

MESOS = ["GENER", "FEBRER", "MARC", "ABRIL", "MAIG", "JUNY", "JULIOL", "AGOST", "SETEMBRE", "OCTUBRE",
         "NOVEMBRE", "DESEMBRE"]


# --- Conversions (GDD 4.4) ---------------------------------------------------------------------------------

def feiner_des_de_anual(anual, calendari):
    """Viatgers d'un feiner quan la font dona el total de l'any: `P_anual / (feiners + f × no feiners)`."""
    return anual / (calendari["diesFeiners"] + calendari["factorNoFeiner"] * calendari["diesNoFeiners"])


def factor_no_feiner_calibrat(feiner, anual, dies_feiners, dies_no_feiners):
    """El factor de dissabte i festiu que fa quadrar un feiner mitjà amb el total de l'any."""
    return (anual / feiner - dies_feiners) / dies_no_feiners


# --- TMB ---------------------------------------------------------------------------------------------------

def _numero(valor):
    if valor is None or valor == "":
        return None
    try:
        return float(valor)
    except (TypeError, ValueError):
        return None


def _nom_linia_tmb(text):
    """«LÍNIA  1» → «L1»; «LÍNIA 9 NORD» → «L9N»; «LÍNIA 10 SUD» → «L10S»."""
    t = re.sub(r"\s+", " ", str(text)).upper().replace("Í", "I")
    m = re.search(r"LINIA (\d+)\s*(NORD|SUD|N|S)?", t)
    if not m:
        return None
    return "L" + m.group(1) + (m.group(2)[0] if m.group(2) else "")


def llegeix_tmb_metro(llibre):
    """Full de càlcul de TMB «Resum dades mensuals i diàries de viatgers FMB»: entrades per línia i estació.

    Retorna {(línia, estació): {"feiner": mitjana dels feiners de l'any, "anual": total de l'any}}. Els dos fulls
    no sempre escriuen igual el nom de l'estació («PARAL·LEL», «PARAL.LEL»): s'ajunten pel nom normalitzat.
    """
    resultat = defaultdict(dict)
    nom_de = {}
    for full, clau in (("Feiners", "feiner"), ("Mensuals", "anual")):
        linia = None
        for fila in llibre[full].iter_rows(values_only=True):
            cel = list(fila) + [None] * 16
            capcalera = cel[1]
            if isinstance(capcalera, str) and capcalera.strip().upper().startswith(("LÍNIA", "LINIA")):
                linia = _nom_linia_tmb(capcalera)
                continue
            if isinstance(capcalera, str) and capcalera.strip().upper() == "TOTAL":
                linia = None
                continue
            nom = cel[2]
            if linia is None or not isinstance(nom, str) or not nom.strip():
                continue
            valors = [_numero(v) for v in cel[3:15]]
            estacio = nom_de.setdefault((linia, normalitza(nom)), nom.strip())
            if clau == "feiner":
                mesos = [v for v in valors if v]
                if mesos:
                    resultat[(linia, estacio)]["feiner"] = sum(mesos) / len(mesos)
            else:
                acumulat = _numero(cel[15])
                if acumulat is None:
                    acumulat = sum(v for v in valors if v)
                resultat[(linia, estacio)]["anual"] = acumulat
    return dict(resultat)


def llegeix_tmb_bus(llibre):
    """Full de càlcul de TMB «Resum dades mensuals i diàries de viatgers TB»: per línia de bus, el total de
    cada mes i la mitjana dels feiners. Retorna {línia: {"feiner": mitjana dels mesos, "anual": suma}}."""
    resultat = {}
    full = llibre.worksheets[0]
    dins = False
    for fila in full.iter_rows(values_only=True):
        cel = list(fila)
        if any(isinstance(v, str) and v.strip().upper() == "LÍNIES" for v in cel):
            dins = True
            continue
        if not dins or len(cel) < 4 or cel[1] is None:
            continue
        codi = str(cel[1]).strip()
        if codi.endswith(".0"):
            codi = codi[:-2]
        if not codi or codi.upper().startswith("TOTAL"):
            continue
        totals = [_numero(v) for v in cel[2::2]]
        feiners = [_numero(v) for v in cel[3::2]]
        mesos = [v for v in feiners if v]
        if mesos:
            resultat[codi] = {"feiner": sum(mesos) / len(mesos), "anual": sum(v for v in totals if v)}
    return resultat


# --- FGC ---------------------------------------------------------------------------------------------------

def llegeix_fgc(cami):
    """Demanda mensual per estació d'FGC (portal d'FGC). Retorna {any: {estació: total anual}}."""
    resultat = defaultdict(dict)
    with open(cami, encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f, delimiter=";"):
            total = _numero(fila.get("total"))
            if total is None:
                continue
            any_ = int(fila["any"])
            resultat[any_][fila["estacio"].strip()] = resultat[any_].get(fila["estacio"].strip(), 0.0) + total
    return dict(resultat)


def llegeix_fgc_gencat(cami):
    """Demanda mensual d'FGC (Generalitat). Retorna ({any: {estació: total}}, {estació: (lat, lon, línia)})."""
    per_any = defaultdict(lambda: defaultdict(float))
    posicions = {}
    with open(cami, encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f):
            nom = fila["Estació"].strip()
            viatgers = _numero(fila.get("Viatgers"))
            if viatgers is not None:
                per_any[int(fila["Any"])][nom] += viatgers
            lat, lon = _numero(fila.get("Latitud WGS84 [Estació]")), _numero(fila.get("Longitud WGS84 [Estació]"))
            if lat and lon:
                posicions[nom] = (lat, lon, fila.get("Línia FGC", ""))
    return {a: dict(v) for a, v in per_any.items()}, posicions


# --- Renfe ---------------------------------------------------------------------------------------------------

def llegeix_renfe_2018(llibre):
    """Viatgers per franja horària d'un feiner del 2018 (Renfe Data). Retorna {codi: {nom, pujats, baixats}}."""
    resultat = {}
    full = llibre.worksheets[0]
    files = full.iter_rows(values_only=True)
    capcalera = [str(c).strip() for c in next(files)]
    i = {nom: capcalera.index(nom) for nom in
         ("CODIGO_ESTACION", "NOMBRE_ESTACION", "VIAJEROS_SUBIDOS", "VIAJEROS_BAJADOS")}
    for fila in files:
        if fila[i["CODIGO_ESTACION"]] is None:
            continue
        codi = str(fila[i["CODIGO_ESTACION"]]).strip().split(".")[0]
        estacio = resultat.setdefault(codi, {"nom": str(fila[i["NOMBRE_ESTACION"]]).strip(), "pujats": 0.0,
                                             "baixats": 0.0})
        estacio["pujats"] += _numero(fila[i["VIAJEROS_SUBIDOS"]]) or 0.0
        estacio["baixats"] += _numero(fila[i["VIAJEROS_BAJADOS"]]) or 0.0
    return resultat


# --- Models ------------------------------------------------------------------------------------------------

def model_rodalies(entrades_2018, pes_linies, factors):
    """GDD 2.2: `entrades_feiner(e, any) = entrades_2018(e) × Σ_l s(e, l) × f(l)`.

    `pes_linies[e]` = {línia: expedicions}; `factors[línia]` = f(l). Les línies sense factor fan servir el
    factor de xarxa (`factors[None]`).
    """
    resultat = {}
    for estacio, entrades in entrades_2018.items():
        expedicions = pes_linies.get(estacio) or {None: 1.0}
        total = sum(expedicions.values())
        factor = sum(n / total * factors.get(l, factors[None]) for l, n in expedicions.items())
        resultat[estacio] = entrades * factor
    return resultat


def pes_regional(expedicions, poblacio_municipi, exponent):
    """GDD 2.2: `expedicions_diàries_estació × població_municipi^exponent`."""
    return expedicions * max(poblacio_municipi, 1.0) ** exponent


def pes_parada(expedicions, poblacio_radi, divisor):
    """GDD 2.3: `expedicions_diàries_parada × (1 + població_400m / 1.000)`."""
    return expedicions * (1 + poblacio_radi / divisor)


def reparteix(total, pesos):
    """Reparteix `total` entre les claus de `pesos` proporcionalment al pes."""
    suma = sum(pesos.values())
    if suma <= 0:
        return {k: 0.0 for k in pesos}
    return {k: total * p / suma for k, p in pesos.items()}


def model_parades(totals_linia, pes, parades_linia):
    """GDD 2.3: `viatgers_parada = Σ_línies total_línia × pes_parada / Σ pes_parades_línia`."""
    resultat = defaultdict(float)
    for linia, total in totals_linia.items():
        parades = parades_linia.get(linia, ())
        for parada, valor in reparteix(total, {p: pes[p] for p in parades}).items():
            resultat[parada] += valor
    return dict(resultat)


def ajust_exponent(punts):
    """Mínims quadrats de `log(entrades) = a + log(expedicions) + β · log(població)`: el β que millor
    reprodueix les estacions amb dada (GDD 2.2, calibratge de l'exponent). `punts` = [(entrades, exp, pob)]."""
    xs, ys = [], []
    for entrades, expedicions, poblacio in punts:
        if entrades > 0 and expedicions > 0 and poblacio > 0:
            xs.append(math.log(poblacio))
            ys.append(math.log(entrades) - math.log(expedicions))
    if len(xs) < 3:
        return None
    mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
    sxx = sum((x - mx) ** 2 for x in xs)
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sxx if sxx else None
