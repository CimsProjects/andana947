"""Normalització de noms de parades i estacions per poder-los comparar entre fonts."""

import re
import unicodedata

# Abreviatures que fan servir les fonts (TMB, FGC, Renfe) i la forma completa.
ABREVIATURES = {
    "AV": "AVINGUDA",
    "AVDA": "AVINGUDA",
    "AVDA.": "AVINGUDA",
    "RBLA": "RAMBLA",
    "PL": "PLACA",
    "PZA": "PLACA",
    "PG": "PASSEIG",
    "PS": "PASSEIG",
    "C": "CARRER",
    "CTRA": "CARRETERA",
    "STA": "SANTA",
    "ST": "SANT",
    "PTA": "PORTA",
    "HOSP": "HOSPITAL",
    "UNIV": "UNIVERSITAT",
    "SGDA": "SAGRADA",
}
PARAULES_BUIDES = {"DE", "DEL", "DELS", "LA", "LES", "EL", "ELS", "L", "D", "I", "Y", "ESTACIO", "ESTACION"}


def sense_accents(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text) if not unicodedata.combining(c))


def normalitza(nom):
    """Clau de comparació: majúscules, sense accents ni puntuació, abreviatures desplegades."""
    text = re.sub(r"L[·•‧.]L", "LL", sense_accents(nom).upper())
    text = re.sub(r"[^A-Z0-9]+", " ", text)
    paraules = [ABREVIATURES.get(p, p) for p in text.split()]
    return " ".join(p for p in paraules if p not in PARAULES_BUIDES)


def slug(nom):
    return re.sub(r"[^a-z0-9]+", "-", sense_accents(nom).lower()).strip("-")
