"""Els trams del joc (GDD 4.0): cada línia real és un tram, amb una mida (petit, mitjà o gran) segons les arribades
a andana d'un feiner i un preu segons les monedes que dona.

Les regles són les mateixes que les de `joc/` (`Trams.kt`), i `TramsTest` comprova que `trams.csv` hi quadra.
"""

import math
import re
from dataclasses import dataclass, field

# Codis de Renfe que no són una línia sinó un tipus de tren: cada recorregut (origen i destinació) és un tram.
CODIS_GENERICS = {"REGIONAL": "Regional", "REG.EXP.": "Regional exprés", "MD": "Mitjana distància"}
PETIT, MITJA, GRAN = "petit", "mitja", "gran"


@dataclass
class Tram:
    id: str
    operador: str
    mode: str
    codi: str
    nom: str
    color: str = ""
    linies: list = field(default_factory=list)
    nodes: set = field(default_factory=set)
    arribades_feiner: int = 0
    mida: str = ""
    preu: int | None = None

    @property
    def parades(self):
        return len(self.nodes)


def estacions_del_recorregut(id_linia):
    """Les estacions d'origen i de destinació d'un recorregut de Renfe, que van al principi del route_id:
    `renfe_md:7140079400VRE` és de 71400 a 79400. None si l'identificador no té aquesta forma."""
    m = re.fullmatch(r"[^:]+:(\d{5})(\d{5})\w*", id_linia)
    return (m.group(1), m.group(2)) if m else None


def clau_tram(linia):
    """Les línies amb la mateixa clau són el mateix tram: tots els sentits i variants d'una línia junts."""
    if linia["operador"] == "Renfe" and linia["codi"] in CODIS_GENERICS:
        estacions = estacions_del_recorregut(linia["id"])
        if estacions:
            return (linia["operador"], linia["mode"], linia["codi"], tuple(sorted(estacions)))
        return (linia["operador"], linia["mode"], linia["codi"], linia["id"])
    return (linia["operador"], linia["mode"], linia["codi"])


def id_tram(clau, linies):
    prefix = min(l["id"].split(":", 1)[0] for l in linies)
    if len(clau) == 4:
        extrem = "-".join(clau[3]) if isinstance(clau[3], tuple) else clau[3].split(":", 1)[-1]
        return f"{prefix}:{clau[2]}:{extrem}"
    return f"{prefix}:{clau[2]}"


def nom_tram(clau, linies, noms_nodes):
    if len(clau) == 4 and isinstance(clau[3], tuple):
        tipus = CODIS_GENERICS[clau[2]]
        extrems = [noms_nodes.get(f"renfe:{codi}") for codi in clau[3]]
        if all(extrems):
            return f"{tipus} {extrems[0]} – {extrems[1]}"
        coneguts = [e for e in extrems if e]
        if coneguts:
            return f"{tipus} {coneguts[0]} – fora de Catalunya"
        return tipus
    noms = sorted({l["nom"].strip() for l in linies if l["nom"].strip()})
    return noms[0] if noms else clau[2]


def agrupa(linies, linia_nodes, noms_nodes):
    """Els trams a partir de les files de linies.csv i linia_nodes.csv (diccionaris)."""
    per_clau = {}
    for linia in linies:
        per_clau.setdefault(clau_tram(linia), []).append(linia)
    tram_de_linia = {}
    trams = []
    for clau, del_tram in per_clau.items():
        del_tram.sort(key=lambda l: l["id"])
        tram = Tram(
            id=id_tram(clau, del_tram),
            operador=clau[0],
            mode=clau[1],
            codi=clau[2],
            nom=nom_tram(clau, del_tram, noms_nodes),
            color=next((l["color"].upper() for l in del_tram if l["color"]), ""),
            linies=[l["id"] for l in del_tram],
        )
        trams.append(tram)
        for l in del_tram:
            tram_de_linia[l["id"]] = tram
    for fila in linia_nodes:
        tram = tram_de_linia.get(fila["linia_id"])
        if tram:
            tram.nodes.add(fila["node_id"])
            tram.arribades_feiner += int(fila["expedicions_feiner"])
    ids = [t.id for t in trams]
    assert len(ids) == len(set(ids)), "Hi ha identificadors de tram repetits"
    return sorted(trams, key=lambda t: t.id)


def talls(arribades):
    """Les arribades on comencen els trams mitjans i els grans: els terços de tots els trams."""
    a = sorted(arribades)
    n = len(a)
    return a[n // 3], a[2 * n // 3]


def mida(arribades, tall_mitja, tall_gran):
    if arribades < tall_mitja:
        return PETIT
    if arribades < tall_gran:
        return MITJA
    return GRAN


def arrodoneix(valor, pas):
    """Arrodoneix al múltiple de [pas] més proper; els mitjos, cap amunt (com `Trams.kt`)."""
    return int(math.floor(valor / pas + 0.5)) * pas


def preu(arribades, mida_tram, config):
    """El preu d'un tram petit o mitjà: les monedes que dona en `diesRetorn` dies. Els grans no tenen preu (es
    compraran per fraccions)."""
    if mida_tram == GRAN:
        return None
    if mida_tram == PETIT:
        mitjana = (config["recompensaPetitMinima"] + config["recompensaPetitMaxima"]) / 2
    else:
        mitjana = (config["recompensaMitjaMinima"] + config["recompensaMitjaMaxima"]) / 2
    valor = arrodoneix(arribades * mitjana * config["diesRetorn"], config["arrodonimentPreu"])
    return max(config["preuMinim"], valor)


def classifica(trams, config):
    """Descarta els trams massa curts i sense servei, i dona a la resta la mida i el preu. Torna els trams
    jugables i els talls."""
    jugables = [t for t in trams if t.parades >= config["minimParades"] and t.arribades_feiner > 0]
    tall_mitja, tall_gran = talls([t.arribades_feiner for t in jugables])
    for t in jugables:
        t.mida = mida(t.arribades_feiner, tall_mitja, tall_gran)
        t.preu = preu(t.arribades_feiner, t.mida, config)
    return jugables, (tall_mitja, tall_gran)
