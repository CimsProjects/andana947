"""La flota de cada línia (fase 5, GDD 4.7): quants vehicles circulen alhora en hora punta i quants km fan en un feiner.

Surt dels horaris reals de `circulacions.json`: cada expedició d'un feiner ocupa un vehicle des que surt fins que arriba
al final. El màxim d'expedicions que circulen alhora és la flota en hora punta [estimat]: la flota real és una mica més
gran (reserves, temps de girar al final de la línia), però no es publica per línia.

Una línia del joc és un codi d'un mode (`rodalies:R2`): els GTFS tenen diverses rutes per a la mateixa línia.
"""

from collections import defaultdict


def clau_linia(linia):
    return f"{linia['mode']}:{linia['codi']}"


def flota_per_linia(circulacions, tipus_dia="feiner"):
    """Per a cada línia (mode:codi): vehicles en hora punta, expedicions i km del dia."""
    linies = circulacions["linies"]
    patrons = circulacions["patrons"]
    pla = circulacions["expedicions"].get(tipus_dia, [])
    esdeveniments = defaultdict(list)
    resultat = defaultdict(lambda: {"vehicles_punta": 0, "expedicions": 0, "km": 0.0})
    for i in range(0, len(pla) - 1, 2):
        patro = patrons[pla[i]]
        clau = clau_linia(linies[patro["linia"]])
        sortida = pla[i + 1]
        arribada = sortida + patro["arribades"][-1]
        esdeveniments[clau] += [(sortida, 1), (arribada, -1)]
        resultat[clau]["expedicions"] += 1
        resultat[clau]["km"] += patro["distancies"][-1] / 1000.0
    for clau, llista in esdeveniments.items():
        # A la mateixa hora, primer arriben i després surten: un vehicle que arriba pot tornar a sortir.
        llista.sort(key=lambda e: (e[0], e[1]))
        ara = maxim = 0
        for _, canvi in llista:
            ara += canvi
            maxim = max(maxim, ara)
        resultat[clau]["vehicles_punta"] = maxim
    return dict(resultat)
