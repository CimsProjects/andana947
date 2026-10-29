# Vehicles al mapa

> Els trens, metros i tramvies que circulen per Catalunya, dibuixats al mapa i movent-se per la via.
> Última actualització: 29/10/2026 (fase 1 dels vehicles: els trens)

## Què es veu

- Cada tren és un **bloc del color de la línia amb vora de tinta**, que ocupa el seu tros de via i en segueix les corbes. Des de dalt es veu la planta del tren; inclinant el mapa amb dos dits, el bloc en 3D.
- **La mida s'adapta al zoom** perquè el tren es vegi sempre igual a la pantalla (uns 28 píxels de llarg el metro i FGC, 34 Rodalies i regionals, 20 el TRAM). De molt a prop no es fa mai més petit que una mida mínima en metres (100, 130 i 40 m). No hi ha punts: a tots els zooms és un bloc.
- Els trens amb **posició real** són opacs; els que tenen **posició estimada** segons l'horari són una mica transparents (norma de les dades reals: el que és estimat, es marca).

Les mides són de dibuix, no d'un model de tren concret (`CapaVehicles.kt`).

Només es mostra la part dels recorreguts que és dins de Catalunya: els trens que venen de fora o hi van apareixen i desapareixen a la frontera.

## Enfocament híbrid

Decidit el 29/10/2026: posició real on n'hi ha, i estimada segons l'horari a la resta. Primer els trens (metro, FGC, Rodalies, regionals i TRAM) i després els busos.

| Xarxa | Posició | Font |
|---|---|---|
| Rodalies de Catalunya (R1–R8, R11–R17, RG1, RT, RL) | **Real** | GTFS-Realtime de Renfe Cercanías (`vehicle_positions.pb`) |
| FGC | **Real** | Geotren, dades obertes d'FGC (*posicionament-dels-trens*) |
| Metro de TMB | Estimada (horari) | GTFS de TMB |
| TRAM | Estimada (horari) | GTFS del Trambaix i del Trambesòs |
| Regionals de mitjana distància de Renfe | Estimada (horari) | GTFS de Renfe AV/LD/MD |

Si una font real falla, les seves línies tornen a l'horari automàticament. Sense servidor configurat (app/LLEGEIX-ME.md), tot va segons l'horari.

### Pendent

- Comprovar amb una crida real els camps del Geotren d'FGC (`lin`, `geo_point_2d`) i el codi de línia dels trens de Renfe. La funció ho llegeix amb alternatives i, si no troba cap tren llegible, torna a l'horari.
- Metro: TMB té temps d'arribada en directe (iMetro, amb clau). Es podria fer servir per ajustar els trens estimats amb els retards.
- Fase 2: els busos (TMB, AMB, interurbans i urbans). Molts més vehicles: caldrà calcular-los al servidor o per zones.
- Els festius entre setmana fan l'horari de feiner.

## Com funciona

```
GTFS (horaris i traçats) ──► scripts/genera_circulacions.py ──► dades/generades/app/circulacions.json ──► app (assets)
Renfe GTFS-RT + FGC Geotren ──► supabase/functions/vehicles (cada 15 s, compartit) ──► app (cada 20 s)
                                                                       │
                     joc/…/circulacio: on és cada tren ara ◄───────────┘
```

### Horaris (`scripts/genera_circulacions.py`)

Per a les línies de tren de `dades/generades/linies.csv` (les mateixes que el joc) llegeix el GTFS de cada operador i desa, per a tres **dies tipus** (un dimecres feiner, un dissabte i un diumenge amb servei normal):

- **Traçats**: la polilínia de la via (`shapes.txt`), simplificada a 4 m i codificada com les de Google. Si un recorregut no té traçat, o el traçat no passa per les parades, la recta entre parades (ho diu l'informe).
- **Patrons**: el traçat, a quants metres hi ha cada parada i quants segons després de sortir hi arriba i en surt. Les expedicions amb el mateix horari relatiu en comparteixen un.
- **Expedicions**: el patró i l'hora de sortida. Les de `frequencies.txt` s'expandeixen.
- **Retall a Catalunya**: cada recorregut es queda des de la primera parada de Catalunya fins a l'última (límits municipals de l'IGN, amb un marge de 200 m). Si ve de fora o hi va, la via s'allarga fins a la frontera amb una parada virtual a l'hora interpolada.

L'informe és a `dades/generades/informe_circulacions.md`. El workflow **Dades** el genera amb la resta de dades.

### On és cada tren (`joc/src/main/kotlin/cat/andana947/joc/circulacio/`)

Kotlin pur, amb tests (`CirculacionsTest`):

- `Circulacions`: els trens en circulació a una hora. Entre dues parades, el tren avança per la via accelerant i frenant (corba *smoothstep*); a la parada s'espera fins a l'hora de sortida. Les expedicions d'ahir que passen de mitjanit continuen.
- `SeguimentVehiclesReals`: quan arriba una posició real nova, el tren hi va des d'on es veia durant l'interval, per sobre de la via de la seva línia si n'és a menys de 150 m, o en línia recta si no.
- `Flota`: els reals de les fonts que funcionen i, per a la resta de línies, els estimats.
- `Silueta`: la planta del bloc 3D sobre la via.

### Posicions reals (`supabase/functions/vehicles`)

Una Edge Function que consulta Renfe i FGC i en retorna els trens de Catalunya:

```json
{"generat": 1793000000000, "fonts": {"renfe": true, "fgc": true},
 "vehicles": [{"id": "25603", "font": "renfe", "linia": "R1", "lat": 41.5, "lon": 2.4}]}
```

- Són dades obertes i no hi ha cap acció econòmica: no demana sessió (`verify_jwt = false` a `supabase/config.toml`).
- La resposta es guarda 15 segons: tots els jugadors comparteixen una sola consulta a cada operador.
- Tests: `deno test supabase/functions/vehicles` (sense xarxa).
- Per publicar-la al projecte: `npx supabase functions deploy vehicles --project-ref tocvxpoyrvaabgpbfdxg` (docs/supabase.md).

### Al mapa (`app/…/mapa/`)

- `VehiclesViewModel` carrega els horaris i, mentre es mira el mapa, demana les posicions reals cada 20 segons.
- `VistaMapa` calcula els trens de la part visible fora del fil principal, amb la mida del zoom actual, i els dibuixa cada 150 ms de prop i cada 400 ms de lluny.
- `CapaVehicles` defineix les capes de MapLibre (blocs `fill-extrusion` i la vora) i els colors. Els colors massa clars (els regionals de Renfe són gairebé blancs al GTFS) es dibuixen amb la tinta.
