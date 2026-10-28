"""Construcció dels nodes i de les línies del joc a partir dels GTFS (GDD, secció 3)."""

import re
from collections import defaultdict
from dataclasses import dataclass, field

from . import gtfs
from .territori import distancia
from .text import normalitza

REGIONALS = {"R11", "R12", "R13", "R14", "R15", "R16", "R17", "RG1", "RT1", "RT2", "RL3", "RL4", "R10"}
RODALIES = re.compile(r"^R\d+(N|S|NORD|SUD)?$")
ALTA_VELOCITAT = re.compile(r"^(AVE|AVLO|ALVIA|AVANT|EUROMED|INTERCITY|LD|TRENHOTEL|OUIGO|IRYO)", re.I)


@dataclass
class Linia:
    id: str
    operador: str
    mode: str
    codi: str
    nom: str
    color: str
    color_text: str
    expedicions_feiner: int = 0


@dataclass
class Node:
    id: str
    nom: str
    mode: str
    operador: str
    lat: float
    lon: float
    # Expedicions d'un feiner de cada línia (id de Linia) que hi para.
    expedicions: dict = field(default_factory=lambda: defaultdict(int))
    # (feed, stop_id) del GTFS que formen el node.
    parades_gtfs: set = field(default_factory=set)
    # Clau de l'operador per a l'afluència (codi d'estació de Renfe, per exemple).
    clau: str = ""
    municipi: str = ""
    comarca: str = ""

    @property
    def expedicions_feiner(self):
        return sum(self.expedicions.values())


# --- Mode de cada línia, feed per feed ------------------------------------------------------------------------

def mode_tmb(ruta):
    if ruta.tipus in gtfs.TIPUS_METRO:
        return "metro"
    if ruta.tipus in gtfs.TIPUS_BUS:
        return "bus"
    return None


def mode_fgc(ruta):
    return "fgc" if ruta.tipus in gtfs.TIPUS_METRO | gtfs.TIPUS_TREN else None


def mode_renfe(ruta):
    if ruta.tipus not in gtfs.TIPUS_TREN | {2}:
        return None
    nom = (ruta.nom_curt or ruta.nom_llarg).strip().upper()
    if ALTA_VELOCITAT.match(nom):
        return None
    compacte = nom.replace(" ", "")
    if compacte in REGIONALS or not RODALIES.match(compacte):
        return "regional"
    return "rodalies"


def mode_tram(ruta):
    return "tram" if ruta.tipus in gtfs.TIPUS_TRAMVIA else None


def mode_bus(ruta):
    return "bus" if ruta.tipus in gtfs.TIPUS_BUS else None


@dataclass
class ConfigFeed:
    id: str
    # Prefix dels identificadors dels nodes (els dos feeds de Renfe comparteixen els codis d'estació).
    prefix: str
    operador: str
    mode: object
    # Per als feeds de Renfe: la clau de l'estació és el codi (stop_id sense zeros a l'esquerra).
    clau_codi: bool = False
    # Agències que no s'importen d'aquest feed (perquè ja surten en un altre).
    agencies_excloses: tuple = ()


FEEDS = [
    ConfigFeed("gtfs_tmb", "tmb", "TMB", mode_tmb),
    ConfigFeed("gtfs_fgc", "fgc", "FGC", mode_fgc),
    ConfigFeed("gtfs_rodalies", "renfe", "Renfe", mode_renfe, clau_codi=True),
    ConfigFeed("gtfs_renfe_md", "renfe", "Renfe", mode_renfe, clau_codi=True),
    ConfigFeed("gtfs_trambaix", "tram", "TRAM", mode_tram),
    ConfigFeed("gtfs_trambesos", "tram", "TRAM", mode_tram),
    ConfigFeed("gtfs_amb", "amb", "AMB", mode_bus, agencies_excloses=("TMB", "TRANSPORTS DE BARCELONA")),
    ConfigFeed("gtfs_interurbans", "gencat", "Generalitat de Catalunya", mode_bus),
]


class Xarxa:
    """Els nodes i les línies de tots els feeds, amb les parades fusionades."""

    def __init__(self, territori, radi_fusio_bus):
        self.territori = territori
        self.radi_fusio_bus = radi_fusio_bus
        self.linies = {}
        self.nodes = {}
        self.feeds = {}
        self.diagnosi = []

    # Una parada física del GTFS (no una estació pare ni una entrada).
    @staticmethod
    def _es_parada(p):
        return p.tipus_ubicacio == 0

    def afegeix_feed(self, config, feed):
        self.feeds[config.id] = feed
        modes = {}
        for ruta in feed.rutes.values():
            agencia = feed.agencies.get(ruta.agencia, "").upper()
            if any(e in agencia for e in config.agencies_excloses):
                continue
            mode = config.mode(ruta)
            if mode:
                modes[ruta.id] = mode
        # Només les parades dins de Catalunya.
        expedicions = feed.expedicions_totes()
        municipi = {}
        for parada_id, ruta_id in expedicions:
            if ruta_id not in modes or parada_id in municipi:
                continue
            p = feed.parades.get(parada_id)
            municipi[parada_id] = self.territori.municipi_de(p.lat, p.lon) if p else None
        linies_amb_parades = {r for (p, r) in expedicions if r in modes and municipi.get(p)}
        id_de_ruta = {}
        for ruta_id in linies_amb_parades:
            ruta = feed.rutes[ruta_id]
            id_linia = f"{config.id.removeprefix('gtfs_')}:{ruta.id}"
            id_de_ruta[ruta_id] = id_linia
            self.linies[id_linia] = Linia(
                id=id_linia,
                operador=config.operador,
                mode=modes[ruta_id],
                codi=ruta.nom_curt or ruta.nom_llarg or ruta.id,
                nom=ruta.nom_llarg,
                color=ruta.color,
                color_text=ruta.color_text,
                expedicions_feiner=feed.expedicions_ruta[ruta_id],
            )
        alternatives = 0
        for (parada_id, ruta_id), n in expedicions.items():
            if ruta_id not in id_de_ruta or not municipi.get(parada_id):
                continue
            if (parada_id, ruta_id) in feed.expedicions_alternatives:
                alternatives += 1
            p = feed.parades[parada_id]
            mode = modes[ruta_id]
            node = self._node_per(config, feed, p, mode, municipi[parada_id])
            node.expedicions[id_de_ruta[ruta_id]] += n
            node.parades_gtfs.add((config.id, parada_id))
        self.diagnosi.append(
            f"{config.id}: dia de referència {feed.dia_referencia}, {len(feed.rutes)} línies al feed, "
            f"{len(linies_amb_parades)} importades; {alternatives} parades-línia sense servei aquell dia, "
            f"comptades amb un dimecres posterior"
        )

    def _node_per(self, config, feed, parada, mode, municipi):
        if mode == "bus":
            clau = f"bus:{config.prefix}:{parada.id}"
            pos = parada
        elif config.clau_codi:
            codi = parada.id.lstrip("0") or parada.id
            clau = f"renfe:{codi}"
            pos = parada
        else:
            pare = feed.parades.get(parada.pare) if parada.pare else None
            pos = pare or parada
            clau = f"{config.prefix}:{pos.id}"
        node = self.nodes.get(clau)
        if node is None:
            node = Node(id=clau, nom=pos.nom, mode=mode, operador=config.operador, lat=pos.lat, lon=pos.lon,
                        clau=clau.split(":", 1)[1], municipi=municipi)
            self.nodes[clau] = node
        elif mode == "rodalies" and node.mode == "regional":
            # Una estació amb Rodalies i regionals és de Rodalies.
            node.mode = "rodalies"
        return node

    # --- Fusions ------------------------------------------------------------------------------------------

    def fusiona_estacions(self, radi=300):
        """Estacions del mateix operador i mode amb el mateix nom a menys de `radi` metres (per exemple, les
        estacions pare que un GTFS dona per a cada línia): un sol node."""
        grups = defaultdict(list)
        for node in self.nodes.values():
            if node.mode != "bus":
                grups[(node.operador, node.mode, normalitza(node.nom))].append(node)
        for nodes in grups.values():
            self._fusiona_grup(nodes, radi)

    def fusiona_parades_bus(self):
        """Parades de bus amb el mateix nom a menys del radi de fusió (els dos sentits, o la mateixa parada en
        feeds de dos operadors): un sol node (GDD, secció 3)."""
        grups = defaultdict(list)
        for node in self.nodes.values():
            if node.mode == "bus":
                grups[normalitza(node.nom)].append(node)
        for nodes in grups.values():
            self._fusiona_grup(nodes, self.radi_fusio_bus)

    def _fusiona_grup(self, nodes, radi):
        if len(nodes) < 2:
            return
        pare = {n.id: n.id for n in nodes}

        def arrel(x):
            while pare[x] != x:
                pare[x] = pare[pare[x]]
                x = pare[x]
            return x

        for i, a in enumerate(nodes):
            for b in nodes[i + 1:]:
                if distancia(a.lat, a.lon, b.lat, b.lon) <= radi:
                    pare[arrel(a.id)] = arrel(b.id)
        components = defaultdict(list)
        for n in nodes:
            components[arrel(n.id)].append(n)
        for membres in components.values():
            if len(membres) < 2:
                continue
            membres.sort(key=lambda n: (-n.expedicions_feiner, n.id))
            principal = membres[0]
            total = sum(max(m.expedicions_feiner, 1) for m in membres)
            principal.lat = sum(m.lat * max(m.expedicions_feiner, 1) for m in membres) / total
            principal.lon = sum(m.lon * max(m.expedicions_feiner, 1) for m in membres) / total
            for m in membres[1:]:
                for linia, n in m.expedicions.items():
                    principal.expedicions[linia] += n
                principal.parades_gtfs |= m.parades_gtfs
                if m.mode == "rodalies":
                    principal.mode = "rodalies"
                del self.nodes[m.id]

    def assigna_territori(self):
        for node in self.nodes.values():
            node.municipi = self.territori.municipi_de(node.lat, node.lon) or node.municipi
            municipi = self.territori.municipis.get(node.municipi)
            node.comarca = municipi.comarca if municipi else ""

    def nodes_de_linia(self):
        resultat = defaultdict(set)
        for node in self.nodes.values():
            for linia in node.expedicions:
                resultat[linia].add(node.id)
        return resultat

    def codis_linia(self, node):
        return sorted({self.linies[l].codi for l in node.expedicions if l in self.linies})
