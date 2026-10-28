# Dades del joc

> Fase 2: el pipeline de dades. Com es generen les parades, les línies i l'afluència, d'on surten i com es carreguen a Supabase.
> Última actualització: 28/10/2026

Totes les dades surten de fonts obertes (`dades/fonts.json`). Quan no hi ha dada per a una parada, s'estima amb els models del GDD (seccions 2.2 i 2.3) i es marca com a *estimat*. Les xifres que fan servir els models i no es poden baixar automàticament (totals anuals publicats) són a `dades/totals_control.json`, cadascuna amb la font.

## Com es generen

```bash
pip install -r scripts/requirements.txt
python3 scripts/baixa_fonts.py            # baixa les fonts a font/ (no es puja)
python3 scripts/genera_dades.py           # genera dades/generades/ i l'informe
python3 scripts/prepara_simulacio.py      # escenari de la simulació del primer mes
./gradlew -p joc simulacio                # la simulació
```

Normalment no cal fer-ho en local: el workflow **Dades** (`.github/workflows/dades.yml`) ho fa i desa el resultat a la mateixa branca. Per estalviar minuts d'Actions, només es llança a mà: *Actions → Dades → Run workflow*, triant la branca. També hi carrega les dades a un Supabase local per comprovar-les (`informe_carrega.md`).

El GTFS de TMB es baixa de l'API oficial amb les claus `TMB_APP_ID` i `TMB_APP_KEY` (secrets del repositori); sense claus, de la còpia que en desa la Mobility Database. Les claus no es desen enlloc.

Els tests: `cd scripts && python3 -m unittest discover -s test -t .`

## Què es genera (`dades/generades/`)

| Fitxer | Contingut |
|---|---|
| `nodes.csv` | Parades i estacions: posició, municipi, comarca, línies, expedicions d'un feiner, població a 400 m (bus i TRAM), **viatgers d'un feiner**, `font_afluencia` (`real` o `estimat`), mètode i any de la dada |
| `linies.csv` | Línies de cada operador, amb el color, les expedicions i els viatgers d'un feiner |
| `linia_nodes.csv` | Les parades de cada línia, amb les expedicions que hi paren |
| `municipis.csv` | Població (Cens 2021), superfície i densitat de cada municipi (per al Bo de Cohesió, GDD 4.6) |
| `informe_dades.md` | Recomptes, aparellaments entre fonts, comprovacions i calibratges |
| `informe_fonts.txt` | Estat de cada font l'últim cop que es va baixar |
| `informe_carrega.md` | La càrrega de prova a PostGIS |
| `escenari_primer_mes.json`, `simulacio_primer_mes.md` | La simulació del primer mes (GDD 8) |

`dades/territori/` són els límits municipals (IGN, desembre del 2024) i les comarques, els mateixos que fa servir [municipis](https://github.com/CimsProjects/municipis) (`dades/municipis_catalunya.geojson` i `dades/comarques.json`), copiats tal qual.

El contorn de Catalunya que fa servir el mapa de l'app (`app/src/main/assets/mapa/catalunya.geojson`) surt d'aquests límits municipals amb `python3 scripts/genera_contorn.py`: la unió dels municipis simplificada a uns 100 m, i la capsa que limita la càmera.

## Nodes (GDD 3)

| Mode | GTFS | Un node és… |
|---|---|---|
| Metro | TMB | L'estació pare del GTFS (totes les línies de TMB en un node). Les estacions pare amb el mateix nom a menys de 300 m s'ajunten |
| FGC | FGC | L'estació pare (sense funiculars ni cremalleres) |
| Rodalies i regionals | Renfe Cercanías i Renfe mitjana distància | El codi d'estació d'Adif, comú als dos feeds. Una estació amb Rodalies i regionals és de Rodalies. Només les parades dins de Catalunya; sense alta velocitat ni llarga distància |
| TRAM | Trambaix i Trambesòs | L'estació pare |
| Bus | TMB, AMB i interurbans de la Generalitat | Cada parada física; les que tenen el mateix nom a menys de 30 m (els dos sentits, o la mateixa parada a dos feeds) s'ajunten |

Les expedicions són les d'un **dimecres** feiner (el primer a partir del dia de la generació amb el servei normal). Si una parada no té servei aquell dia (obres, talls de Rodalies), es compten les del dimecres amb més servei dels sis mesos següents, perquè el node existeixi igualment; l'informe diu quantes.

Estacions d'operadors diferents al mateix lloc són nodes diferents (Catalunya de TMB, d'FGC i de Renfe), com diu el GDD.

## Afluència

| Xarxa | Font | Mètode | Etiqueta |
|---|---|---|---|
| Metro | TMB, *Resum de dades mensuals i diàries de viatgers FMB 2025* | Mitjana dels feiners de l'any per estació i línia (suma de les línies de l'estació) | **real** |
| FGC | FGC, *Demanda mensual per estació* (2024); per a Lleida–la Pobla, la sèrie de la Generalitat | Total de l'any → feiner (GDD 4.4) | **real** |
| Rodalies | Renfe, viatgers pujats per estació en un feiner del 2018 | GDD 2.2: × factor de Rodalies 2024/2018 (Idescat) | estimat |
| Regionals | Idescat: 11,3 milions de viatgers el 2024 | GDD 2.2: repartits per expedicions × població del municipi^0,5 | estimat |
| Bus TMB | TMB, *Resum de dades mensuals i diàries de viatgers TB 2025*: mitjana dels feiners de cada línia | GDD 2.3: el total de la línia es reparteix entre les parades | real per línia, estimat per parada |
| Bus AMB | — | GDD 2.3: expedicions de la línia × ocupació mitjana d'una expedició de TMB | estimat |
| Bus interurbà | Idescat: 67,3 milions de viatgers el 2018 (transport regular per carretera) | GDD 2.3: expedicions × ocupació mitjana (viatgers ÷ expedicions de la xarxa) | estimat |
| TRAM | Idescat: 35,0 milions de viatgers el 2024 | GDD 2.3, amb tota la xarxa com si fos una línia | estimat |

**Població a 400 m** (GDD 2.3): seccions censals de l'INE (cartografia vigent) amb la població del Cens 2021, repartida uniformement dins de cada secció. Les seccions creades després del 2021 no tenen població al cens i s'estimen amb la densitat de la resta del municipi.

### Limitacions conegudes

- **Factor de Rodalies per línia.** El GDD 2.2 el vol línia per línia (sèrie de l'ATM / OMC). El servidor de l'OMC no respon a les màquines de GitHub i no s'ha trobat una altra sèrie oberta per línia dels anys recents, així que de moment hi ha un sol factor per a tota la xarxa (Idescat, 2024 / 2018). Quan hi hagi la sèrie, n'hi ha prou d'omplir `factors` a `genera_dades.py`.
- **Interurbans: total del 2018.** És el darrer total publicat que s'ha pogut verificar. S'aplica a les expedicions del GTFS actual.
- **Bus urbà fora de l'AMB** (Girona, Lleida, Tarragona, Terrassa, Sabadell, Mataró, Reus…): els GTFS són al Punto de Acceso Nacional, que demana registre. Les línies urbanes que surten al GTFS de la Generalitat sí que hi són.
- **TRAM:** no hi ha dada oberta per línia; el total de la xarxa es reparteix com si fos una sola línia.

## Supabase

L'esquema és a `supabase/migrations/20261028180000_dades_xarxa.sql`: taules `municipis`, `linies`, `nodes` (amb la posició com a `geography` i un índex GiST), `linia_nodes` i `fonts_dades`, i la funció `nodes_a_prop(lat, lon, radi_metres, maxim)` per a la pantalla «A prop». Tothom les pot llegir; ningú no les pot modificar des de l'app (RLS). Els tests són a `supabase/tests/dades.test.sql`.

Per carregar les dades:

```bash
npx supabase start                                    # local, amb Docker
DATABASE_URL=postgresql://postgres:postgres@127.0.0.1:54322/postgres python3 scripts/carrega_postgis.py
```

Al projecte de producció: la cadena de connexió és a *Project Settings → Database* i porta la contrasenya de la base de dades, que no va mai al repositori ni al xat (`docs/supabase.md`). La càrrega és una sola transacció i esborra els nodes i les línies que ja no hi són.
