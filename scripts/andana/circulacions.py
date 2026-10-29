"""Horaris i traçats dels trens i dels busos per moure'ls al mapa (vehicles en circulació, docs/vehicles.md).

De cada expedició d'un dia tipus (feiner, dissabte i festiu) es desa a quina hora surt i el *patró* que segueix: el
traçat de la via, a quants metres del traçat hi ha cada parada i quants segons després de sortir hi arriba i en surt.
Amb això l'app calcula on és cada tren en qualsevol moment (posició *estimada* segons l'horari).

Quan el GTFS no té traçats (`shapes.txt`), el traçat és la línia recta entre parades consecutives.
"""

import math
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field

from . import gtfs
from .territori import distancia

TIPUS_DIA = ("feiner", "dissabte", "festiu")
# Dia de la setmana que representa cada tipus de dia: dimecres, dissabte i diumenge.
DIA_SETMANA = {"feiner": 2, "dissabte": 5, "festiu": 6}

# Tolerància de la simplificació dels traçats, en metres: a l'escala d'un tren no es nota i el fitxer és molt més petit.
TOLERANCIA_SIMPLIFICACIO = 4.0
# Una parada a més d'aquesta distància del traçat vol dir que el traçat no és bo: es fa servir la recta entre parades.
MAX_DISTANCIA_PARADA_TRACAT = 300.0


# --- Geometria ------------------------------------------------------------------------------------------------

def _xy(lat, lon, lat0):
    """Projecció local equirectangular, en metres (prou bona a l'escala d'un traçat)."""
    r = 6_371_008.8
    return math.radians(lon) * r * math.cos(math.radians(lat0)), math.radians(lat) * r


def simplifica(punts, tolerancia=TOLERANCIA_SIMPLIFICACIO):
    """Douglas-Peucker sobre punts (lat, lon), amb la tolerància en metres. Conserva el primer i l'últim."""
    if len(punts) < 3:
        return list(punts)
    lat0 = punts[0][0]
    xy = [_xy(lat, lon, lat0) for lat, lon in punts]
    conserva = [False] * len(punts)
    conserva[0] = conserva[-1] = True
    pila = [(0, len(punts) - 1)]
    while pila:
        i, j = pila.pop()
        (x1, y1), (x2, y2) = xy[i], xy[j]
        dx, dy = x2 - x1, y2 - y1
        llarg2 = dx * dx + dy * dy
        millor, idx = -1.0, -1
        for k in range(i + 1, j):
            x, y = xy[k]
            if llarg2 == 0:
                d = math.hypot(x - x1, y - y1)
            else:
                t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / llarg2))
                d = math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))
            if d > millor:
                millor, idx = d, k
        if millor > tolerancia:
            conserva[idx] = True
            pila += [(i, idx), (idx, j)]
    return [p for p, c in zip(punts, conserva) if c]


def distancies_acumulades(punts):
    """Metres des del primer punt fins a cada punt, amb la mateixa distància que fa servir l'app (haversinus)."""
    resultat = [0.0]
    for (a, b), (c, d) in zip(punts, punts[1:]):
        resultat.append(resultat[-1] + distancia(a, b, c, d))
    return resultat


def projecta_parades(punts, parades):
    """Per a cada parada (lat, lon), a quants metres del traçat és i a quina distància en queda.

    Les parades van en ordre: cada una es busca a partir de la posició de l'anterior, perquè un traçat que passa dues
    vegades pel mateix lloc (una línia circular, una anada i tornada) no faci tirar enrere el tren.
    Retorna (distàncies sobre el traçat, distància màxima d'una parada al traçat).
    """
    acumulades = distancies_acumulades(punts)
    lat0 = punts[0][0]
    xy = [_xy(lat, lon, lat0) for lat, lon in punts]
    resultat, pitjor, inici, t_inici = [], 0.0, 0, 0.0
    for lat, lon in parades:
        px, py = _xy(lat, lon, lat0)
        millor = (math.inf, acumulades[-1], inici, t_inici)
        for k in range(inici, len(punts) - 1):
            (x1, y1), (x2, y2) = xy[k], xy[k + 1]
            dx, dy = x2 - x1, y2 - y1
            llarg2 = dx * dx + dy * dy
            t = 0.0 if llarg2 == 0 else max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / llarg2))
            # Al tram de la parada anterior, només endavant.
            if k == inici:
                t = max(t, t_inici)
            d = math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))
            if d < millor[0] - 1e-6:
                millor = (d, acumulades[k] + t * (acumulades[k + 1] - acumulades[k]), k, t)
        d, sobre_tracat, inici, t_inici = millor
        resultat.append(sobre_tracat)
        pitjor = max(pitjor, d)
    return resultat, pitjor


def punt_a(punts, acumulades, d):
    """El punt a `d` metres de l'inici de la polilínia."""
    d = min(max(d, 0.0), acumulades[-1])
    for k in range(len(punts) - 1):
        if acumulades[k + 1] >= d:
            llarg = acumulades[k + 1] - acumulades[k]
            t = 0.0 if llarg == 0 else (d - acumulades[k]) / llarg
            (a, b), (c, e) = punts[k], punts[k + 1]
            return a + t * (c - a), b + t * (e - b)
    return punts[-1]


def tros(punts, acumulades, desde, fins):
    """Els punts de la polilínia entre `desde` i `fins` metres, amb els dos extrems."""
    resultat = [punt_a(punts, acumulades, desde)]
    resultat += [p for p, d in zip(punts, acumulades) if desde < d < fins]
    resultat.append(punt_a(punts, acumulades, fins))
    return resultat


def frontera(punts, acumulades, d_dins, d_fora, dins, precisio=10.0):
    """La distància on la via surt del territori (`dins`) entre un punt de dins i un de fora (cerca binària)."""
    while abs(d_fora - d_dins) > precisio:
        mig = (d_dins + d_fora) / 2
        if dins(*punt_a(punts, acumulades, mig)):
            d_dins = mig
        else:
            d_fora = mig
    return d_dins


@dataclass
class Retall:
    """La part d'un recorregut dins del territori: de la parada `primera` a la `ultima`, més les fronteres (en metres
    del traçat original) si el recorregut ve de fora o hi va."""

    punts: list
    distancies: list
    primera: int
    ultima: int
    entrada: float | None
    sortida: float | None


def retalla(punts, distancies, dins):
    """Es queda des de la primera parada de dins fins a l'última. Si el recorregut ve de fora o hi va, la via
    s'allarga fins a la frontera, on hi haurà una parada virtual perquè el tren hi aparegui o hi desaparegui.
    Retorna None si cap parada és de dins. Les distàncies noves comencen a 0."""
    acumulades = distancies_acumulades(punts)
    interiors = [i for i, d in enumerate(distancies) if dins(*punt_a(punts, acumulades, d))]
    if not interiors:
        return None
    primera, ultima = interiors[0], interiors[-1]
    if primera == 0 and ultima == len(distancies) - 1:
        return Retall(punts, list(distancies), primera, ultima, None, None)
    entrada = frontera(punts, acumulades, distancies[primera], distancies[primera - 1], dins) if primera > 0 else None
    sortida = (frontera(punts, acumulades, distancies[ultima], distancies[ultima + 1], dins)
               if ultima < len(distancies) - 1 else None)
    noves = list(distancies[primera:ultima + 1])
    if entrada is not None:
        noves.insert(0, entrada)
    if sortida is not None:
        noves.append(sortida)
    inici = noves[0]
    return Retall(tros(punts, acumulades, inici, noves[-1]), [d - inici for d in noves], primera, ultima, entrada, sortida)


def temps_retallats(retall, distancies, temps):
    """Els temps (arribada, sortida) de cada parada del retall. A les fronteres, l'hora s'interpola per la distància
    entre la sortida de la parada d'abans i l'arribada a la de després."""
    def interpola(i, j, d):
        t0, t1 = temps[i][1], temps[j][0]
        f = 0.0 if distancies[j] == distancies[i] else (d - distancies[i]) / (distancies[j] - distancies[i])
        t = round(t0 + f * (t1 - t0))
        return t, t

    resultat = list(temps[retall.primera:retall.ultima + 1])
    if retall.entrada is not None:
        resultat.insert(0, interpola(retall.primera - 1, retall.primera, retall.entrada))
    if retall.sortida is not None:
        resultat.append(interpola(retall.ultima, retall.ultima + 1, retall.sortida))
    return resultat


def codifica_polilinia(punts, precisio=5):
    """Polilínia codificada de Google (l'algorisme de https://developers.google.com/maps/documentation/utilities/polylinealgorithm)."""
    factor = 10 ** precisio
    sortida, ant_lat, ant_lon = [], 0, 0
    for lat, lon in punts:
        ilat, ilon = round(lat * factor), round(lon * factor)
        for valor in (ilat - ant_lat, ilon - ant_lon):
            valor = ~(valor << 1) if valor < 0 else valor << 1
            while valor >= 0x20:
                sortida.append(chr((0x20 | (valor & 0x1F)) + 63))
                valor >>= 5
            sortida.append(chr(valor + 63))
        ant_lat, ant_lon = ilat, ilon
    return "".join(sortida)


def descodifica_polilinia(text, precisio=5):
    factor = 10 ** precisio
    punts, i, lat, lon = [], 0, 0, 0
    while i < len(text):
        valors = []
        for _ in range(2):
            resultat, desplacament = 0, 0
            while True:
                b = ord(text[i]) - 63
                i += 1
                resultat |= (b & 0x1F) << desplacament
                desplacament += 5
                if b < 0x20:
                    break
            valors.append(~(resultat >> 1) if resultat & 1 else resultat >> 1)
        lat += valors[0]
        lon += valors[1]
        punts.append((lat / factor, lon / factor))
    return punts


# --- Dies de referència ---------------------------------------------------------------------------------------

def tria_dia(recompte, dia_setmana, festius=(), des_de=None):
    """El dia de la setmana `dia_setmana` amb més serveis a partir de `des_de` (o de tot el feed, si no n'hi ha)."""
    candidats = [d for d in recompte if d.weekday() == dia_setmana and d not in festius and recompte[d] > 0]
    if des_de is not None:
        candidats = [d for d in candidats if d >= des_de] or candidats
    if not candidats:
        return None
    maxim = max(recompte[d] for d in candidats)
    return min(d for d in candidats if recompte[d] >= 0.95 * maxim)


# --- Lectura dels horaris -------------------------------------------------------------------------------------

@dataclass
class Patro:
    linia: str
    tracat: int
    distancies: tuple
    arribades: tuple
    sortides: tuple


@dataclass
class Circulacions:
    """Les línies, els traçats, els patrons i les expedicions de tots els feeds afegits."""

    linies: dict = field(default_factory=dict)          # id de línia -> fila de linies.csv
    tracats: list = field(default_factory=list)          # punts (lat, lon) simplificats
    patrons: list = field(default_factory=list)
    expedicions: dict = field(default_factory=lambda: {t: [] for t in TIPUS_DIA})   # tipus -> [(patró, inici)]
    dies: dict = field(default_factory=dict)             # feed -> {tipus: data}
    diagnosi: list = field(default_factory=list)
    _index_tracats: dict = field(default_factory=dict)
    _index_patrons: dict = field(default_factory=dict)

    def _tracat(self, clau, punts):
        if clau not in self._index_tracats:
            self._index_tracats[clau] = len(self.tracats)
            self.tracats.append(punts)
        return self._index_tracats[clau]

    def _patro(self, patro):
        clau = (patro.linia, patro.tracat, patro.distancies, patro.arribades, patro.sortides)
        if clau not in self._index_patrons:
            self._index_patrons[clau] = len(self.patrons)
            self.patrons.append(patro)
        return self._index_patrons[clau]

    def afegeix_feed(self, cami, id_feed, linies, festius=(), des_de=None, dins=None):
        """Afegeix les expedicions de les rutes del feed que són a `linies` ({route_id: id de línia}).

        Amb `dins(lat, lon)`, només la part de cada recorregut que és dins del territori (Catalunya).
        """
        with zipfile.ZipFile(cami) as z:
            calendari = gtfs.Calendari(z)
            recompte = calendari.recompte()
            dies = {t: tria_dia(recompte, DIA_SETMANA[t], festius, des_de) for t in TIPUS_DIA}
            self.dies[id_feed] = dies
            actius = {t: (calendari.actius(d) if d else set()) for t, d in dies.items()}

            parades = {}
            for fila in gtfs._files(z, "stops.txt"):
                try:
                    parades[fila["stop_id"]] = (float(fila["stop_lat"]), float(fila["stop_lon"]))
                except (KeyError, ValueError):
                    continue

            viatges = {}
            for fila in gtfs._files(z, "trips.txt"):
                linia = linies.get(fila["route_id"])
                if linia is None:
                    continue
                tipus = [t for t in TIPUS_DIA if fila["service_id"] in actius[t]]
                if tipus:
                    viatges[fila["trip_id"]] = (linia, fila.get("shape_id", ""), tipus)

            frequencies = defaultdict(list)
            for fila in gtfs._files(z, "frequencies.txt"):
                if fila["trip_id"] in viatges and int(fila.get("headway_secs") or 0) > 0:
                    frequencies[fila["trip_id"]].append(
                        (gtfs._segons(fila["start_time"]), gtfs._segons(fila["end_time"]), int(fila["headway_secs"])))

            formes = defaultdict(list)
            formes_usades = {s for _, s, _ in viatges.values() if s}
            for fila in gtfs._files(z, "shapes.txt"):
                if fila["shape_id"] in formes_usades:
                    formes[fila["shape_id"]].append(
                        (int(float(fila["shape_pt_sequence"])), float(fila["shape_pt_lat"]), float(fila["shape_pt_lon"])))
            formes = {s: [(lat, lon) for _, lat, lon in sorted(p)] for s, p in formes.items()}

            horaris = defaultdict(list)
            for fila in gtfs._files(z, "stop_times.txt"):
                if fila["trip_id"] not in viatges or fila.get("stop_id") not in parades:
                    continue
                arribada = fila.get("arrival_time") or fila.get("departure_time")
                sortida = fila.get("departure_time") or arribada
                if not arribada:
                    continue
                horaris[fila["trip_id"]].append(
                    (int(fila["stop_sequence"]), fila["stop_id"], gtfs._segons(arribada), gtfs._segons(sortida)))

        rectes, retallats, n_exp = 0, 0, defaultdict(int)
        projeccions, retalls = {}, {}
        for viatge, files in horaris.items():
            if len(files) < 2:
                continue
            files.sort()
            linia, forma, tipus = viatges[viatge]
            ids = tuple(f[1] for f in files)
            posicions = [parades[i] for i in ids]
            clau_proj = (forma, ids)
            if clau_proj not in projeccions:
                tracat = None
                if forma in formes and len(formes[forma]) >= 2:
                    punts = simplifica(formes[forma])
                    distancies, pitjor = projecta_parades(punts, posicions)
                    if pitjor <= MAX_DISTANCIA_PARADA_TRACAT and distancies[-1] > distancies[0]:
                        tracat = (punts, distancies)
                if tracat is None:
                    rectes += 1
                    tracat = ((id_feed, ids), posicions, distancies_acumulades(posicions))
                else:
                    tracat = ((id_feed, forma), tracat[0], tracat[1])
                projeccions[clau_proj] = tracat
            clau_tracat, punts, distancies = projeccions[clau_proj]
            temps = [(f[2], f[3]) for f in files]
            if dins is not None:
                if clau_proj not in retalls:
                    retalls[clau_proj] = retalla(punts, distancies, dins)
                retall = retalls[clau_proj]
                if retall is None or len(retall.distancies) < 2 or retall.distancies[-1] <= 0:
                    continue
                if retall.punts is not punts:
                    retallats += 1
                    clau_tracat = ("retall", codifica_polilinia(retall.punts))
                temps = temps_retallats(retall, distancies, temps)
                punts, distancies = retall.punts, retall.distancies
            index_tracat = self._tracat(clau_tracat, punts)
            inici = temps[0][1]
            patro = Patro(
                linia=linia,
                tracat=index_tracat,
                distancies=tuple(round(d) for d in distancies),
                arribades=tuple(t[0] - inici for t in temps),
                sortides=tuple(t[1] - inici for t in temps),
            )
            index = self._patro(patro)
            sortides = [inici]
            if viatge in frequencies:
                # Les freqüències són de la sortida de la primera parada del GTFS; el retall pot començar més tard.
                despres = inici - files[0][3]
                sortides = [s + despres for a, b, cada in frequencies[viatge] for s in range(a, b, cada)]
            for t in tipus:
                for s in sortides:
                    self.expedicions[t].append((index, s))
                    n_exp[t] += 1
        self.diagnosi.append(
            f"{id_feed}: dies {', '.join(f'{t} {d}' for t, d in dies.items())}; "
            + ", ".join(f"{n_exp[t]} expedicions en {t}" for t in TIPUS_DIA)
            + f"; {rectes} recorreguts sense traçat (recta entre parades); {retallats} expedicions retallades a la frontera"
        )

    def a_json(self, generat):
        """L'estructura del fitxer de l'app (docs/vehicles.md). Els índexs de línia i de traçat són posicions a les llistes."""
        ids_linia = sorted({p.linia for p in self.patrons})
        index_linia = {l: i for i, l in enumerate(ids_linia)}
        patrons = []
        for p in self.patrons:
            patro = {"linia": index_linia[p.linia], "tracat": p.tracat, "distancies": list(p.distancies),
                     "arribades": list(p.arribades)}
            if p.sortides != p.arribades:
                patro["sortides"] = list(p.sortides)
            patrons.append(patro)
        expedicions = {}
        for t in TIPUS_DIA:
            pla = []
            for patro, inici in sorted(self.expedicions[t], key=lambda e: (e[1], e[0])):
                pla += [patro, inici]
            expedicions[t] = pla
        return {
            "versio": 1,
            "generat": generat,
            "descripcio": "Horaris i traçats dels trens i dels busos per moure'ls al mapa. Posició estimada segons l'horari "
                          "(scripts/genera_circulacions.py, docs/vehicles.md).",
            "dies": {f: {t: (d.isoformat() if d else None) for t, d in dies.items()} for f, dies in self.dies.items()},
            "linies": [self._linia_json(self.linies[l]) for l in ids_linia],
            "tracats": [codifica_polilinia(p) for p in self.tracats],
            "patrons": patrons,
            "expedicions": expedicions,
        }

    @staticmethod
    def _linia_json(fila):
        return {"id": fila["id"], "codi": fila["codi"], "mode": fila["mode"], "operador": fila["operador"],
                "color": fila["color"].upper()}
