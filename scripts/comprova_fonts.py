#!/usr/bin/env python3
"""Comprova que totes les fonts de dades/fonts.json responen i n'escriu un informe.

    python3 scripts/comprova_fonts.py [--sortida dades/generades/informe_fonts.txt]

Per a cada font fa una petició i n'anota l'estat HTTP, el tipus i la mida. Si és un GTFS,
també n'enumera els fitxers. No desa les descàrregues: per això hi ha baixa_fonts.py.
"""

import argparse
import datetime as dt
import io
import pathlib
import sys
import urllib.error
import urllib.request
import zipfile

ARREL = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from andana import fonts  # noqa: E402


def comprova(font, maxim_bytes=400 * 1024 * 1024):
    url = fonts.url_de(font)
    peticio = urllib.request.Request(url, headers={"User-Agent": fonts.AGENT})
    try:
        with urllib.request.urlopen(peticio, timeout=180) as resposta:
            cos = resposta.read(maxim_bytes)
            linia = f"{resposta.status} {resposta.headers.get('Content-Type', '?')} {len(cos)} bytes"
    except urllib.error.HTTPError as error:
        return f"ERROR HTTP {error.code}"
    except Exception as error:  # noqa: BLE001 (és un informe: qualsevol error s'hi anota)
        return f"ERROR {type(error).__name__}: {error}"
    if cos[:2] == b"PK":
        try:
            with zipfile.ZipFile(io.BytesIO(cos)) as z:
                noms = sorted(z.namelist())
                linia += "\n    fitxers: " + ", ".join(noms[:30])
                for nom in noms:
                    if nom.endswith(".txt") or nom.endswith(".csv"):
                        with z.open(nom) as f:
                            capcalera = f.readline().decode("utf-8-sig", "replace").strip()
                            files = 1 + sum(1 for _ in f)
                        linia += f"\n    {nom}: {files} línies; {capcalera[:300]}"
        except zipfile.BadZipFile:
            linia += " (zip no vàlid)"
    else:
        text = cos[:1500].decode("utf-8", "replace").replace("\r", "")
        linia += "\n    " + "\n    ".join(text.splitlines()[:6])
    return linia


def main():
    arguments = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    arguments.add_argument("--sortida", type=pathlib.Path)
    args = arguments.parse_args()
    linies = [f"Comprovació de les fonts, {dt.datetime.now(dt.timezone.utc):%Y-%m-%d %H:%M} UTC", ""]
    errors = 0
    for font in fonts.llegeix()["fonts"]:
        resultat = comprova(font)
        if resultat.startswith("ERROR"):
            errors += 1
        linies.append(f"{font['id']}: {resultat}")
        print(linies[-1], flush=True)
    linies.append("")
    linies.append(f"Fonts amb error: {errors}")
    if args.sortida:
        args.sortida.parent.mkdir(parents=True, exist_ok=True)
        args.sortida.write_text("\n".join(linies) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
