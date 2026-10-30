"""Els trams de la xarxa: parells d'andanes seguides d'una mateixa línia (docs/abast.md, objectius d'«unir»).

El GTFS carregat a Supabase no desa l'ordre de les parades de cada línia. L'ordre surt dels recorreguts de
`circulacions.json` (els horaris reals de cada expedició): cada parada d'un recorregut s'aparella amb el node més
proper de la mateixa línia (linia_nodes.csv) i dues parades seguides aparellades amb nodes diferents fan un tram.
"""

from .circulacions import descodifica_polilinia, distancies_acumulades, punt_a
from .territori import distancia

# Fins a quina distància una parada d'un recorregut és un node de la seva línia. Només es busca entre els nodes de
# la mateixa línia, i les estacions es fusionen en un radi de 300 m (xarxa.py).
RADI_TREN_METRES = 300.0
RADI_BUS_METRES = 80.0


def node_mes_proper(lat, lon, candidats, radi):
    """El node (id, lat, lon) de `candidats` més proper al punt, si és dins del radi."""
    millor, millor_d = None, radi
    for node_id, nlat, nlon in candidats:
        d = distancia(lat, lon, nlat, nlon)
        if d <= millor_d:
            millor, millor_d = node_id, d
    return millor


def trams(circulacions, nodes_per_linia, radi_tren=RADI_TREN_METRES, radi_bus=RADI_BUS_METRES):
    """Els trams (linia_id, node_a, node_b), amb node_a < node_b, i un recompte de parades aparellades.

    `circulacions` és el contingut de circulacions.json; `nodes_per_linia`, linia_id -> [(node_id, lat, lon)].
    """
    linies = circulacions["linies"]
    tracats = [None] * len(circulacions["tracats"])
    resultat = set()
    aparellades = sense_node = 0
    for patro in circulacions["patrons"]:
        linia = linies[patro["linia"]]
        candidats = nodes_per_linia.get(linia["id"])
        if not candidats:
            continue
        radi = radi_bus if linia["mode"] == "bus" else radi_tren
        i_tracat = patro["tracat"]
        if tracats[i_tracat] is None:
            punts = descodifica_polilinia(circulacions["tracats"][i_tracat])
            tracats[i_tracat] = (punts, distancies_acumulades(punts))
        punts, acumulades = tracats[i_tracat]
        parades = patro.get("parades") or [0] * len(patro["distancies"])
        anterior = None
        for d, parada in zip(patro["distancies"], parades):
            # Les parades virtuals de la frontera (-1) no són de Catalunya: tallen el recorregut.
            node = None
            if parada >= 0:
                lat, lon = punt_a(punts, acumulades, d)
                node = node_mes_proper(lat, lon, candidats, radi)
                if node is None:
                    sense_node += 1
                else:
                    aparellades += 1
            if node is not None and anterior is not None and node != anterior:
                resultat.add((linia["id"], min(node, anterior), max(node, anterior)))
            anterior = node
    return sorted(resultat), aparellades, sense_node
