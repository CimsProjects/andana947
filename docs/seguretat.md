# Seguretat i rendiment del servidor

Revisió feta l'1/11/2026, abans de la beta (fase 8), sobre totes les migracions de `supabase/migrations/` en una base
de dades local amb les dades reals. Les correccions són a `20261101000000_seguretat_rendiment.sql` i les comprovacions
que han de continuar sent certes, a `supabase/tests/seguretat.test.sql`.

## Model de seguretat

- **L'app no escriu cap taula.** Cap taula del joc té permisos d'escriptura per a `anon` ni `authenticated`, i totes
  tenen RLS. Tot canvi passa per una funció del servidor (`SECURITY DEFINER`, `search_path` buit) que comprova qui la
  crida (`auth.uid()`) i ho valida tot. El client mai no calcula ni envia diners (CLAUDE.md).
- **Lectura.** Cada jugador llegeix només el seu (perfil, andanes, moviments, check-ins, maestries, flota, millores).
  Són públics les dades de la xarxa, el catàleg, els esdeveniments i la part social que s'ha de veure (accionistes,
  ofertes, propostes i vots, cooperatives i membres), on el jugador surt amb el seu identificador o el nom públic.
- **Funcions internes.** Les que paguen, sumen experiència, sortegen o liquiden (`liquida_jugador`, `suma_xp`,
  `puja_nivells`, `sorteja_esdeveniments`, `liquida_tots`…) no les pot cridar cap client.
- **Mode desenvolupador.** `perfils.mode_dev` només es canvia des del servidor. Les eines `dev_*` el comproven, i els
  comptes de desenvolupador no surten al rànquing.

## Què s'ha trobat i s'ha arreglat

| Punt | Risc | Arreglat |
|---|---|---|
| `estat_imperi` i `fer_checkin` es podien cridar sense sessió | Baix: tornaven error | Només amb sessió |
| La funció del trigger de registre es podia cridar | Cap (un trigger no es pot cridar directament) | Treta |
| Quatre funcions sense `search_path` fix | Advertència del linter de Supabase | Fixat |
| Política que deixava actualitzar el perfil propi | Latent: sense permís d'escriptura no feia res, però si algun dia se'n donava, permetia canviar el saldo | Treta |
| Noms de cooperativa amb qualsevol caràcter | Noms ofensius o enganyosos amb símbols | Lletres, xifres i espais, com els noms de jugador |
| Juntes: en aprovar una millora, els saldos dels qui la paguen no estaven bloquejats | Una compra simultània podia fer fallar l'aprovació | Es bloquegen en ordre |
| Comptes de desenvolupador al rànquing | Compres gratis: rànquing injust | Fora del rànquing |
| `cobra_arribades` cridada en bucle | Càrrega del servidor | Com a molt un cobrament cada 20 segons per jugador |

## Riscos coneguts que queden

- **Diners entre comptes del mateix jugador.** Al mercat, un compte pot vendre accions car a un altre seu. Està
  acotat: el preu ha de ser entre la meitat i el triple del de referència, cap compte no pot tenir més de 20 accions
  d'una estació i el venedor paga un 2 %. Si es veu abús, es pot baixar el màxim o limitar les vendes per setmana.
- **Ubicació falsa al check-in.** El servidor comprova la precisió, la velocitat entre check-ins i la distància (GDD
  7.3), però una app d'ubicació falsa ho pot enganyar. Play Integrity (GDD 7.4) queda per a la beta pública.
- **Límit de peticions.** Supabase limita l'autenticació; les crides a funcions, no. Si cal, es pot posar un límit per
  jugador a les funcions que escriuen, com a `cobra_arribades`.
- **Còpies de seguretat.** El pla de Supabase ha de tenir còpies diàries abans d'obrir la beta.

## Rendiment

Prova amb **500 jugadors simulats** sobre les dades reals (13.617 nodes, 30.428 trams, 1.081 línies amb flota), cada un
amb 30 andanes (24 de bus i 6 de tren o metro), la meitat amb un vehicle i alguns amb accions:

| Crida | Abans | Ara |
|---|---|---|
| `liquida_tots()` (cron, cada hora; 1 h endarrerida) | 9,7 s | 4,8 s (≈10 ms per jugador) |
| `estat_flota()` (pantalla Flota) | 830 ms | 15 ms |
| `estat_imperi()` | 30 ms | 16 ms |
| `cobra_arribades()` (cada minut amb l'app oberta) | 17 ms | 8–9 ms |
| `estat_temporada()`, `estat_maestries()` | 20–45 ms | 40–45 ms |

- El temps d'`estat_flota` era el **compilador JIT** de Postgres, que en consultes petites tarda més a compilar que a
  executar-les. Les funcions del joc el desactiven (`set jit = off`).
- L'experiència de maestria d'un cobrament se suma ara d'una sola consulta (abans, una per andana i línia).
- A 5.000 jugadors, `liquida_tots` trigaria uns 50 segons cada hora; si cal, es pot repartir per grups de jugadors.
  Amb 500 jugadors amb l'app oberta, `cobra_arribades` suposa uns 75 ms de servidor per segon.
