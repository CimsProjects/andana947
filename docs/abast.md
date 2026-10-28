# Andana 947 — Abast del projecte

> Document viu. Recull les decisions preses abans de començar el desenvolupament.
> Última actualització: 28/10/2026

## 1. Concepte

Joc mòbil multijugador de geolocalització i gestió econòmica (*tycoon*) ambientat a tot Catalunya. Fusiona l'exploració física a l'estil Pokémon GO amb la gestió empresarial d'un operador de transport, utilitzant dades reals del sistema de transport públic català.

- **Terreny de joc:** el mapa real dels 947 municipis de Catalunya.
- **Nodes:** estacions i parades reals (Rodalies, FGC, metro i bus de TMB, TRAM, bus interurbà) a les seves coordenades GPS exactes, a partir de dades GTFS obertes.
- **Conquesta:** per adquirir els drets d'explotació d'una parada o estació, el jugador s'hi ha de desplaçar físicament i fer *check-in* amb el GPS del mòbil.
- **Ingressos:** cada concessió genera ingressos passius diaris calculats sobre l'afluència real de passatgers.
- **Moneda:** les **monedes** (1 € real = 1 moneda a les fórmules).
- **Model de joc híbrid:** les concessions de parades, les línies i la flota són individuals (cada jugador té el seu mapa i ningú li pot prendre res); les accions de les grans estacions, el caos, les cooperatives i els rànquings són compartits. Detall a `docs/gdd.md`, secció 1.4.
- **Projecte germà i referència:** [CimsProjects/municipis](https://github.com/CimsProjects/municipis) (Bocins de Catalunya). En seguim l'arquitectura, les normes i l'estil visual, amb variacions lleugeres.

## 2. Xarxes i línies

Es fan servir les denominacions reals de les línies.

| Xarxa | Línies |
|---|---|
| Metro TMB | L1, L2, L3, L4, L5, L9 Nord, L9 Sud, L10 Nord, L10 Sud, L11 |
| FGC | L6, L7, L8, L12, S1, S2, S3, S4, S8, S9, R5, R6, R50, R60, R53, R63, RL1, RL2 (Lleida–la Pobla) |
| Rodalies | R1, R2, R2 Nord, R2 Sud, R3, R4, R7, R8, R11, R13, R14, R15, R16, R17, RT1, RT2, RG1, RL3, RL4 |
| TRAM | T1, T2, T3, T4, T5, T6 |
| Bus TMB | Xarxa ortogonal (H, V, D), X, Bus del Barri, línies convencionals |
| Bus urbà fora de l'AMB | Tarragona, Lleida, Girona, Terrassa, Sabadell, etc. |
| Bus interurbà | Sagalés, Moventis, Plana, Teisa i altres operadors |
| Alta velocitat i mercaderies | Material rodant adquirible a l'arbre tecnològic |

> Cal verificar la llista definitiva de línies amb les dades GTFS a la fase 2.

## 3. Mecàniques principals

1. **Economia de passatgers.** La fórmula base és: passatgers reals × tarifa base − costos (flota, cànon Adif, energia, nòmines, manteniment).
2. **Intermodalitat Perfecta.** Posseir una estació de tren i les parades de bus en un radi de 100 m dona un multiplicador permanent per transbord.
3. **Bo de Cohesió Territorial.** Les parades remotes (Pallars, Val d'Aran, Priorat…) tenen pocs passatgers però reben subvencions.
4. **Material rodant i flota.**
   - Trens: sèries 447, 444, 470, Civia 463/464/465, alta velocitat i mercaderies.
   - Autobusos: dièsel, híbrids, elèctrics i biarticulats, amb desgast, consum i impacte a la ZBE.
5. **Infraestructura.**
   - Tallers: Sant Andreu i Rubí.
   - Cotxeres: Triangle, Horta i Ponent.
   - Energia vinculada al preu real del mercat majorista (OMIE).
6. **Motor de caos.** Inclou aquests esdeveniments:
   - Robatori de coure (R3, R4, R8).
   - Atropellaments d'animals (FGC Vallès, línia de Puigcerdà).
   - Incidències a Garraf i a la costa (R2 Sud, R1).
   - Caos urbà (MWC, Sant Jordi, manifestacions, obres a Via Laietana, Marató).
   - Temporals i DANA (Ebre, Maresme).
7. **Social.**
   - Accionariat compartit a les estacions grans, amb repartiment de dividends.
   - Cooperatives de Mobilitat (clans) organitzades geogràficament.

## 4. Decisions tècniques

| Àmbit | Decisió |
|---|---|
| App | Android natiu: Kotlin, Jetpack Compose, Hilt, Room i DataStore, les mateixes peces que `municipis`. Paquet `cat.andana947`, `minSdk` 26. |
| Mòduls | Mateixa estructura que `municipis`: `app/` (Android), `joc/` (regles i matemàtiques en Kotlin pur, amb tests, sense SDK d'Android), `dades/` (dades generades i `configuracio_joc.json`, l'únic lloc amb els valors del joc), `scripts/` (ingesta de dades reals) i `supabase/`. |
| Mapa | **Pendent** (vegeu la secció 9). `municipis` dibuixa els límits amb Compose `Canvas`, sense mapa de base. Aquí el jugador camina pel carrer buscant parades, i cal un mapa de carrers. |
| Backend | **Supabase**, projecte propi (`tocvxpoyrvaabgpbfdxg`), no compartit. Les taules van a l'esquema `public`. |
| Base de dades | Postgres + **PostGIS** per a consultes geoespacials (radis, proximitat, municipis). |
| Autenticació | **Supabase Auth**, a la mateixa plataforma que la BD. Proveïdors: Google i correu electrònic. |
| Lògica de servidor | Edge Functions + `pg_cron` per al *tick* econòmic diari, els esdeveniments de caos i el preu OMIE. |
| Temps real | Supabase Realtime per a l'accionariat i les cooperatives. |
| Seguretat | Row Level Security a totes les taules. L'economia és **autoritativa al servidor**: el client mai calcula diners. A l'app només hi van la URL i la clau pública, llegides de `local.properties`. |
| Sense connexió | A diferència de `municipis`, el joc necessita connexió per a qualsevol acció econòmica. Room només fa de memòria cau (parades, línies, l'estat de l'imperi) perquè l'app obri ràpid. |
| Anti-trampes | Validació del *check-in* al servidor: distància, velocitat entre *check-ins* i Play Integrity API. |
| Migracions | Supabase CLI. L'esquema es versiona al repositori, a `supabase/migrations`. |

## 5. Dades reals

**Principi:** totes les dades han de ser reals. Quan no existeixi una dada oberta, es farà servir un model d'estimació documentat i marcat com a *estimat*.

Fonts previstes, a verificar a la fase 2:

- **GTFS:** TMB (Open Data, requereix app_id/app_key), FGC, Renfe (data.renfe.com), AMB i Mou-te / Generalitat.
- **Afluència:** Renfe (viatgers per estació), FGC, TMB (validacions per estació) i ATM.
- **Energia:** OMIE / Red Eléctrica (preu majorista).
- **Territori:** límits municipals i població (ICGC, Idescat).

**Limitació coneguda:** no hi ha afluència oberta per a cada parada de bus. Es reparteixen els viatgers de cada línia segons la població o l'activitat de l'entorn de cada parada.

**Marques:** es fan servir noms de línia i models de tren (informació factual), però no logotips oficials. La iconografia és pròpia, amb els colors de línia.

## 6. Idioma

- Tot el projecte s'escriu en **català**: app, documentació, missatges de commit i issues.
- Possible localització posterior a castellà i anglès.

## 7. Convencions de treball

- Un fil de conversa per *feature* o branca d'implementació.
- Branques amb el format `feature/<nom>`, `fix/<nom>` o `data/<nom>`.
- Tots els commits surten com a **polMarsol**, sense línies `Co-authored-by` ni cap altra coautoria.
- Les claus secretes (Supabase *service_role* / *secret*, contrasenya de la BD) **mai** es pugen al repositori ni passen pel xat.

## 8. Full de ruta

| Fase | Lliurable |
|---|---|
| 0. Configuració | Repositori a CimsProjects, projecte Supabase, convencions i CI bàsic |
| 1. GDD (la bíblia) | Fórmules d'ingressos, arbre tecnològic, taules del motor de caos, UX al carrer, simulació del primer mes |
| 2. Pipeline de dades | Ingesta GTFS a PostGIS, datasets d'afluència i model d'estimació |
| 3. Esquelet de l'app | Autenticació, mapa, tema visual derivat de `municipis` |
| 4. MVP jugable | Parades properes, *check-in*, conquesta de concessions, accions de les grans estacions (compra i dividends), ingrés diari |
| 5. Flota i línies | Compra de material rodant i autobusos, costos (Adif, OMIE, manteniment) |
| 6. Motor de caos | Esdeveniments aleatoris i calendari real d'esdeveniments urbans |
| 7. Social | Compravenda d'accions entre jugadors, juntes, cooperatives |
| 8. Balanç i beta | Ajust de l'economia amb jugadors reals |

## 9. Pendent de decidir

- [x] Nom del joc i de l'app: **Andana 947**
- [x] Nom del repositori: `CimsProjects/andana947`
- [x] Paquet de l'app: `cat.andana947`
- [x] Projecte de Supabase: propi, `tocvxpoyrvaabgpbfdxg`
- [ ] **Mapa.** Proposta: MapLibre amb un estil propi (blanc, negre i un color fort, com `municipis`) i teselles d'OpenStreetMap sense clau (per exemple OpenFreeMap). Alternativa: Google Maps amb estil personalitzat (necessita clau i compte de facturació).
- [ ] **Estètica.** Partim del sistema de `municipis` (`docs/disseny.md`: blanc i negre, un sol color fort, botons amb ombra dura, Archivo Black + Instrument Sans + Caveat). Cal triar el color fort d'Andana 947 perquè no es confongui amb el groc de Bocins.
- [ ] Abast exacte del MVP
