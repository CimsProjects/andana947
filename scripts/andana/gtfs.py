"""Lectura de fitxers GTFS (zip) i recompte d'expedicions d'un dia feiner.

Només llegeix el que el joc necessita: parades, línies, expedicions i el calendari de serveis. Els recomptes
són els d'un **dia de referència**: un dimecres feiner, sense festius, dins de la validesa del feed.
"""

import csv
import datetime as dt
import io
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field

DIES = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]

# route_type de GTFS (i els ampliats de Google) que fa servir el joc.
TIPUS_TRAMVIA = {0, 900}
TIPUS_METRO = {1, 400, 401, 402}
TIPUS_TREN = {2, 100, 101, 102, 103, 106, 109}
TIPUS_BUS = {3, 700, 702, 704, 715}


def tipus_ruta(valor):
    try:
        return int(valor)
    except (TypeError, ValueError):
        return -1


@dataclass
class Parada:
    id: str
    nom: str
    lat: float
    lon: float
    tipus_ubicacio: int = 0
    pare: str = ""
    codi: str = ""


@dataclass
class Ruta:
    id: str
    nom_curt: str
    nom_llarg: str
    tipus: int
    agencia: str = ""
    color: str = ""
    color_text: str = ""


@dataclass
class Feed:
    id: str
    parades: dict = field(default_factory=dict)
    rutes: dict = field(default_factory=dict)
    agencies: dict = field(default_factory=dict)
    dia_referencia: dt.date | None = None
    # Expedicions del dia de referència per (stop_id, route_id).
    expedicions_parada_ruta: Counter = field(default_factory=Counter)
    # Expedicions del dia de referència per route_id.
    expedicions_ruta: Counter = field(default_factory=Counter)
    # Parades sense servei el dia de referència: expedicions del dimecres posterior amb més servei.
    expedicions_alternatives: Counter = field(default_factory=Counter)

    def expedicions_totes(self):
        """Les del dia de referència i, per a les parades sense servei aquell dia, les alternatives."""
        return {**self.expedicions_alternatives, **self.expedicions_parada_ruta}

    def expedicions_parada(self):
        resultat = Counter()
        for (parada, _), n in self.expedicions_parada_ruta.items():
            resultat[parada] += n
        return resultat

    def rutes_de_parada(self):
        resultat = defaultdict(set)
        for parada, ruta in self.expedicions_parada_ruta:
            resultat[parada].add(ruta)
        return resultat


def _files(z, nom):
    if nom not in z.namelist():
        return
    with z.open(nom) as f:
        lector = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig", newline=""))
        for fila in lector:
            yield {(k or "").strip(): (v or "").strip() for k, v in fila.items()}


def _data(text):
    return dt.datetime.strptime(text, "%Y%m%d").date()


class Calendari:
    """calendar.txt i calendar_dates.txt, llegits una sola vegada."""

    def __init__(self, z):
        self.regles = [
            (fila["service_id"], _data(fila["start_date"]), _data(fila["end_date"]),
             {i for i, d in enumerate(DIES) if fila.get(d) == "1"})
            for fila in _files(z, "calendar.txt")
        ]
        self.excepcions = defaultdict(list)
        for fila in _files(z, "calendar_dates.txt"):
            self.excepcions[_data(fila["date"])].append((fila["service_id"], fila["exception_type"]))

    def actius(self, dia):
        """Serveis que funcionen el dia `dia`."""
        actius = {s for s, inici, fi, dies in self.regles if inici <= dia <= fi and dia.weekday() in dies}
        for servei, tipus in self.excepcions.get(dia, ()):
            if tipus == "1":
                actius.add(servei)
            elif tipus == "2":
                actius.discard(servei)
        return actius

    def dates(self):
        """Totes les dates del feed (els 400 primers dies de cada regla n'hi ha prou)."""
        dates = set(self.excepcions)
        for _, inici, fi, _ in self.regles:
            for i in range(min((fi - inici).days + 1, 400)):
                dates.add(inici + dt.timedelta(days=i))
        return dates

    def recompte(self):
        """Per a cada data, quants serveis hi ha actius (per triar el dia de referència)."""
        return Counter({dia: len(self.actius(dia)) for dia in self.dates()})


def tria_dia_referencia(recompte, festius=(), des_de=None):
    """El dimecres amb més serveis (els festius a part) a partir de `des_de`; si no n'hi ha, el de tot el feed."""
    candidats = [d for d in recompte if d.weekday() == 2 and d not in festius and recompte[d] > 0]
    if des_de is not None:
        posteriors = [d for d in candidats if d >= des_de]
        candidats = posteriors or candidats
    if not candidats:
        candidats = [d for d in recompte if d.weekday() < 5 and recompte[d] > 0]
    if not candidats:
        raise ValueError("El feed no té cap dia feiner amb servei")
    maxim = max(recompte[d] for d in candidats)
    # El primer dels dimecres amb el màxim de serveis: un dimecres normal, no un dia de servei reduït.
    return min(d for d in candidats if recompte[d] >= 0.95 * maxim)


def llegeix(cami, id_feed, festius=(), des_de=None, filtre_ruta=None, dies_alternatius=182):
    """Llegeix un GTFS i compta les expedicions del dia de referència.

    Si una parada no té servei el dia de referència (obres, talls), compta les del dimecres amb més servei dels
    `dies_alternatius` següents (`expedicions_alternatives`), perquè el node existeixi igualment.
    `filtre_ruta(ruta)` permet quedar-se només amb algunes línies (per exemple, les de Catalunya).
    """
    feed = Feed(id_feed)
    with zipfile.ZipFile(cami) as z:
        for fila in _files(z, "agency.txt"):
            feed.agencies[fila.get("agency_id", "")] = fila.get("agency_name", "")
        for fila in _files(z, "stops.txt"):
            try:
                lat, lon = float(fila["stop_lat"]), float(fila["stop_lon"])
            except (KeyError, ValueError):
                continue
            feed.parades[fila["stop_id"]] = Parada(
                id=fila["stop_id"],
                nom=fila.get("stop_name", ""),
                lat=lat,
                lon=lon,
                tipus_ubicacio=int(fila.get("location_type") or 0),
                pare=fila.get("parent_station", ""),
                codi=fila.get("stop_code", ""),
            )
        for fila in _files(z, "routes.txt"):
            ruta = Ruta(
                id=fila["route_id"],
                nom_curt=fila.get("route_short_name", ""),
                nom_llarg=fila.get("route_long_name", ""),
                tipus=tipus_ruta(fila.get("route_type")),
                agencia=fila.get("agency_id", ""),
                color=fila.get("route_color", ""),
                color_text=fila.get("route_text_color", ""),
            )
            if filtre_ruta is None or filtre_ruta(ruta):
                feed.rutes[ruta.id] = ruta

        calendari = Calendari(z)
        recompte = calendari.recompte()
        feed.dia_referencia = tria_dia_referencia(recompte, festius, des_de)
        actius = calendari.actius(feed.dia_referencia)
        dimecres = [d for d in recompte if d.weekday() == 2 and d not in festius and recompte[d] > 0
                    and feed.dia_referencia <= d <= feed.dia_referencia + dt.timedelta(days=dies_alternatius)]
        actius_dimecres = [calendari.actius(d) for d in sorted(dimecres)]

        multiplicador = Counter()
        for fila in _files(z, "frequencies.txt"):
            inici, fi = _segons(fila["start_time"]), _segons(fila["end_time"])
            interval = int(fila["headway_secs"] or 0)
            if interval > 0:
                multiplicador[fila["trip_id"]] += max(1, (fi - inici) // interval)

        viatges = {}
        for fila in _files(z, "trips.txt"):
            if fila["route_id"] in feed.rutes:
                viatges[fila["trip_id"]] = (fila["route_id"], fila["service_id"])
        for viatge, (ruta, servei) in viatges.items():
            if servei in actius:
                feed.expedicions_ruta[ruta] += multiplicador.get(viatge, 1)

        per_servei = defaultdict(Counter)
        for fila in _files(z, "stop_times.txt"):
            viatge = viatges.get(fila["trip_id"])
            if viatge is None or not fila.get("stop_id"):
                continue
            # Les parades on no es pot pujar ni baixar no compten.
            if fila.get("pickup_type") == "1" and fila.get("drop_off_type") == "1":
                continue
            per_servei[(fila["stop_id"], viatge[0])][viatge[1]] += multiplicador.get(fila["trip_id"], 1)

        ruta_alternativa = defaultdict(int)
        for clau, serveis in per_servei.items():
            avui = sum(n for s, n in serveis.items() if s in actius)
            if avui:
                feed.expedicions_parada_ruta[clau] = avui
                continue
            alternativa = max((sum(n for s, n in serveis.items() if s in a) for a in actius_dimecres), default=0)
            if alternativa:
                feed.expedicions_alternatives[clau] = alternativa
                ruta_alternativa[clau[1]] = max(ruta_alternativa[clau[1]], alternativa)
        for ruta, n in ruta_alternativa.items():
            if not feed.expedicions_ruta.get(ruta):
                feed.expedicions_ruta[ruta] = n
    return feed


def _segons(hora):
    h, m, s = (int(x) for x in hora.split(":"))
    return h * 3600 + m * 60 + s
