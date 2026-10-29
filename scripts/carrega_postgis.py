#!/usr/bin/env python3
"""Carrega les dades generades (dades/generades/*.csv) a la base de dades de Supabase (PostGIS).

    DATABASE_URL=postgresql://postgres:postgres@127.0.0.1:54322/postgres python3 scripts/carrega_postgis.py

Fa servir `psql` (cal tenir-lo instal·lat) i ho fa tot en una sola transacció: si alguna cosa falla, la base
de dades queda com estava. Els nodes i les línies que ja no surten a les dades noves s'esborren.

La connexió amb el projecte de producció necessita la contrasenya de la base de dades: no la posis mai al
repositori ni al xat (docs/supabase.md). Per provar-ho, fes servir el Supabase local (`npx supabase start`).
"""

import argparse
import csv
import json
import os
import pathlib
import subprocess
import sys
import tempfile

ARREL = pathlib.Path(__file__).resolve().parent.parent
GENERADES = ARREL / "dades" / "generades"
FITXERS = ["municipis.csv", "linies.csv", "nodes.csv", "linia_nodes.csv", "trams.csv"]

SQL = r"""
\set ON_ERROR_STOP on
begin;

create temp table nou_fonts (id text, nom text, operador text, url text, llicencia text, web text) on commit drop;
create temp table nou_municipis (codi text, nom text, comarca text, poblacio_2021 text, superficie_km2 text,
    densitat text) on commit drop;
create temp table nou_linies (id text, operador text, mode text, codi text, nom text, color text, color_text text,
    expedicions_feiner text, viatgers_feiner text, font_afluencia text) on commit drop;
create temp table nou_nodes (id text, nom text, mode text, operador text, lat text, lon text, municipi text,
    comarca text, linies text, expedicions_feiner text, poblacio_400m text, viatgers_feiner text,
    font_afluencia text, metode text, any_dada text) on commit drop;
create temp table nou_linia_nodes (linia_id text, node_id text, expedicions_feiner text) on commit drop;
create temp table nou_trams (id text, operador text, mode text, codi text, nom text, color text, parades text,
    arribades_feiner text, mida text, preu text, linies text) on commit drop;

\copy nou_fonts from {fonts} with (format csv, header true)
\copy nou_municipis from {municipis} with (format csv, header true)
\copy nou_linies from {linies} with (format csv, header true)
\copy nou_nodes from {nodes} with (format csv, header true)
\copy nou_linia_nodes from {linia_nodes} with (format csv, header true)
\copy nou_trams from {trams} with (format csv, header true)

insert into public.fonts_dades (id, nom, operador, url, llicencia, web)
select id, nom, nullif(operador, ''), url, nullif(llicencia, ''), nullif(web, '') from nou_fonts
on conflict (id) do update set nom = excluded.nom, operador = excluded.operador, url = excluded.url,
    llicencia = excluded.llicencia, web = excluded.web, carregat_el = now();

insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat)
select codi, nom, comarca, poblacio_2021::integer, superficie_km2::numeric, densitat::numeric from nou_municipis
on conflict (codi) do update set nom = excluded.nom, comarca = excluded.comarca, poblacio = excluded.poblacio,
    superficie_km2 = excluded.superficie_km2, densitat = excluded.densitat;

insert into public.linies (id, operador, mode, codi, nom, color, color_text, expedicions_feiner, viatgers_feiner,
    font_afluencia)
select id, operador, mode::public.mode_transport, codi, nom,
       case when color ~ '^[0-9A-Fa-f]{6}$' then color end,
       case when color_text ~ '^[0-9A-Fa-f]{6}$' then color_text end,
       expedicions_feiner::integer, nullif(viatgers_feiner, '')::numeric,
       nullif(font_afluencia, '')::public.font_afluencia
from nou_linies
on conflict (id) do update set operador = excluded.operador, mode = excluded.mode, codi = excluded.codi,
    nom = excluded.nom, color = excluded.color, color_text = excluded.color_text,
    expedicions_feiner = excluded.expedicions_feiner, viatgers_feiner = excluded.viatgers_feiner,
    font_afluencia = excluded.font_afluencia;

insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner,
    poblacio_400m, viatgers_feiner, font_afluencia, metode, any_dada, versio_model, actualitzat_el)
select id, nom, mode::public.mode_transport, operador,
       extensions.st_setsrid(extensions.st_makepoint(lon::float8, lat::float8), 4326)::extensions.geography,
       nullif(municipi, ''), nullif(comarca, ''),
       case when linies = '' then '{}'::text[] else string_to_array(linies, ';') end,
       expedicions_feiner::integer, nullif(poblacio_400m, '')::integer, viatgers_feiner::numeric,
       font_afluencia::public.font_afluencia, metode, nullif(any_dada, '')::smallint, :versio, now()
from nou_nodes
on conflict (id) do update set nom = excluded.nom, mode = excluded.mode, operador = excluded.operador,
    posicio = excluded.posicio, municipi = excluded.municipi, comarca = excluded.comarca, linies = excluded.linies,
    expedicions_feiner = excluded.expedicions_feiner, poblacio_400m = excluded.poblacio_400m,
    viatgers_feiner = excluded.viatgers_feiner, font_afluencia = excluded.font_afluencia,
    metode = excluded.metode, any_dada = excluded.any_dada, versio_model = excluded.versio_model,
    actualitzat_el = now();

delete from public.linia_nodes;
insert into public.linia_nodes (linia_id, node_id, expedicions_feiner)
select linia_id, node_id, expedicions_feiner::integer from nou_linia_nodes;

-- La configuració del joc (dades/configuracio_joc.json), que llegeixen les funcions dels trams.
insert into public.configuracio_joc (id, valors) values (1, :'config'::jsonb)
on conflict (id) do update set valors = excluded.valors, carregat_el = now();

-- Els trams (GDD 4.0). Si un tram desapareix de les dades, també desapareix dels jugadors que el tenien.
insert into public.trams (id, operador, mode, codi, nom, color, parades, arribades_feiner, mida, preu, linies)
select id, operador, mode::public.mode_transport, codi, nom,
       case when color ~ '^[0-9A-Fa-f]{6}$' then color end,
       parades::integer, arribades_feiner::integer, mida::public.mida_tram, nullif(preu, '')::integer,
       string_to_array(linies, ';')
from nou_trams
on conflict (id) do update set operador = excluded.operador, mode = excluded.mode, codi = excluded.codi,
    nom = excluded.nom, color = excluded.color, parades = excluded.parades,
    arribades_feiner = excluded.arribades_feiner, mida = excluded.mida, preu = excluded.preu, linies = excluded.linies;
delete from public.trams where id not in (select id from nou_trams);

delete from public.nodes where id not in (select id from nou_nodes);
delete from public.linies where id not in (select id from nou_linies);

commit;

select 'municipis' as taula, count(*) as files from public.municipis
union all select 'linies', count(*) from public.linies
union all select 'nodes', count(*) from public.nodes
union all select 'linia_nodes', count(*) from public.linia_nodes
union all select 'fonts_dades', count(*) from public.fonts_dades
union all select 'trams', count(*) from public.trams;
"""


def main():
    arguments = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    arguments.add_argument("--url", default=os.environ.get("DATABASE_URL"),
                           help="cadena de connexió (per defecte, la variable DATABASE_URL)")
    args = arguments.parse_args()
    if not args.url:
        print("Falta la connexió: posa-la a DATABASE_URL o passa --url.", file=sys.stderr)
        return 2
    falten = [str(c.relative_to(ARREL)) for c in (GENERADES / f for f in FITXERS) if not c.is_file()]
    if falten:
        print("Falten fitxers de dades/generades: " + ", ".join(falten) + ". Genera'ls amb "
              "python3 scripts/genera_dades.py.", file=sys.stderr)
        return 1
    config = json.loads((ARREL / "dades" / "configuracio_joc.json").read_text(encoding="utf-8"))
    fonts = json.loads((ARREL / "dades" / "fonts.json").read_text(encoding="utf-8"))["fonts"]
    with tempfile.TemporaryDirectory() as temporal:
        cami_fonts = pathlib.Path(temporal) / "fonts.csv"
        with open(cami_fonts, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(["id", "nom", "operador", "url", "llicencia", "web"])
            for font in fonts:
                w.writerow([font["id"], font["nom"], font.get("operador", ""), font["url"], font.get("llicencia", ""),
                            font.get("web", "")])
        variables = {
            "fonts": cami_fonts,
            "municipis": GENERADES / "municipis.csv",
            "linies": GENERADES / "linies.csv",
            "nodes": GENERADES / "nodes.csv",
            "linia_nodes": GENERADES / "linia_nodes.csv",
            "trams": GENERADES / "trams.csv",
            "versio": config["modelAfluencia"]["versio"],
        }
        for nom in ("fonts", "municipis", "linies", "nodes", "linia_nodes", "trams"):
            if not pathlib.Path(variables[nom]).is_file():
                print(f"No trobo el fitxer de {nom}: {variables[nom]}", file=sys.stderr)
                return 1
        # \copy no substitueix variables de psql: els camins van directament al text, entre cometes.
        sql = SQL
        for nom, valor in variables.items():
            if nom != "versio":
                sql = sql.replace("{" + nom + "}", "'" + str(valor).replace("'", "''") + "'")
        ordre = ["psql", args.url, "--no-psqlrc", "-v", f"versio={variables['versio']}",
                 "-v", "config=" + json.dumps(config, ensure_ascii=False, separators=(",", ":"))]
        return subprocess.run(ordre, input=sql, text=True).returncode


if __name__ == "__main__":
    sys.exit(main())
