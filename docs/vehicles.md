# Vehicles al mapa

> Els trens, metros, tramvies i busos que circulen per Catalunya, dibuixats al mapa i movent-se pel seu recorregut.
> Última actualització: 29/10/2026 (fase 2 dels vehicles: els busos)

## Què es veu

- Cada tren és **un tren del color de la línia amb vora de tinta**: cotxes separats (5 el metro i Rodalies, 4 FGC i regionals, 5 el TRAM, articulat i gairebé sense espais), morro més estret als dos extrems i el vidre del davant fosc, que marca el sentit de la marxa. Ocupa el seu tros de via i en segueix les corbes cotxe a cotxe. Des de dalt es veu la planta del tren; inclinant el mapa amb dos dits, en 3D. Els cotxes i el morro són de dibuix: no reprodueixen cap model concret. Els vehicles de les línies del jugador tenen més detall: el sostre de cada cotxe més clar i una mica més alt, la franja fosca de les finestres als costats (es veu inclinant el mapa) i, als trens, la cabina dels dos extrems amb el vidre fosc. Els grisos de les altres línies són simples, perquè n'hi ha molts.
- **La mida s'adapta al zoom** perquè el tren es vegi sempre igual a la pantalla (uns 34 píxels de llarg el metro, 32 FGC, 40 Rodalies, 36 els regionals, 24 el TRAM i 18 els busos, que són molts més i no han de tapar els trens). De lluny es fan una mica més petits (fins a menys de la meitat amb tot Catalunya a la pantalla) perquè no tapin les línies, i de molt a prop no es fan mai més petits que una mida mínima en metres (100, 130, 40 i 25 m). No hi ha punts: a tots els zooms és un tren o un bus.
- Cada bus és **un sol cos** del color de la línia, amb el front gairebé pla i les cantonades arrodonides, i el vidre del davant fosc. Segueix el traçat de la línia pel carrer.
- **Es poden amagar els trens o els busos** amb els botons «Trens» i «Busos» de dalt del mapa (encès, ple de color; apagat, buit). L'app ho recorda entre sessions (DataStore, `PreferenciesVehicles`). Els vehicles amagats ni es calculen (`Flota.vehicles(…, tipus)`), i amb els busos amagats el mapa va molt més lleuger.
- Els trens amb **posició real** són opacs; els que tenen **posició estimada** segons l'horari són una mica transparents (norma de les dades reals: el que és estimat, es marca).

Les mides són de dibuix, no d'un model de tren concret (`CapaVehicles.kt`).

Només es mostra la part dels recorreguts que és dins de Catalunya: els trens i els busos que venen de fora o hi van apareixen i desapareixen a la frontera.

## Enfocament híbrid

Decidit el 29/10/2026: posició real on n'hi ha, i estimada segons l'horari a la resta. Primer els trens (metro, FGC, Rodalies, regionals i TRAM) i després els busos.

| Xarxa | Posició | Font |
|---|---|---|
| Rodalies de Catalunya (R1–R8, R11–R17, RG1, RT, RL) | **Real** | GTFS-Realtime de Renfe Cercanías (`vehicle_positions.pb`) |
| FGC | **Real** | Geotren, dades obertes d'FGC (*posicionament-dels-trens*) |
| Metro de TMB | Estimada (horari) | GTFS de TMB |
| TRAM | Estimada (horari) | GTFS del Trambaix i del Trambesòs |
| Regionals de mitjana distància de Renfe | Estimada (horari) | GTFS de Renfe AV/LD/MD |
| Bus de TMB | Estimada (horari) | GTFS de TMB |
| Bus metropolità de l'AMB | Estimada (horari) | GTFS de l'AMB |
| Bus interurbà de la Generalitat (Moventis, Sagalés…) | Estimada (horari) | GTFS dels autobusos interurbans |

Si una font real falla, les seves línies tornen a l'horari automàticament. Sense servidor configurat (app/LLEGEIX-ME.md), tot va segons l'horari.

### Pendent

- Comprovar amb una crida real els camps del Geotren d'FGC (`lin`, `geo_point_2d`) i el codi de línia dels trens de Renfe. La funció ho llegeix amb alternatives i, si no troba cap tren llegible, torna a l'horari.
- Metro: TMB té temps d'arribada en directe (iMetro, amb clau). Es podria fer servir per ajustar els trens estimats amb els retards.
- Busos urbans de fora de l'AMB (Girona, Lleida, Tarragona, Reus, Terrassa, Sabadell, Mataró…): tenen GTFS al punt d'accés nacional, però encara no són a `linies.csv`. Cal afegir-los primer a les dades del joc.
- Posició real dels busos: TMB (iBus) i l'AMB donen temps d'arribada a les parades, no posicions, i amb clau. De moment, tots els busos van segons l'horari.
- Els festius entre setmana fan l'horari de feiner.

## Com funciona

```
GTFS (horaris i traçats) ──► scripts/genera_circulacions.py ──► dades/generades/app/circulacions.json ──► app (assets)
Renfe GTFS-RT + FGC Geotren ──► supabase/functions/vehicles (cada 15 s, compartit) ──► app (cada 20 s)
                                                                       │
                     joc/…/circulacio: on és cada tren ara ◄───────────┘
```

### Horaris (`scripts/genera_circulacions.py`)

Per a les línies de tren i de bus de `dades/generades/linies.csv` (les mateixes que el joc) llegeix el GTFS de cada operador i desa, per a tres **dies tipus** (un dimecres feiner, un dissabte i un diumenge amb servei normal):

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
- `Silueta`: la planta del tren (cotxes, morro i vidre) sobre la via, segons la `Forma` de cada mode.

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
- Amb els busos, un dimecres a les 8 del matí hi ha uns 2.700 vehicles en circulació (1.000 interurbans, 850 de TMB, 600 de l'AMB i la resta, trens). Calcular-los tots triga uns 8 ms en un ordinador i carregar els horaris (`circulacions.json`, 8 MB, 2,8 MB comprimit dins l'APK), uns 0,5 s. Es pot fer al mòbil sense servidor; si es nota lent, el pas següent és calcular només els traçats que toquen la pantalla.
- Els interurbans tenen 433 recorreguts sense traçat al GTFS: aquests busos van en línia recta entre parades.
- `CapaVehicles` defineix les capes de MapLibre (cotxes i vidre en `fill-extrusion`, i la vora), la mida i la forma de cada mode i els colors. Cada mode (també el bus) és una entrada a `mides` amb la seva `Forma`. Els colors massa clars (els regionals de Renfe són gairebé blancs al GTFS) es dibuixen amb la tinta.
