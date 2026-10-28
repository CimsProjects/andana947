"""Territori: municipis, comarques i població per seccions censals, amb consultes geomètriques.

Les geometries es projecten amb una projecció azimutal equivalent de Lambert centrada a Catalunya: conserva
les àrees (per a les densitats) i, a l'escala d'una parada, també les distàncies.
"""

import csv
import json
import math
import pathlib
from collections import defaultdict
from dataclasses import dataclass

from shapely import STRtree
from shapely.geometry import Point, shape
from shapely.ops import transform

RADI_TERRA = 6_371_008.8
LAT0, LON0 = math.radians(41.7), math.radians(1.8)


def projecta(lon, lat):
    """Lambert azimutal equivalent (esfera), en metres."""
    f, l = math.radians(lat), math.radians(lon)
    k = math.sqrt(2 / (1 + math.sin(LAT0) * math.sin(f) + math.cos(LAT0) * math.cos(f) * math.cos(l - LON0)))
    x = RADI_TERRA * k * math.cos(f) * math.sin(l - LON0)
    y = RADI_TERRA * k * (math.cos(LAT0) * math.sin(f) - math.sin(LAT0) * math.cos(f) * math.cos(l - LON0))
    return x, y


def _projecta_geometria(geometria):
    return transform(lambda x, y, z=None: _vectoritza(x, y), geometria)


def _vectoritza(xs, ys):
    if isinstance(xs, float):
        return projecta(xs, ys)
    parelles = [projecta(x, y) for x, y in zip(xs, ys)]
    return [p[0] for p in parelles], [p[1] for p in parelles]


def distancia(lat1, lon1, lat2, lon2):
    """Distància sobre l'esfera, en metres (haversinus), la mateixa que fa servir joc/."""
    f1, f2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((f2 - f1) / 2) ** 2 + math.cos(f1) * math.cos(f2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * RADI_TERRA * math.asin(math.sqrt(min(1.0, a)))


@dataclass
class Municipi:
    codi: str
    nom: str
    comarca: str
    superficie_km2: float
    poblacio: float = 0.0

    @property
    def densitat(self):
        return self.poblacio / self.superficie_km2 if self.superficie_km2 else 0.0


class Territori:
    def __init__(self, fitxer_municipis, fitxer_comarques):
        comarca_de = {}
        for c in json.loads(pathlib.Path(fitxer_comarques).read_text(encoding="utf-8")):
            for m in c["municipis"]:
                comarca_de[m] = c["codi"]
        dades = json.loads(pathlib.Path(fitxer_municipis).read_text(encoding="utf-8"))
        self.municipis = {}
        self._geometries = []
        self._codis = []
        for f in dades["features"]:
            codi = f["properties"]["codi_ine"]
            geometria = _projecta_geometria(shape(f["geometry"]))
            self.municipis[codi] = Municipi(codi, f["properties"]["nom"], comarca_de.get(codi, ""),
                                            geometria.area / 1e6)
            self._geometries.append(geometria)
            self._codis.append(codi)
        self._arbre = STRtree(self._geometries)
        self._seccions = []
        self._poblacio_seccions = []
        self._arbre_seccions = None
        self.seccions_estimades = 0

    def municipi_de(self, lat, lon):
        """Codi INE del municipi que conté el punt, o el més proper a menys de 500 m (costa, límits)."""
        punt = Point(projecta(lon, lat))
        for i in self._arbre.query(punt, predicate="intersects"):
            return self._codis[i]
        i = self._arbre.nearest(punt)
        if i is not None and self._geometries[i].distance(punt) < 500:
            return self._codis[i]
        return None

    def carrega_seccions(self, fitxer_geojson, fitxer_cens):
        """Població del Cens del 2021 (INE) per secció censal, amb la cartografia vigent de l'INE.

        Les seccions creades després del 2021 no tenen població al cens: s'estimen amb la densitat de les
        seccions del mateix municipi que sí que en tenen (i es compten a `seccions_estimades`).
        """
        poblacio = {}
        with open(fitxer_cens, encoding="utf-8-sig", newline="") as f:
            for fila in csv.DictReader(f):
                if fila["ccaa"] != "09":
                    continue
                cusec = fila["cpro"] + fila["cmun"] + fila["dist"] + fila["secc"]
                poblacio[cusec] = float(fila["t1_1"])
        dades = json.loads(pathlib.Path(fitxer_geojson).read_text(encoding="utf-8"))
        seccions = []
        for f in dades["features"]:
            p = f["properties"]
            if p.get("CCA") != "09":
                continue
            seccions.append((p["CUSEC"], p["CUMUN"], _projecta_geometria(shape(f["geometry"]))))
        conegut = defaultdict(lambda: [0.0, 0.0])
        for cusec, cumun, g in seccions:
            if cusec in poblacio:
                conegut[cumun][0] += poblacio[cusec]
                conegut[cumun][1] += g.area
        for cusec, cumun, g in seccions:
            if cusec in poblacio:
                valor = poblacio[cusec]
            else:
                total, area = conegut[cumun]
                valor = total / area * g.area if area else 0.0
                self.seccions_estimades += 1
            self._seccions.append(g)
            self._poblacio_seccions.append(valor)
            if cumun in self.municipis:
                self.municipis[cumun].poblacio += valor
        self._arbre_seccions = STRtree(self._seccions)
        return len(self._seccions)

    def poblacio_radi(self, lat, lon, radi):
        """Habitants a menys de `radi` metres, suposant la població repartida uniformement dins de cada secció."""
        cercle = Point(projecta(lon, lat)).buffer(radi, quad_segs=16)
        total = 0.0
        for i in self._arbre_seccions.query(cercle, predicate="intersects"):
            seccio = self._seccions[i]
            if seccio.area > 0:
                total += self._poblacio_seccions[i] * seccio.intersection(cercle).area / seccio.area
        return total
