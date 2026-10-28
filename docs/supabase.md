# Supabase

El servidor del joc és un projecte de Supabase propi, no compartit amb cap altra app: base de dades (Postgres + PostGIS), autenticació, funcions i temps real.

| | |
|---|---|
| Projecte | `andana947` |
| Referència | `tocvxpoyrvaabgpbfdxg` |
| URL | `https://tocvxpoyrvaabgpbfdxg.supabase.co` |

## Claus

| Clau | On va | Es pot publicar? |
|---|---|---|
| URL del projecte | App (`local.properties`) i secrets de la CI | Sí |
| Clau pública (`anon` o `sb_publishable_…`) | App (`local.properties`) i secrets de la CI | Sí: és pública per disseny; la seguretat la posa l'RLS |
| Clau secreta (`service_role` o `sb_secret_…`) | Només scripts de servidor i Edge Functions | **Mai.** Ni al repositori, ni a l'app, ni al xat |
| Contrasenya de la base de dades | Gestor de contrasenyes i `supabase link` | **Mai** |

Tot i que la clau pública es pot publicar, no es posa al codi: es llegeix de `local.properties` (que no es puja) a través de `BuildConfig`, com a `municipis`.

```properties
# local.properties (no es puja al repositori)
supabase.url=https://tocvxpoyrvaabgpbfdxg.supabase.co
supabase.anonKey=<clau anon>
```

## Configuració del projecte

### Fet

- [x] Crear el projecte.
- [x] Guardar la contrasenya de la base de dades al gestor de contrasenyes.

### Per fer al tauler de Supabase

- [x] **PostGIS.** El crea la primera migració (`20261028140000_extensions.sql`). Si ja s'ha activat a mà a *Database → Extensions*, la migració no fa res.
- [x] **Correu.** *Authentication → Sign In / Providers → Email*: activat, amb *Confirm email* desactivat (com a `municipis`: el servidor de correu de Supabase només permet uns quants correus per hora).
- [ ] **SMTP propi** (més endavant). El correu del projecte és `cimsequip@gmail.com`. Quan calgui verificar correus o recuperar contrasenyes, es configura a *Authentication → Emails → SMTP Settings* amb el servidor de Gmail (`smtp.gmail.com`, port 465) i una **contrasenya d'aplicació** de Google (no la del compte), i aleshores es torna a activar *Confirm email*.
- [ ] **Google.** Es configura a la fase 3, quan hi hagi l'app: cal el paquet (`cat.andana947`) i l'empremta SHA-1 de la signatura per crear les credencials a Google Cloud Console.
- [ ] **URL de redirecció.** *Authentication → URL Configuration*: afegir `cat.andana947://login/**` quan l'app faci servir enllaços per correu.

## Migracions

L'esquema, les polítiques RLS i les funcions són migracions a `supabase/migrations/`, amb tests a `supabase/tests/`. No es crea cap taula a mà des del tauler.

```bash
npx supabase start          # Supabase local (cal Docker)
npx supabase db reset       # aplica totes les migracions al local
npx supabase test db        # tests de l'esquema

npx supabase login          # una sola vegada per ordinador: obre el navegador i desa el token
npx supabase link --project-ref tocvxpoyrvaabgpbfdxg      # demana la contrasenya de la BD
npx supabase db push --linked   # aplica les migracions noves al projecte
npx supabase migration list --linked   # comprova que local i remot coincideixen
```

Si `link` diu *Access token not provided*, falta el `login`. En un ordinador sense navegador, es crea un token a *Account → Access Tokens* del tauler i es fa servir amb `SUPABASE_ACCESS_TOKEN` (mai al repositori).

Els tests es passen sempre en local, mai contra el projecte de producció.
