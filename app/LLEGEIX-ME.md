# App Android

L'app d'Andana 947: Kotlin, Jetpack Compose, Hilt i DataStore, com a [municipis](https://github.com/CimsProjects/municipis). Paquet `cat.andana947`, `minSdk` 26.

## Compilar i provar

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Cal l'Android SDK (Android Studio o `ANDROID_HOME`) i Java 17 o posterior. La CI (`.github/workflows/ci.yml`) fa el mateix a les PR i als *push* a `main` que toquen l'app, `joc/`, `dades/` o Gradle, i deixa l'APK de prova com a artefacte (`app-debug`).

## Servidor

L'app llegeix la URL i la clau pública de Supabase de `local.properties`, que no es puja al repositori (`docs/supabase.md`):

```properties
supabase.url=https://tocvxpoyrvaabgpbfdxg.supabase.co
supabase.anonKey=<clau pública>
```

A la CI surten dels secrets `SUPABASE_URL` i `SUPABASE_ANON_KEY`. Mai la clau secreta.

**Sense servidor** (sense aquestes línies), l'app funciona igualment amb un compte local: s'hi entra amb qualsevol correu i contrasenya, i no es desa res. Serveix per provar l'esquelet des de l'Android Studio. El perfil ho indica.

## Què hi ha

| Peça | On | Notes |
|---|---|---|
| Tema | `ui/theme/` | Blanc i negre i el magenta `#FF3D7F` (`docs/abast.md`, secció 9). Clar i fosc segons el mòbil. Les tipografies (Archivo Black, Instrument Sans i Caveat) i les llicències (`assets/llicencies/`) són les de municipis. |
| Components | `ui/components/` | Botons amb vora i ombra dura que s'enfonsen en prémer, la marca i la pantalla d'obertura. |
| Compte | `data/compte/`, `compte/` | Supabase Auth amb correu i contrasenya. El compte és obligatori. Google arribarà quan hi hagi l'empremta SHA-1 de la signatura (`docs/supabase.md`). |
| Mapa | `mapa/` | MapLibre amb l'estil propi `assets/mapa/estil.json` i les teselles d'OpenStreetMap d'[OpenFreeMap](https://openfreemap.org), sense clau. |
| Trens | `mapa/CapaVehicles.kt`, `mapa/VehiclesViewModel.kt`, `data/vehicles/` | Els trens que circulen, com a punts de lluny i blocs 3D de prop (`docs/vehicles.md`). Els horaris (`circulacions.json`) surten de `dades/generades/app`, que és una carpeta d'assets. El càlcul és a `joc/`. |
| Ubicació | `data/ubicacio/` | Només en primer pla, mentre es mira el mapa. El permís es demana en obrir el mapa. |
| Navegació | `navegacio/` | Pestanyes Mapa, A prop, Imperi i Perfil (GDD, secció 7.1). A prop i Imperi esperen les dades i l'economia de les fases 2 i 4. |

## L'estil del mapa

`assets/mapa/estil.json` és un estil de MapLibre (esquema d'OpenMapTiles) amb els colors com a marques `{{nom}}`. `EstilMapa.genera` les omple amb la paleta clara o fosca, i `EstilMapaTest` comprova que no en quedi cap i que totes tinguin color.

- Carrers i edificis en grisos, l'aigua una mica més fosca, i les vies de tren, metro i tramvia en tinta (en túnel, ratllades i grises).
- Els límits municipals a partir del zoom 11.
- Els noms en català quan OpenStreetMap els té (`name:ca`).
- El jugador: un punt magenta amb vora de tinta i el cercle de precisió en metres reals. És l'única cosa magenta del mapa fins que hi hagi les parades del jugador.

Les lletres del mapa (Noto Sans) les serveix OpenFreeMap; l'atribució d'OpenStreetMap és al botó «i» del mapa i al perfil.

## Pendent per a les fases següents

- **Room**, com a memòria cau de parades, línies i l'estat de l'imperi, quan hi hagi dades (fases 2 i 4).
- **Postgrest i Realtime** de Supabase, quan hi hagi l'esquema (fase 4).
- **Avís de velocitat** en obrir el mapa (GDD, secció 7.4): el llindar de 10 km/h és un valor del joc i ha d'estar a `dades/configuracio_joc.json` (fase 2). `Posicio` ja porta la velocitat.
- **Tests d'interfície** amb emulador, com a municipis, quan hi hagi fluxos de joc.
