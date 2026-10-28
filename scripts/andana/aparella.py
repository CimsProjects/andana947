"""Aparellament de noms d'estacions entre fonts (per exemple, el full de càlcul de TMB i el GTFS)."""

from .text import normalitza


def _coincideix(a, b):
    """Dues paraules coincideixen si són iguals o si una és l'abreviatura de l'altra (H → HOSPITAL)."""
    return a == b or (len(a) <= 3 and b.startswith(a)) or (len(b) <= 3 and a.startswith(b))


def similitud(nom_a, nom_b):
    """Entre 0 i 1: la part de paraules del nom més curt que surten a l'altre, amb una penalització petita per
    les paraules de més (perquè «CATALUNYA» s'assembli més a «Catalunya» que a «Plaça de Catalunya»)."""
    a, b = normalitza(nom_a).split(), normalitza(nom_b).split()
    if not a or not b:
        return 0.0
    curt, llarg = (a, b) if len(a) <= len(b) else (b, a)
    comunes = sum(1 for p in curt if any(_coincideix(p, q) for q in llarg))
    return comunes / len(curt) - 0.01 * (len(llarg) - comunes)


def aparella(noms, candidats, minim=0.5):
    """Aparella cada nom de `noms` amb un dels `candidats` ({clau: nom}), com a molt un per candidat.

    Primer les parelles més semblants; al final, si només queda un nom i un candidat sense parella (la mateixa
    línia, per exemple), s'aparellen entre ells. Retorna {nom: clau}.
    """
    parelles = sorted(
        ((similitud(n, c), n, k) for n in noms for k, c in candidats.items()),
        key=lambda x: -x[0],
    )
    resultat, usats = {}, set()
    for puntuacio, nom, clau in parelles:
        if puntuacio < minim:
            break
        if nom in resultat or clau in usats:
            continue
        resultat[nom] = clau
        usats.add(clau)
    sobrers = [n for n in noms if n not in resultat]
    lliures = [k for k in candidats if k not in usats]
    if len(sobrers) == 1 and len(lliures) == 1:
        resultat[sobrers[0]] = lliures[0]
    return resultat
