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


def serveis_actius(z, dia):
    """Serveis que funcionen el dia `dia` segons calendar.txt i calendar_dates.txt."""
    actius = set()
    for fila in _files(z, "calendar.txt"):
        if _data(fila["start_date"]) <= dia <= _data(fila["end_date"]) and fila.get(DIES[dia.weekday()]) == "1":
            actius.add(fila["service_id"])
    for fila in _files(z, "calendar_dates.txt"):
        if _data(fila["date"]) == dia:
            if fila["exception_type"] == "1":
                actius.add(fila["service_id"])
            elif fila["exception_type"] == "2":
                actius.discard(fila["service_id"])
    return actius


def dies_amb_servei(z):
    """Per a cada data del feed, quants serveis hi ha actius (per triar el dia de referència)."""
    recompte = Counter()
    calendari = list(_files(z, "calendar.txt"))
    excepcions = list(_files(z, "calendar_dates.txt"))
    dates = set()
    for fila in calendari:
        inici, fi = _data(fila["start_date"]), _data(fila["end_date"])
        # Només mirem els 400 primers dies de cada servei: n'hi ha prou per trobar un dimecres normal.
        for i in range(min((fi - inici).days + 1, 400)):
            dates.add(inici + dt.timedelta(days=i))
    for fila in excepcions:
        dates.add(_data(fila["date"]))
    for dia in dates:
        recompte[dia] = 0
    for fila in calendari:
        inici, fi = _data(fila["start_date"]), _data(fila["end_date"])
        for dia in dates:
            if inici <= dia <= fi and fila.get(DIES[dia.weekday()]) == "1":
                recompte[dia] += 1
    for fila in excepcions:
        dia = _data(fila["date"])
        recompte[dia] += 1 if fila["exception_type"] == "1" else -1
    return recompte


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


def llegeix(cami, id_feed, festius=(), des_de=None, filtre_ruta=None):
    """Llegeix un GTFS i compta les expedicions del dia de referència.

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

        feed.dia_referencia = tria_dia_referencia(dies_amb_servei(z), festius, des_de)
        actius = serveis_actius(z, feed.dia_referencia)

        multiplicador = Counter()
        for fila in _files(z, "frequencies.txt"):
            inici, fi = _segons(fila["start_time"]), _segons(fila["end_time"])
            interval = int(fila["headway_secs"] or 0)
            if interval > 0:
                multiplicador[fila["trip_id"]] += max(1, (fi - inici) // interval)

        ruta_de = {}
        for fila in _files(z, "trips.txt"):
            if fila["service_id"] in actius and fila["route_id"] in feed.rutes:
                ruta_de[fila["trip_id"]] = fila["route_id"]
        for viatge, ruta in ruta_de.items():
            feed.expedicions_ruta[ruta] += multiplicador.get(viatge, 1)

        for fila in _files(z, "stop_times.txt"):
            ruta = ruta_de.get(fila["trip_id"])
            if ruta is None or not fila.get("stop_id"):
                continue
            # Les parades on no es pot pujar ni baixar no compten.
            if fila.get("pickup_type") == "1" and fila.get("drop_off_type") == "1":
                continue
            feed.expedicions_parada_ruta[(fila["stop_id"], ruta)] += multiplicador.get(fila["trip_id"], 1)
    return feed


def _segons(hora):
    h, m, s = (int(x) for x in hora.split(":"))
    return h * 3600 + m * 60 + s
