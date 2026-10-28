# Andana 947 — Document de disseny del joc (GDD)

> Fase 1. La bíblia del joc: fórmules d'ingressos, arbre tecnològic, motor de caos, UX al carrer i simulació del primer mes.
> Última actualització: 28/10/2026
>
> Si alguna cosa d'aquí contradiu `docs/abast.md`, mana `abast.md`. Els valors definitius aniran a `dades/configuracio_joc.json` (fase 2), amb tests; aquí només n'hi ha la proposta.

## 0. Com llegir aquest document

Cada xifra porta una etiqueta que diu d'on surt:

| Etiqueta | Significat |
|---|---|
| **[real]** | Dada publicada per una font oberta o oficial. Es cita a la secció 11. |
| **[estimat]** | Surt d'un model d'estimació documentat en aquest document. A l'app i a la base de dades es marca com a *estimat*. |
| **[joc]** | Paràmetre de disseny. No pretén reflectir el món real: es tria perquè el joc funcioni i s'ajusta a la fase 8. |

El nom: l'**andana** és el lloc on s'espera el tren. És on el jugador fa el *check-in*, i on comença tot.

## 1. Visió i bucles de joc

### 1.1 Pilars

1. **El carrer és el tauler.** No es pot comprar res que no s'hagi trepitjat. El creixement el limita el territori, no els diners.
2. **Els números són de veritat.** Una parada val el que val perquè hi passa la gent que hi passa. Si a la vida real Catalunya té 17,5 milions d'entrades l'any i Casa de l'Aigua 47.000, al joc també.
3. **Catalunya sencera.** Una parada del Pallars ha de valer la pena (Bo de Cohesió), i Barcelona no pot valer mil vegades més que un poble.
4. **El caos és part del joc.** Rodalies falla, plou a l'Ebre i hi ha el Mobile. El jugador se'n protegeix, no ho pateix sense poder fer-hi res.
5. **L'economia és del servidor.** El client només mostra i demana; tots els diners es calculen a Supabase.

### 1.2 Bucles

| Bucle | Durada | Què fa el jugador |
|---|---|---|
| **Trajecte** | minuts | Obre l'app a l'andana o a la parada, veu què hi ha lliure a prop i hi fa *check-in*: conquereix una concessió, compra accions o en renova una. |
| **Dia** | 24 h | A les 04:00 (fi del servei) el servidor liquida el dia: ingressos, costos, dividends i caos. El jugador ho veu en obrir l'app. |
| **Setmana** | 7–14 dies | Renova les concessions tornant-hi (caduquen als 14 dies) i planifica rutes noves per créixer. |
| **Temporada** | mesos | Puja de nivell, desbloqueja l'arbre tecnològic, opera línies amb flota pròpia, entra en una cooperativa. |

### 1.3 Progressió en una frase

Concessions de parades (fase 4) → accions d'estacions grans → primer minibús llogat i primeres expedicions d'una línia de bus (fase 5) → flota i línies pròpies → trens.

### 1.4 Model de joc: híbrid

Decidit el 28/10/2026. El joc té una part individual i una part compartida, i tots els jugadors juguen al mateix mode.

| Què | Mode | Per què |
|---|---|---|
| **Parades** (concessions) | **Individual.** Cada jugador té el seu propi mapa de concessions, com a `municipis`: si tu tens Baró de Viver, un altre jugador també la pot tenir, i ningú te la pot prendre | Funciona des del primer dia amb pocs jugadors i a qualsevol poble |
| **Explotació de línies i flota** (fase 5) | **Individual.** Les expedicions del GTFS són de cada jugador | Igual que les parades |
| **Grans estacions** (accionariat) | **Compartit.** 100 accions per estació entre tots els jugadors | Són els llocs on coincideix més gent: el punt de trobada |
| **Caos** | **Compartit.** Un esdeveniment afecta tots els jugadors del seu àmbit | El món és el mateix per a tothom |
| **Cooperatives, rànquings i compravenda d'accions** (fase 7) | **Compartit** | La part social |

Conseqüència: no es pot perdre res per culpa d'un altre jugador, excepte quedar-se sense accions disponibles en una gran estació. Un mode de competició per temporades, amb parades disputades, queda com a idea per després de la fase 8.

## 2. Dades reals d'afluència: què existeix

Investigació feta el 28/10/2026. La descàrrega i la verificació de cada fitxer és feina de la fase 2 (`scripts/`). Des del xat no s'han pogut baixar els fitxers; les característiques són les que publiquen els portals.

### 2.1 Inventari

| Xarxa | Font | Què hi ha | Granularitat | Període | Llicència | Ús al joc |
|---|---|---|---|---|---|---|
| **Rodalies (Renfe)** | Renfe Data: *Volumen de viajeros por franja horaria (Barcelona)* | Viatgers pujats i baixats per estació (`CODIGO_ESTACION`, `NOMBRE_ESTACION`, `NUCLEO_CERCANIAS`, `TRAMO_HORARIO`, `VIAJEROS_SUBIDOS`, `VIAJEROS_BAJADOS`) | Estació × franja horària, dia feiner | **2018** (publicat el 2020) | CC BY 4.0 | Repartiment entre estacions del nucli de Barcelona |
| Rodalies | Renfe Data: *Volumen de viajeros por franja horaria (Estaciones de Cercanías)* | El mateix per a tots els nuclis | Estació × franja | 2018 | CC BY 4.0 | Comprovació creuada |
| Rodalies | Rodalies de Catalunya (Generalitat), notes anuals; ATM / OMC *Demanda per línia i operador* | Viatgers per línia | Línia × any | 2024 (127 milions a Rodalies) | Pendent de confirmar | Escalar el 2018 a l'any actual, línia per línia |
| Renfe (totals) | Idescat, Anuari: ferrocarril | Viatgers per operador i servei: Rodalies 117,5 M, regionals 11,3 M, Avant 2,0 M | Operador × any | 2024 | Idescat | Total de control dels regionals |
| **FGC** | Dades obertes FGC: *Demanda mensual per estació 2019-2024* | Viatgers per estació i mes | Estació × mes | 2019–2024 | Pendent de confirmar al portal | Afluència directa de cada estació |
| FGC | Generalitat (Socrata): *Demanda mensual de viatgers d'FGC* | Viatgers per estació, mes i any; línies Llobregat-Anoia, Barcelona-Vallès i Lleida–la Pobla | Estació × mes | 2019–2023 | Pendent de confirmar | Còpia de seguretat de l'anterior |
| **Metro (TMB)** | Portal de transparència de TMB: *Dades bàsiques i de demanda* | Demanda per línia i per estació: feiner, mensual i anual; demanda de metro i bus des del 2020 | Estació (entrades validades) × any, mes i feiner | 2020–2024 (2025 a comprovar) | Pendent de confirmar | Afluència directa de cada estació |
| **Bus TMB** | El mateix portal | Demanda per línia de bus | Línia × any | 2020–2024 | Pendent | Total de línia per al model de parades (2.3) |
| **TRAM** | ATM / OMC *Demanda per línia i operador* | Viatgers per línia | Línia × any | Anual | Pendent | Total de línia; les parades s'estimen |
| **Tots els modes STI** | ATM, *TransMet Xifres* (també a l'OMC) | Viatges i **recaptació** per àmbit, mode i operador; línies, km, vehicles | Operador / mode × any (semestral) | Anual | Pendent | Ingrés mitjà real per viatge (4.1) |
| **Bus interurbà** | Departament de Territori, *Estadística del transport regular de viatgers per carretera* | Viatgers, expedicions, línies, km, viatgers/km | Agregat (no consta per línia) | Anual (darrer: 2023) | Publicació oficial | Calibrar l'ocupació mitjana per expedició |
| Bus urbà fora de l'AMB | Memòries dels operadors municipals (Tarragona, Lleida, Girona, Terrassa, Sabadell…) | Variable | Variable | Variable | Variable | **Per investigar a la fase 2** |
| Oferta (totes) | GTFS de TMB, FGC, Renfe, AMB i Mou-te | Parades, línies, horaris, expedicions | Parada × expedició | Actual | Segons operador | Nodes del mapa i pes de cada parada |

Xifres de control conegudes **[real]**:

- TMB 2024: L1, més de 125 milions de validacions; L5, 111,6 M; L3, 88,8 M; L4, 61,6 M; L2, 48,7 M. Estacions: Catalunya 17.501.208 entrades, Diagonal 16.207.348, Espanya 15.135.060, Baró de Viver 477.756 i Casa de l'Aigua 46.884 (la que menys).
- TMB 2025: 700,5 milions de viatges entre metro i bus (+2,4 %).
- FGC 2025: 100,6 milions de viatges a les línies metropolitanes, 71,2 M a la Barcelona-Vallès.
- Rodalies, dia feiner del 2018: Sants 101.006 usuaris, Plaça de Catalunya 67.866, Passeig de Gràcia 38.791, Clot-Aragó 27.284, Arc de Triomf 26.539.

### 2.2 Model d'estimació per a les estacions de tren [estimat]

**Rodalies (nucli de Barcelona).** No hi ha dades per estació posteriors al 2018 (decidit el 28/10/2026: s'escalen, no es demanen dades noves). El repartiment entre estacions és el del 2018 i el volum és el de l'any actual, línia per línia.

1. **Base.** `entrades_2018(e)` = suma de `VIAJEROS_SUBIDOS` de totes les franges horàries de l'estació `e` (dataset de Renfe del 2018, dia feiner). Es fan servir només els pujats, perquè és el que compten TMB i FGC (entrades validades); els baixats serveixen per comprovar que l'estació quadra (pujats ≈ baixats en un dia).
2. **Pes de cada línia a l'estació.** `s(e, l)` = expedicions de la línia `l` que paren a `e` en un dia feiner, dividides per les de totes les línies que hi paren (GTFS de Renfe). Si l'estació és d'una sola línia, `s = 1`.
3. **Factor de cada línia.** `f(l) = total_línia(l, any) / total_línia(l, 2018)`, amb els dos totals de la **mateixa sèrie** (ATM / OMC, *Demanda per línia i operador*), perquè un canvi de font no es confongui amb un canvi de demanda. `any` és el darrer any complet publicat.
4. **Estimació.**

```
entrades_feiner(e, any) = entrades_2018(e) × Σ_l s(e, l) × f(l)
```

Detalls:

- **Línies que han canviat des del 2018** (traçats, noms o branques noves): una taula d'equivalències a `dades/` diu de quina o quines línies del 2018 ve cada línia actual, i `f` es calcula sobre el conjunt. Cada equivalència porta la seva font.
- **Estacions noves des del 2018:** no tenen base i s'estimen com les regionals (a sota).
- **Estacions tancades o en obres l'any de referència:** si una estació té un factor de línia que reflecteix un tall llarg i ella no hi era afectada, es fa servir la mitjana dels dos darrers anys complets. Es documenta cas per cas.
- **Hipòtesi que s'assumeix:** dins de cada línia, el pes relatiu de les estacions no ha canviat des del 2018. És la part més feble del model, i per això l'app marca aquestes estacions com a *estimat*.
- **Comprovacions (tests de la fase 2):** (a) cada estació d'una sola línia creix exactament `f(l)`, i el creixement total de la xarxa queda entre el `f` més petit i el més gran; (b) cap estació canvia d'ordre respecte de les altres de la mateixa línia; (c) cap factor `f` surt de l'interval 0,5–2 sense una explicació escrita.

**Regionals (R11, R13–R17, RG1, RT1, RT2, RL3, RL4) i estacions sense dada.** El total anual de regionals (Idescat, 11,3 M el 2024) es reparteix entre estacions amb:

```
pes_estació = expedicions_diàries_estació × població_municipi^0,5
```

L'exponent 0,5 evita que les capitals de comarca s'ho emportin tot. Es calibra a la fase 2 contra les estacions de Rodalies que sí tenen dada.

**FGC i metro:** dada directa, sense model.

### 2.3 Model d'estimació per a les parades de bus i de tramvia [estimat]

No hi ha afluència oberta per parada (`abast.md`, secció 5). El model reparteix el total de cada línia entre les seves parades:

```
viatgers_parada = Σ_línies  total_línia × pes_parada / Σ pes_parades_línia
pes_parada     = expedicions_diàries_parada × (1 + població_400m / 1.000)
```

- `expedicions_diàries_parada`: GTFS (`stop_times`).
- `població_400m`: població de les seccions censals a menys de 400 m de la parada (INE / Idescat, per secció censal).
- `total_línia`: dada real quan existeix (bus TMB, TRAM per línia). Si no n'hi ha, `expedicions_diàries_línia × ocupació_mitjana_expedició`, amb l'ocupació calibrada a partir de les estadístiques agregades (viatgers / expedicions) del bus interurbà o de l'operador.

Cada valor estimat es desa a la base de dades amb `font = 'estimat'` i la versió del model, i l'app ho diu a la fitxa de la parada («Afluència estimada»).

### 2.4 Altres dades reals que fa servir el joc

| Dada | Font | Valor o estat |
|---|---|---|
| Tarifes 2026 (ATM, 1 zona) | ATM, vigents des del 15/01/2026 | Bitllet senzill 2,90 €; T-casual 13,00 €; T-usual 22,80 € (amb el 50 % de bonificació a la T-usual i la T-jove) **[real]** |
| Ingrés mitjà per viatge | ATM, *TransMet Xifres* (recaptació / viatges) | A extreure a la fase 2 |
| Cànons d'Adif 2026 | Projecte de *Reglamento de determinación de los cánones ferroviarios* 2026–2028 | Línies no A, € per tren-km, serveis VL1 / VL2 / VCM: modalitat A 0,8971 / 0,9062 / 1,8442; B 0,4490 / 0,4536 / 0,7854; C (energia de tracció) 0,0886 / 0,0895 / 0,1839. Estacions urbanes, € per parada: categoria 1 de 2,148 (intermèdia) a 6,136 (origen); categoria 5 de 0,426 a 1,217; més 0,0192 € per viatger pujat o baixat **[real, projecte]**. Falta confirmar quin codi de servei correspon a Rodalies i la versió publicada al BOE |
| Preu de l'energia | OMIE, preu marginal diari del mercat espanyol | Fitxers diaris públics; descàrrega a verificar a la fase 2 |
| Material rodant (Rodalies) | Contracte de Renfe del 15/12/2022 | 97 trens per 858,4 M€: Alstom 49 trens de 100 m (368 M€, ~7,5 M€/tren), Stadler 20 de 200 m (307,7 M€, ~15,4 M€/tren), CAF 28 regionals (183 M€, ~6,5 M€/tren) **[real; preu mitjà del lot]** |
| Autobusos | Contracte de TMB del 2021 (210 vehicles, 112,5 M€) | Elèctric 12 m ~0,60 M€, elèctric articulat 18 m ~0,90 M€, híbrid de GNC ~0,41 M€, de dos pisos ~0,53 M€ **[real; preu mitjà del lot, que pot incloure serveis]** |
| Incidències de Rodalies | Departament de Territori al Parlament (1r semestre 2025) | Incidències destacades: R15 111, R14 77, R16 69, R13 45, R17 45, R11 27, R4 17, R2 13, R1 11, R3 10. Els regionals sumen el 82 % de les greus **[real]** |
| Calendari | Generalitat, festes laborals; organitzadors (MWC, Marató, Mercè…) | Dies feiners i festius, i esdeveniments urbans |
| Territori | ICGC (límits) i Idescat (població, densitat) | Com a `municipis` |

## 3. Nodes

Un **node** és una parada o estació real del GTFS on es pot fer *check-in*.

| Tipus de node | Agrupació | Exemple |
|---|---|---|
| Estació de metro | Un node per estació, amb totes les línies de TMB (com publica TMB l'afluència) | Catalunya (L1, L3) |
| Estació de Rodalies / regional | Un node per estació de Renfe | Clot-Aragó |
| Estació d'FGC | Un node per estació d'FGC | Sabadell Plaça Major |
| Parada de TRAM | Un node per parada | |
| Parada de bus | Un node per parada física (`stop_id` del GTFS, fusionant els dos sentits si són a menys de 30 m) | |

Estacions d'operadors diferents al mateix lloc (Catalunya de TMB, Plaça de Catalunya de Renfe i Catalunya d'FGC) són **nodes diferents**: és el que fa interessant la intermodalitat (4.5).

### 3.1 Categories

| Categoria | Condició **[joc]** | Com es posseeix |
|---|---|---|
| **Parada** | Menys de 20.000 viatgers en un dia feiner | **Concessió** individual: cada jugador té la seva (1.4) |
| **Gran estació** | 20.000 o més | **Accionariat**: 100 accions, màxim 20 per jugador |

Amb les dades del 2.1, són grans estacions Catalunya, Diagonal, Espanya, Sants, Plaça de Catalunya, Passeig de Gràcia, Clot-Aragó i Arc de Triomf, entre d'altres. Baró de Viver (≈1.500 per feiner) i Casa de l'Aigua (≈150) són parades.

## 4. Economia

Tot es calcula al servidor (funcions de Supabase i `pg_cron`). El client mai envia una quantitat de diners: envia una intenció («vull comprar aquesta concessió») i el servidor la valida i la cobra. La moneda del joc són les **monedes** (decidit el 28/10/2026). Al servidor es guarden amb dos decimals; l'app mostra els saldos i els preus arrodonits i els dividends amb decimals. Plural correcte als textos: «1 moneda», «2 monedes». Icona pròpia. Per connectar el joc amb les dades reals, **1 € real equival a 1 moneda**: la tarifa, els cànons d'Adif, el preu de l'energia i el preu dels vehicles entren a les fórmules en euros i en surten en monedes. No es poden comprar amb diners de veritat.

### 4.1 Ingrés d'una concessió

```
I(P) = t × q × P_ref × (P / P_ref)^α
```

| Símbol | Significat | Valor proposat |
|---|---|---|
| `P` | Viatgers del node aquell dia (4.4) | **[real]** o **[estimat]** |
| `t` | Ingrés per viatge | 1,30 € (T-casual 1 zona 2026 / 10 viatges) **[real]**. A la fase 2 es canvia per l'ingrés mitjà real per viatge de l'ATM, per mode |
| `q` | Quota del concessionari | 1 % **[joc]** |
| `P_ref` | Afluència de referència | 1.000 viatgers/dia **[joc]** |
| `α` | Compressió | 0,6 **[joc]** |

Per què hi ha compressió: amb un repartiment lineal, Catalunya (≈55.000 al dia) donaria 370 vegades més que Casa de l'Aigua (≈150). Amb `α = 0,6` en dona 35 vegades més. L'ordre de les parades segueix sent exactament el de les dades reals; només s'escurça la distància.

| Viatgers/dia | Ingrés/dia | Preu de la concessió | Cèntims per viatger |
|---:|---:|---:|---:|
| 10 | 0,82 monedes | 10 monedes | 8,2 |
| 100 | 3,27 monedes | 33 monedes | 3,3 |
| 1.000 | 13,00 monedes | 130 monedes | 1,3 |
| 10.000 | 51,75 monedes | 518 monedes | 0,5 |
| 100.000 | 206,04 monedes | 2.060 monedes | 0,2 |

### 4.2 Preu, durada i caducitat

```
preu_concessió = max(10 monedes, 10 × I(P_feiner))      [joc: es recupera en 10 dies feiners]
preu_acció     = max(1 moneda, preu_concessió / 100)
```

- Una concessió dura **14 dies** **[joc]**. Fer-hi *check-in* durant la segona setmana la renova 14 dies més, sense cost.
- Si caduca, la concessió queda **inactiva**: no dona ingressos ni compta per a la intermodalitat fins que el jugador hi torna a fer *check-in*, que la reactiva 14 dies sense pagar-la de nou. No passa mai a un altre jugador (1.4). Les concessions inactives no compten per al límit de la llicència.
- **Llicència d'operador:** màxim de concessions = 10 + 2 × nivell **[joc]**. Això obliga a triar i evita que algú acapari un barri sencer.
- Les accions no caduquen, però una gran estació on un jugador no fa *check-in* en 30 dies deixa de pagar-li dividends fins que hi torna **[joc]**.

### 4.3 Accionariat de les grans estacions

- 100 accions per estació. Cada *check-in* permet comprar-ne fins a 5, i cap jugador en pot tenir més de 20 **[joc]**. Per omplir una estació calen almenys 5 jugadors.
- Dividend diari per acció = `I(P) / 100`.
- Les accions no venudes no paguen a ningú (no hi ha un «operador públic» que les cobri).
- Les accions entren al MVP (fase 4): compra amb *check-in* i dividends. La compravenda entre jugadors, les juntes i les cooperatives són de la fase 7 (decidit el 28/10/2026).

### 4.4 Viatgers de cada dia

```
P_feiner     = afluència d'un feiner (dada directa o estimada)
P_no_feiner  = P_feiner × 0,6                      [estimat]
P_feiner     = P_anual / (251 + 0,6 × 114)          quan la font és anual
```

- 251 i 114 són els dies feiners i no feiners del 2026 a Catalunya **[real]**; el calendari de festes es llegeix de la Generalitat.
- El factor 0,6 es calibrarà a la fase 2 amb les dades de TMB, que publica alhora l'afluència de feiner i l'anual de cada estació.
- Els esdeveniments de caos (6) multipliquen `P` o els costos d'aquell dia.

### 4.5 Intermodalitat Perfecta

Si un jugador té una estació de tren (concessió o ≥ 5 accions) i almenys una parada de bus o de TRAM a menys de **100 m** (`abast.md`), tots aquests nodes reben ×1,25. Cada mode més dins del mateix radi hi suma 0,10, fins a ×1,50 **[joc]**. La distància es calcula amb PostGIS (`ST_DWithin` sobre `geography`).

### 4.6 Bo de Cohesió Territorial

Una concessió en un municipi de menys de 20 hab/km² (densitat de l'Idescat **[real]**) o de l'Alt Pirineu i Aran rep cada dia `max(I(P), 3 monedes)` **[joc]**. Així, una parada de 10 viatgers al dia al Pallars dona 3 monedes, com una de 160 a l'àrea metropolitana. Els llindars van a la configuració.

### 4.7 Explotació de línies (fase 5)

A la fase 5 el jugador deixa de ser només concessionari i opera **expedicions reals** del GTFS. És lineal, sense compressió: aquí el realisme és el joc.

```
ingrés_expedició = (viatgers_línia_dia / expedicions_línia_dia) × t_mode
cost_expedició   = km × (cànon_A + cànon_B + cànon_C)              (tren, Adif)  [real]
                 + km × consum_kWh_km × preu_OMIE_dia               (elèctric)   [real × estimat]
                 + km × consum_l_km × preu_gasoil                    (dièsel)     [real × estimat]
                 + hores × cost_hora_personal                                      [estimat, convenis]
                 + km × manteniment_km                                             [estimat]
                 + amortització_dia_vehicle / expedicions_dia_vehicle              [real: preu ÷ vida útil]
                 + parades × cànon_estació                           (tren)       [real]
subvenció        = viatgers × subvenció_per_viatger_zona                           [joc, calibrada amb l'ATM]
```

- Per operar expedicions d'una línia cal tenir com a mínim una concessió o 5 accions en una de les seves parades. El màxim d'expedicions que pot operar és proporcional a la fracció de parades de la línia que té **[joc]**.
- Les expedicions són les del GTFS real. Com que el mode és individual (1.4), un jugador no en treu cap a un altre; el límit és la seva flota i la fracció de parades de la línia que té.
- **Vehicles:** es lloguen (rènting) abans de poder-se comprar. La quota diària és l'amortització real: preu de contracte ÷ vida útil (12 anys per al bus, 30 per al tren **[estimat]**). Exemple: un bus elèctric de 12 m a ~0,60 M€ són ≈137 monedes/dia. El lloguer demana una fiança de 30 dies de quota **[joc]**.
- Un bus elèctric té costos d'energia baixos i un preu alt; un dièsel, el contrari, i a dins de la **ZBE Rondes de Barcelona** paga una penalització diària **[joc]**.

### 4.8 Tic econòmic diari

Cada dia a les **04:00** (hora de Barcelona), `pg_cron` llança una funció que, per a cada jugador:

1. Tanca les concessions caducades.
2. Llegeix els esdeveniments de caos del dia.
3. Calcula `P` de cada node (tipus de dia × caos), l'ingrés, els dividends i el bo de cohesió.
4. A la fase 5, els ingressos i costos de cada expedició, amb el preu OMIE del dia.
5. Escriu un **apunt** per concepte a un llibre comptable (`moviments`) i actualitza el saldo. El saldo és sempre la suma dels apunts: es pot auditar i recalcular.

## 5. Arbre tecnològic

### 5.1 Prestigi i nivells

| Acció | Prestigi **[joc]** |
|---|---|
| Primer *check-in* en un node | 20 |
| Conquerir una concessió | 30 |
| Renovar una concessió | 5 |
| Comprar accions | 2 per acció |
| Primer node d'un municipi nou | 50 |
| Primer node d'una comarca nova | 200 |

Per arribar al nivell `n` calen `100 × (n − 1)^1,5` punts de prestigi **[joc]**: el nivell 1 és l'inici, el 2 demana 100 punts, el 3 283, el 4 520 i el 5 800. El prestigi no es gasta: els desbloquejos demanen nivell **i** diners.

### 5.2 Branques

| Branca | Desbloqueig | Nivell | Cost | Requisit | Efecte |
|---|---|---|---|---|---|
| **Gestió** | Oficina de barri | 1 | — | — | Inici. 10 + 2 × nivell concessions |
| | Delegació comarcal | 8 | [joc] | Concessions en 3 comarques | +10 concessions; renovar a distància una vegada per setmana |
| | Seu central | 20 | [joc] | Delegació | Gestionar cooperatives (fase 7) |
| **Bus** | Bus del Barri (minibús elèctric) | 5 | Fiança | 3 parades d'una línia de Bus del Barri | Operar expedicions de Bus del Barri |
| | Bus urbà estàndard 12 m (dièsel, híbrid o elèctric) | 8 | Fiança | Bus del Barri | Línies convencionals i de la xarxa ortogonal (H, V, D) |
| | Articulat 18 m | 12 | Fiança | Una cotxera | Línies H i V |
| | Biarticulat 24 m | 18 | Fiança | Articulat | Línies de més demanda |
| | Autocar interurbà | 10 | Fiança | Concessions en 2 municipis de la línia | Línies interurbanes |
| | Cotxera (Triangle, Horta o Ponent) | 12 | [joc] | 10 vehicles a Barcelona | Calen per tenir més de 10 busos a Barcelona; −15 % de manteniment |
| **Ferrocarril** | Lloguer de 447 | 15 | Fiança | 10 % de les estacions d'una línia de Rodalies | Expedicions de Rodalies |
| | Civia (463/464/465) | 18 | Fiança | Taller de Sant Andreu | Més places i menys consum |
| | Unitats d'FGC | 15 | Fiança | Taller de Rubí | Expedicions de les línies d'FGC |
| | Regionals (470 i successors) | 22 | Fiança | 20 % de les estacions d'una línia regional | Línies R11–R17, RG1, RT, RL |
| | Alta velocitat i mercaderies | 30 | [joc] | Seu central | Final de l'arbre (abast) |
| **Energia** | Contracte a preu fix | 10 | [joc] | — | Paga un preu fix, amb prima, en lloc de l'OMIE del dia |
| | Plaques solars a la cotxera | 14 | [joc] | Cotxera | −20 % d'energia de la cotxera |
| | Frenada regenerativa | 18 | [joc] | Civia o bus elèctric | −10 % de consum |
| **Resiliència** | Vigilància del coure | 15 | [joc] | Línia R3, R4 o R8 | −50 % de probabilitat de robatori en les seves expedicions |
| | Tanques per a la fauna | 15 | [joc] | Línia d'FGC del Vallès o de Puigcerdà | −50 % d'atropellaments d'animals |
| | Bus llançadora | 10 | [joc] | Bus urbà | Recupera el 50 % dels ingressos d'un tall de via |
| | Assegurança | 6 | Prima diària [joc] | — | Cobreix part de les pèrdues per caos |

Les característiques tècniques del material rodant (places, consum, velocitat, preu) es verificaran a la fase 5 amb fonts citades. La llista de sèries és la d'`abast.md`.

## 6. Motor de caos

### 6.1 Principis

- **Res amb víctimes.** No es recreen accidents amb ferits o morts (per exemple, el de Gelida del 2026), atemptats ni suïcidis. El caos és molèstia, no tragèdia.
- **Probabilitats calibrades.** Les probabilitats **[joc]** es calibren amb dades reals de freqüència quan n'hi ha (per exemple, les incidències per línia del Departament de Territori), i es documenta com.
- **Agència.** Cada esdeveniment té una manera de mitigar-lo a l'arbre tecnològic (5.2).
- **Igual per a tothom.** Els esdeveniments són de l'àmbit (línia, tram, municipi), no del jugador: afecten tots els jugadors d'aquell àmbit alhora.
- **Avís.** L'app ho notifica i ho mostra al mapa, amb un text propi, mai copiat de notícies.

### 6.2 Taula d'esdeveniments

Probabilitat per dia i per àmbit. Efecte sobre `P` (viatgers) o sobre els costos.

| Esdeveniment | Àmbit | Probabilitat/dia **[joc]** | Durada | Efecte | Calibratge |
|---|---|---|---|---|---|
| Robatori de coure | Tram de R3, R4 o R8 | 0,4 % | 1 dia | `P` −60 % al tram; +reparació | Freqüència real de robatoris per línia (fase 6) |
| Atropellament d'animals | FGC Vallès, línia de Puigcerdà | 0,3 % | 3 h → ×0,85 el dia | `P` −15 % a la línia | Incidències per línia |
| Incidència a la costa (Garraf, R2 Sud; R1) | Tram | 0,3 % | 1 dia | `P` −50 % al tram | Incidències per línia |
| Avaria d'infraestructura | Línia de Rodalies o regional | Proporcional a les incidències destacades reals per línia (R15 > R14 > R16 > …) | 1 dia | `P` −30 % | Departament de Territori, 1r semestre 2025 |
| Temporal o DANA | Ebre, Maresme | 0,2 % (×3 de setembre a novembre) | 1–3 dies | `P` −40 %; +costos | Avisos del Meteocat (fase 6) |
| MWC | Fira Gran Via (L9 Sud, FGC Europa \| Fira) | Calendari real | 4 dies | `P` +40 % a les parades de l'entorn | Dates oficials |
| Sant Jordi | Barcelona i capitals | Calendari real (23 d'abril) | 1 dia | `P` +30 % al centre | Demanda diària de TMB |
| Marató de Barcelona | Línies de bus del recorregut | Calendari real | 1 dia | `P` −80 % al bus afectat; +20 % al metro | Recorregut oficial |
| Manifestació | Bus del centre de Barcelona | 1 % (feiner) | 1 dia | `P` −50 % al bus del centre | — |
| Obres (p. ex. Via Laietana) | Línies de bus afectades | Calendari real d'obres | Setmanes | `P` −25 % i desviació | Ajuntament |
| Vaga | Operador | Calendari real si n'hi ha | 1 dia | Serveis mínims: `P` × serveis mínims | Convocatòries |

### 6.3 Implementació

- Una funció diària (`pg_cron`, abans del tic de les 04:00) sorteja els esdeveniments aleatoris amb una llavor desada a la base de dades, perquè el resultat es pugui reproduir i auditar.
- Els esdeveniments de calendari es carreguen des de `dades/` amb la seva font.
- Taula `esdeveniments`: tipus, àmbit (línia, tram o polígon PostGIS), inici, fi, multiplicadors, origen (`aleatori` / `calendari`) i llavor.

## 7. UX al carrer

### 7.1 Pantalles (fase 3–4)

1. **Mapa** (principal). Nodes a prop amb el color de la línia; els teus, plens; els lliures, buits; les grans estacions, amb el percentatge d'accions venudes. Mapa base pendent de decidir (`abast.md`, secció 9).
2. **A prop.** Llista ordenada per distància: nom, línies, afluència («≈1.500 al dia», amb «estimat» quan ho és), estat i què pots fer-hi.
3. **Fitxa del node.** Afluència, ingrés diari, propietari o accionistes, esdeveniments actius i el botó d'acció.
4. **Imperi.** Saldo, ingrés d'ahir, concessions que caduquen aviat (amb «Com hi vaig»), accions i llibre de moviments.
5. **Arbre tecnològic**, **Perfil i rànquing**, com a `municipis`.

### 7.2 Flux de *check-in*

1. El jugador toca el node a «A prop» o al mapa.
2. L'app demana una ubicació fresca (no la de la memòria cau) i l'envia amb la precisió, l'hora i el testimoni de Play Integrity.
3. El servidor valida (7.3). Si tot quadra, fa l'acció i respon amb el resultat i el saldo nou.
4. El botó contesta, com a `municipis`: «Teva!», «+5 accions», «Renovada fins al 12/11», o «No t'arriba: et falten 40 m».

### 7.3 Regles de validació **[joc]**

| Regla | Parada de bus o TRAM | Estació de tren o metro |
|---|---|---|
| Radi des del punt GTFS | 60 m | 150 m (les andanes són llargues) |
| Precisió del GPS | ≤ 50 m | ≤ 50 m |
| Condició | `distància − precisió ≤ radi` | igual |
| Velocitat en el moment del *check-in* | ≤ 10 km/h | ≤ 10 km/h |

- **Des de l'andana:** dins d'un tren aturat a l'estació es pot fer *check-in*. Amb el tren en marxa, no.
- **Velocitat entre *check-ins*:** si la distància en línia recta dividida pel temps supera 330 km/h (per sobre de qualsevol tren de Catalunya), es rebutja.
- **Una acció per node i per dia.**
- Ubicació simulada (*mock location*) o Play Integrity fallit: es rebutja i es registra. Tres rebuigs en una setmana bloquegen el *check-in* 24 h.
- Tot es valida a la funció del servidor; el client només fa d'avís previ.

### 7.4 Seguretat i benestar

- El joc **mai** demana entrar a zones restringides: les coordenades són les del GTFS, en espais públics.
- Avís en obrir el mapa amb moviment de cotxe: «Si condueixes, ja hi tornaràs». No es pot fer *check-in* a més de 10 km/h.
- Cap notificació demana anar a un lloc concret de nit. Les notificacions de caducitat surten entre les 8 i les 21 h.
- Avisos de geovalla opcionals («Tens una parada lliure a 80 m»), desactivats per defecte, per estalviar bateria.

### 7.5 Accessibilitat

Com a `municipis`: elements tàctils de 48 dp, contrast 4,5:1, «Reduir animacions», textos a `strings.xml`. Els colors de línia sempre van acompanyats del nom de la línia (no es fa servir el color sol per distingir).

## 8. Simulació del primer mes

`scripts/simulacio_primer_mes.py` aplica les regles 4.1–4.4 amb les xifres reals de la secció 2.1. Es pot tornar a executar: `python3 scripts/simulacio_primer_mes.py`.

**Perfil.** La Laia viu al Bon Pastor i treballa a prop de Diagonal. Cada dia feiner fa *check-in* a Baró de Viver (L1), Clot-Aragó i Passeig de Gràcia (Rodalies) i Diagonal (L3, L5). Els dimarts passa per Arc de Triomf, els dimecres per Sants, els divendres surt per Plaça de Catalunya i Catalunya, els dissabtes va a Espanya, i dues vegades al mes visita algú a prop de Casa de l'Aigua. Comença el dilluns 2 de novembre de 2026 amb 200 monedes. No hi ha caos ni altres accionistes que li esgotin les accions.

**Valors dels nodes** (afluència real; ingrés i preu, proposta del GDD):

| Node | Viatgers/feiner | Ingrés sencer/dia | Forma | Preu |
|---|---:|---:|---|---:|
| Casa de l'Aigua (L11) | 147 | 4,11 monedes | Concessió | 41,11 monedes |
| Baró de Viver (L1) | 1.496 | 16,55 monedes | Concessió | 165,53 monedes |
| Arc de Triomf (Rodalies) | 26.539 | 92,96 monedes | Accions | 9,30 monedes/acció |
| Clot-Aragó (Rodalies) | 27.284 | 94,51 monedes | Accions | 9,45 monedes/acció |
| Passeig de Gràcia (Rodalies) | 38.791 | 116,73 monedes | Accions | 11,67 monedes/acció |
| Espanya (L1, L3) | 47.386 | 131,62 monedes | Accions | 13,16 monedes/acció |
| Diagonal (L3, L5) | 50.743 | 137,14 monedes | Accions | 13,71 monedes/acció |
| Catalunya (L1, L3) | 54.794 | 143,61 monedes | Accions | 14,36 monedes/acció |
| Plaça de Catalunya (Rodalies) | 67.866 | 163,28 monedes | Accions | 16,33 monedes/acció |
| Sants (Rodalies) | 101.006 | 207,28 monedes | Accions | 20,73 monedes/acció |

**Evolució:**

| Dia | Data | Ingrés del dia | Saldo | Concessions | Accions |
|---:|---|---:|---:|---:|---:|
| 1 | dl. 2/11 | 19,39 monedes | 25,51 monedes | 1 | 3 |
| 7 | dg. 8/11 | 23,16 monedes | 50,29 monedes | 1 | 15 |
| 14 | dg. 15/11 | 42,22 monedes | 94,74 monedes | 1 | 38 |
| 21 | dg. 22/11 | 76,20 monedes | 188,23 monedes | 1 | 73 |
| 30 | dt. 1/12 | 150,60 monedes | 830,83 monedes | 2 | 104 |

En tot el mes ingressa 2.136,82 monedes i n'inverteix 1.506 monedes. Acaba amb 20 accions (el màxim) a Clot-Aragó, Passeig de Gràcia i Diagonal, 16 a Espanya, 11 a Arc de Triomf, 7 a Plaça de Catalunya, 5 a Catalunya i 5 a Sants, i les concessions de Baró de Viver (renovada 3 vegades) i Casa de l'Aigua.

**Què n'aprenem:**

1. **El ritme és bo.** L'ingrés es multiplica per 7,8 en un mes, però a partir de la tercera setmana el fre ja no són els diners sinó els topalls: 5 accions per *check-in* i 20 per estació. Per seguir creixent, la Laia ha d'anar a llocs nous. És el pilar 1.
2. **Les grans estacions pesen massa al principi.** Gairebé tot l'ingrés ve d'accions. Quan existeixi el model de bus (2.3), un jugador nou tindrà moltes parades petites a prop de casa i això s'equilibrarà. Cal repetir la simulació a la fase 2 amb parades de bus.
3. **El primer bus arriba al segon mes.** La fiança d'un bus elèctric (30 × 137 monedes ≈ 4.100 monedes, 4.7) queda a unes 3 setmanes del saldo final, amb l'ingrés de 150 monedes/dia. Pel que fa al prestigi (calculat a mà amb la taula 5.1), la Laia en suma uns 730 (10 nodes nous, 2 conquestes, 3 renovacions, 104 accions, el primer municipi i la primera comarca), que és el nivell 4. Al Bus del Barri (nivell 5, 800 punts) hi arriba al cap de pocs dies, més o menys quan reuneix la fiança: els diners i el nivell van acompassats. Cal confirmar-ho quan hi hagi parades de bus a la simulació.
4. **Les accions s'esgoten.** Amb el model híbrid (1.4), les concessions no es perden, però a les grans estacions concorregudes les 100 accions s'acabaran. Cal simular-ho amb molts jugadors a la fase 8, per si cal ajustar el topall de 20 per jugador o el llindar de 20.000 viatgers.

## 9. Proposta de configuració

Esborrany del que serà `dades/configuracio_joc.json` (fase 2). Les etiquetes de la secció 0 aniran a un camp `origen` de cada bloc.

```json
{
  "economia": {
    "capitalInicial": 200,
    "tarifaBase": 1.30,
    "quotaConcessio": 0.01,
    "afluenciaReferencia": 1000,
    "exponentAfluencia": 0.6,
    "diesRetorn": 10,
    "preuMinim": 10,
    "factorNoFeiner": 0.6,
    "horaTic": "04:00"
  },
  "concessions": {
    "duradaDies": 14,
    "finestraRenovacioDies": 7,
    "maximBase": 10,
    "maximPerNivell": 2
  },
  "accionariat": {
    "llindarViatgersFeiner": 20000,
    "accionsPerEstacio": 100,
    "maximPerJugador": 20,
    "maximPerCheckin": 5,
    "diesSenseVisitaSenseDividend": 30
  },
  "intermodalitat": { "radiMetres": 100, "multiplicador": 1.25, "perModeAddicional": 0.10, "maxim": 1.50 },
  "cohesio": { "densitatMaxima": 20, "ingresMinimDiari": 3 },
  "checkin": {
    "radiParadaMetres": 60,
    "radiEstacioMetres": 150,
    "precisioMaximaMetres": 50,
    "velocitatMaximaKmh": 10,
    "velocitatEntreCheckinsKmh": 330
  },
  "prestigi": { "baseNivell": 100, "exponentNivell": 1.5 },
  "flota": { "diesFianca": 30, "vidaUtilBusAnys": 12, "vidaUtilTrenAnys": 30 }
}
```

## 10. Preguntes obertes

1. ~~**Rodalies 2018.**~~ Decidit el 28/10/2026: s'escala el repartiment del 2018 amb els totals per línia actuals (2.2).
2. **Bus fora de l'AMB i bus interurbà.** No hem trobat afluència per línia oberta. Proposta: model per expedicions i població (2.3) fins que en trobem. Hi estàs d'acord, o vols que la fase 2 comenci demanant-les als operadors (Sagalés, Moventis, EMT de Tarragona…)?
3. ~~**Accionariat al MVP.**~~ Decidit el 28/10/2026: les accions de les grans estacions (compra i dividends) entren a la fase 4; la compravenda entre jugadors, les juntes i les cooperatives queden per a la fase 7. Actualitzat a `abast.md`.
4. ~~**Moneda.**~~ Decidit el 28/10/2026: **monedes**, amb 1 € real = 1 moneda (4).
5. **Llicències.** Cal confirmar les llicències d'FGC, TMB i l'ATM abans de publicar l'app (2.1).
6. **Cànons d'Adif.** Cal confirmar quin codi de servei correspon a Rodalies i fer servir la versió publicada al BOE.

## 11. Fonts

Consultades el 28/10/2026.

- Renfe Data, [Volumen de viajeros por franja horaria (Barcelona)](https://data.renfe.com/dataset/volumen-de-viajeros-por-franja-horaria-barcelona/resource/10c14d86-c031-4c99-b8e3-bbc8994b6024) i [(Estaciones de Cercanías)](https://data.renfe.com/dataset/volumen-de-viajeros-por-franja-horaria-estaciones-de-cercanias/resource/566dcfa2-4c02-48bf-99c9-a7606a097073). CC BY 4.0.
- Rodalies de Catalunya, [Més de 127 milions de viatgers el 2024](https://rodalies.gencat.cat/ca/detalls/article/02_06_172_Millons).
- Tot Barcelona, [L'estació de Sants supera els 100.000 usuaris diaris de Rodalies](https://www.totbarcelona.cat/mobilitat/l-estacio-de-sants-supera-els-100-000-usuaris-diaris-de-rodalies-64813/) (dades de Renfe, 2018).
- Idescat, [Anuari estadístic: ferrocarril, transport de viatgers](https://www.idescat.cat/indicadors/?id=aec&n=15580).
- FGC, [Demanda mensual per estació 2019-2024](https://fgc.opendatasoft.com/explore/dataset/demanda-mensual-per-estacio-2019-2024/); Generalitat, [Demanda mensual de viatgers d'FGC](https://analisi.transparenciacatalunya.cat/Transport/Demanda-mensual-de-viatgers-d-FGC/4z2p-zzwm) ([fitxa a datos.gob.es](https://datos.gob.es/en/catalogo/a09002970-demanda-mensual-de-viajeros-en-fgc)).
- Diari de Sabadell, [FGC 2025: rècord de viatges](https://www.diaridesabadell.com/valles/fgc-dades-2025-record-viatges-valles.html).
- TMB, [Quines són les estacions amb més i menys usuaris](https://noticies.tmb.cat/transport/vols-saber-quines-son-estacions-amb-mes-menys-usuaris-de-xarxa-de-metro), [La L1 supera els 125 milions de validacions](https://noticies.tmb.cat/sala-de-premsa/linia-1-metro-de-barcelona-es-mes-utilitzada-superant-125-milions-de-validacions) i [Dades bàsiques i de demanda](https://www.tmb.cat/ca/web/transparencia-tmb/serveis-publics/cartes-i-cataleg-serveis/cataleg-serveis-prestats/dades-basiques-i-demanda).
- Ara, [TMB supera els 700 milions de viatges el 2025](https://en.ara.cat/society/tmb-s-public-transport-exceeds-700-million-journeys-in-year-for-the-first-time_25_5612998.html).
- Observatori de la Mobilitat de Catalunya, [Demanda per línia i operador ferroviari](https://omc.cat/en/w/demand-per-line-and-railway-operator), [Demanda a les estacions](https://omc.cat/en/w/demand-at-railway-stations) i [Demanda per mes, línia i operador (STI)](https://omc.cat/w/demand-month-railway-stibcn).
- Idescat, PAAE: [Estadística del transport públic col·lectiu de l'STI (ATM, TransMet Xifres)](https://idescat.cat/paae/2024/030708) i [Estadística del transport regular de viatgers per carretera](https://idescat.cat/paae/2024/030704).
- 3Cat, [Com queden les tarifes del transport públic per al 2026](https://www.3cat.cat/3catinfo/com-queden-les-tarifes-del-transport-public-per-al-2026-a-barcelona-tarragona-lleida-i-girona/noticia/3386238/).
- Adif, [Proyecto de Reglamento de determinación de los cánones ferroviarios 2026-2028](https://www.adif.es/documents/d/adif/adif-proyecto-reglamento-canones-2026-2027-2028).
- València Plaza, [Renfe compra 97 trens per a Rodalies per 858,4 milions](https://valenciaplaza.com/valenciaplaza/renfe-firma-con-alstom-stadler-y-caf-la-compra-de-97-trenes-para-rodalies-por-858-4-millones) (15/12/2022).
- TMB, [TMB encarrega 210 busos elèctrics i híbrids](https://noticies.tmb.cat/sala-de-premsa/tmb-encarrega-210-busos-electrics-hibrids-activar-renovacio-descarbonitzacio-de) (2021).
- Nació Digital, [L'R4 és la línia de Rodalies amb més incidències destacades](https://naciodigital.cat/manresa/politica/lr4-es-la-linia-de-rodalies-amb-mes-incidencies-destacades-de-catalunya-el-2025.html) (dades del Departament de Territori, 1r semestre 2025).
