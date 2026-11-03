# Publicar a Google Play

Com es fa el paquet de l'app (AAB) i què cal omplir a la Play Console.

## 1. La clau de pujada (un sol cop)

Google signa l'app amb la seva clau (Play App Signing). Nosaltres només signem el paquet que pugem amb una **clau de
pujada**. Es crea un cop, fora del repositori:

```bash
keytool -genkeypair -v -keystore ~/claus/andana947-pujada.jks -alias pujada \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Guarda el fitxer `.jks` i la contrasenya en un lloc segur (un gestor de contrasenyes). **Mai al repositori** (el
  `.gitignore` ja exclou `*.jks`).
- Si es perd, no és greu: Google la pot canviar des de la Play Console (Integritat de l'app → Signatura).

## 2. local.properties

Al costat de la URL i la clau pública de Supabase (docs/supabase.md):

```properties
supabase.url=https://tocvxpoyrvaabgpbfdxg.supabase.co
supabase.anonKey=sb_publishable_…
signatura.fitxer=/home/<tu>/claus/andana947-pujada.jks
signatura.alies=pujada
signatura.contrasenya=…
versio.codi=2
```

`versio.codi` ha de **créixer a cada pujada** (Google rebutja un número repetit). El nom de la versió surt sol:
`0.1.<versio.codi>`.

## 3. Fer el paquet

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Comprova que hi ha `supabase.url` i `supabase.anonKey`: sense, el paquet surt en mode «sense servidor» i no serveix.

## 4. Play Console

**Crea l'aplicació:** nom «Andana 947», idioma català, **Joc**, **Gratuïta**. Gratuïta és per sempre: després no es pot
passar a de pagament.

**Configura l'aplicació** (Tauler):

| Apartat | Resposta |
|---|---|
| Política de privadesa | `https://github.com/CimsProjects/andana947/blob/main/docs/privacitat.md` |
| Accés a l'aplicació | Cal compte: crea un compte de prova per als revisors de Google i dona'n el correu i la contrasenya |
| Anuncis | No conté anuncis |
| Classificació de contingut | Qüestionari IARC, categoria Joc: sense violència, sexe, drogues ni apostes. Hi ha interacció entre usuaris (noms de jugador i cooperatives), sense xat |
| Públic objectiu | 13 anys o més (no adreçat a infants) |
| Aplicació de notícies | No |
| Aplicacions governamentals | No |
| Funcions financeres | Cap (els diners són del joc) |
| Salut | No |

**Seguretat de les dades** (ha de coincidir amb docs/privacitat.md):

- Es recullen dades: **Sí**. Es comparteixen amb tercers: **No** (Supabase és un encarregat del tractament, no compta
  com a compartir). Xifrades en trànsit: **Sí**. L'usuari pot demanar que s'esborrin: **Sí**, amb l'URL de la política
  (secció «Esborrar el compte»).
- **Ubicació precisa** — recollida, no compartida, obligatòria; per a *Funcionalitat de l'aplicació* i *Prevenció de
  frau*.
- **Informació personal → Adreça electrònica** — recollida, obligatòria; per a *Gestió del compte*.
- **Informació personal → ID d'usuari** — recollida, obligatòria; per a *Funcionalitat* i *Gestió del compte*.
- **Activitat a l'aplicació → Altre contingut generat per l'usuari** (nom de jugador, cooperatives) — recollida,
  opcional; per a *Funcionalitat*.

**Fitxa de Play Store:** descripció breu (80 caràcters) i completa (4.000), icona de 512×512, gràfic de funcions de
1024×500 i com a mínim 2 captures del mòbil. Sense logotips oficials de TMB, Renfe, FGC ni ATM (CLAUDE.md).

## 5. Proves abans de producció

Els comptes de desenvolupador personals nous han de fer una **prova tancada amb 12 persones durant 14 dies** abans de
poder publicar a producció.

1. **Prova interna** (fins a 100 persones, sense revisió llarga): puja-hi el primer AAB per provar-lo amb el Martí.
2. **Prova tancada**: una llista de correus de 12 persones o més, que s'hi han d'apuntar amb l'enllaç i tenir l'app
   instal·lada 14 dies seguits.
3. Després, **Sol·licita l'accés a producció** des del Tauler.

## Pendent per a la beta pública

- Activar R8 (`isMinifyEnabled = true`) amb les regles de supabase-kt, kotlinx.serialization i MapLibre, provat al mòbil.
- Play Integrity per al *check-in* (docs/seguretat.md).
