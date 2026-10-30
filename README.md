# Andana 947

Joc Android de geolocalització i gestió econòmica ambientat al transport públic de Catalunya. Comences com un petit operador de mobilitat local: camines fins a una parada o estació real, hi fas *check-in* amb el GPS i n'aconsegueixes la concessió. Cada concessió genera ingressos segons els passatgers reals que hi passen. Amb els beneficis compres autobusos i trens, obres línies i t'enfrontes al caos de cada dia: robatoris de coure, temporals, el Mobile o la Marató.

Del bus del barri fins al primer tren regional, per tots els 947 municipis.

## Estat

En desenvolupament. L'abast i el full de ruta són a [`docs/abast.md`](docs/abast.md), i les regles del joc al GDD, [`docs/gdd.md`](docs/gdd.md).

| Fase | Estat |
|---|---|
| 0. Configuració | Fet |
| 1. GDD | Fet ([`docs/gdd.md`](docs/gdd.md)) |
| 2. Pipeline de dades | Fet ([`docs/dades.md`](docs/dades.md)): parades, línies i afluència reals a PostGIS, configuració del joc i mòdul `joc/` |
| 3. Esquelet de l'app | Fet ([`app/LLEGEIX-ME.md`](app/LLEGEIX-ME.md)): compte, mapa amb la posició, tema clar i fosc, navegació i CI |
| 4. MVP jugable | Fet: parades a prop, check-in geolocalitzat, conquesta de concessions, accions d'estacions, imperi i ingrés diari |

## Estructura

| Carpeta | Contingut |
|---|---|
| `app/` | L'app Android (Compose, Hilt, DataStore, MapLibre i Supabase). Com es compila i què hi ha: [`app/LLEGEIX-ME.md`](app/LLEGEIX-ME.md). |
| `joc/` | Regles i matemàtiques del joc en Kotlin pur, amb tests: economia, intermodalitat, cohesió, concessions, prestigi, validació del *check-in* i la simulació del primer mes. L'app l'inclou com a build composta, com a [municipis](https://github.com/CimsProjects/municipis). |
| `dades/` | `configuracio_joc.json` (l'únic lloc amb els valors del joc), les fonts (`fonts.json`, `totals_control.json`), el territori i, a `generades/`, les parades, línies i afluència que surten del pipeline. |
| `scripts/` | Pipeline de dades en Python: baixa les fonts, genera els nodes i l'afluència, i els carrega a PostGIS ([`docs/dades.md`](docs/dades.md)). |
| `supabase/` | Migracions de la base de dades (esquema, RLS i funcions) i els seus tests. |
| `docs/` | Abast, GDD, dades i guies (`supabase.md`). |

## Compilar i provar

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew -p joc test                                   # regles del joc
cd scripts && python3 -m unittest discover -s test -t . # pipeline de dades
```

Per estalviar minuts d'Actions, la CI només corre a les PR i als *push* a `main`, i només si hi ha canvis que l'afecten: `ci.yml` compila l'app i passa els tests de `joc/` (canvis a `app/`, `joc/`, `dades/` o Gradle), i `supabase.yml` prova l'esquema en un Supabase local (canvis a `supabase/`). El workflow **Dades** (`dades.yml`) passa els tests dels scripts i regenera les dades; es llança a mà, des d'*Actions → Dades → Run workflow*.

## Fonts de dades

Totes les dades del joc són reals i surten de fonts obertes; quan no n'hi ha per a una parada, s'estimen amb un model documentat i l'app ho diu. La llista completa, amb les llicències, és a [`dades/fonts.json`](dades/fonts.json) i [`docs/dades.md`](docs/dades.md):

- **Horaris i parades (GTFS):** TMB, FGC, Renfe (Rodalies i mitjana distància), TRAM, AMB i autobusos interurbans de la Generalitat.
- **Afluència:** TMB (portal de transparència), FGC i Generalitat (dades obertes), Renfe Data i Idescat.
- **Territori:** IGN (límits municipals), ICGC (comarques) i INE (seccions censals i Cens 2021).
- **Mapa de l'app:** [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL), amb les teselles d'[OpenFreeMap](https://openfreemap.org).
