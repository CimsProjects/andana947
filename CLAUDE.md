# CLAUDE.md

Andana 947: joc Android de geolocalització i gestió econòmica (*tycoon*) del transport públic de Catalunya. El jugador conquereix parades i estacions reals fent *check-in* amb el GPS i les fa rendibles amb dades reals de passatgers.

## Llegeix primer

- `docs/abast.md`: l'abast del projecte i les decisions preses. **És la font de veritat.**
- `docs/supabase.md`: el servidor i les claus.
- `docs/vehicles.md`: els trens i els busos que es mouen pel mapa (horaris, posicions reals i 3D).
- Projecte germà: [CimsProjects/municipis](https://github.com/CimsProjects/municipis). En seguim l'arquitectura, les normes i l'estil.

## Normes

- **Idioma:** tot en **català**: textos de l'app (sempre a `strings.xml`, mai dins del codi), documentació, comentaris, missatges de commit i issues.
- **Dades reals:** no t'inventis cap parada, línia, xifra de passatgers, preu ni model de tren. Les dades surten dels scripts de `scripts/` a partir de fonts obertes. Si una dada no existeix, es fa un model d'estimació documentat i marcat com a *estimat*. Si falta una dada, deixa-ho indicat i pregunta.
- **Configuració del joc:** les regles i els valors (tarifes, costos, multiplicadors, radis, probabilitats del caos) en un sol lloc (`dades/configuracio_joc.json`) i amb tests.
- **Economia al servidor:** el client mai calcula ni envia diners. Les accions econòmiques passen per funcions de Supabase que ho validen tot.
- **Servidor:** només Supabase (`supabase-kt`). Mai la clau secreta (`service_role` / `sb_secret_…`) a l'app, al repositori ni al xat. Cap SDK d'analítica ni de publicitat.
- **Marques:** noms de línia i de material rodant sí; logotips oficials (TMB, Renfe, FGC, ATM…) no.
- **Abans d'acabar una tasca:** compila, i passa els tests i el lint.

## Git

- Una branca per *feature*: `feature/<nom>`, `fix/<nom>`, `data/<nom>`, `docs/<nom>`.
- Commits petits, amb missatge en català.
- **Autoria:** els commits surten amb l'autor de qui fa la feina (el seu usuari de GitHub), **sense cap línia `Co-authored-by`** ni cap altra atribució, ni als commits ni a les PR.

## Estructura

```
app/            projecte Android (Compose, Hilt, Room, DataStore)        — fase 3
joc/            regles i matemàtiques del joc en Kotlin pur, amb tests     — fase 1-2
dades/          dades generades i configuració del joc                     — fase 2
scripts/        ingesta de dades reals (GTFS, afluència, energia)          — fase 2
supabase/       migracions (esquema, RLS i funcions) i tests
docs/           abast, GDD i guies
```

## Supabase

```bash
npx supabase start && npx supabase test db   # local amb Docker; mai contra el projecte de producció
```
