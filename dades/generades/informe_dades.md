# Informe de les dades generades

Generat el 29/10/2026 amb `scripts/genera_dades.py` (model d'afluència v1).

## Territori

- 947 municipis; 6388 seccions censals de l'INE, 1320 sense població al Cens 2021 (estimades amb la densitat del municipi).
- Població total (Cens 2021): 17.947.630 habitants.

## Xarxa (GTFS)

- gtfs_tmb: dia de referència 2026-11-04, 117 línies al feed, 115 importades; 107 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_fgc: dia de referència 2026-11-04, 22 línies al feed, 18 importades; 0 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_rodalies: dia de referència 2026-11-04, 275 línies al feed, 62 importades; 0 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_renfe_md: dia de referència 2026-11-04, 668 línies al feed, 51 importades; 0 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_trambaix: dia de referència 2026-11-04, 3 línies al feed, 2 importades; 0 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_trambesos: dia de referència 2026-11-04, 3 línies al feed, 3 importades; 0 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_amb: dia de referència 2026-11-04, 140 línies al feed, 134 importades; 6 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- gtfs_interurbans: dia de referència 2026-11-04, 948 línies al feed, 796 importades; 95 parades-línia sense servei aquell dia, comptades amb un dimecres posterior
- Parades de bus: 14640 abans de fusionar els dos sentits i els operadors, 13126 després.
- Nodes per mode: bus 13126, fgc 96, metro 138, regional 61, rodalies 132, tram 55.
- Línies AMB (bus): 87, 88, 89, A1, A2, B2, B3, B4, B5, B7, B8, JM, JT, M1, M5, M6, N0, N1, N2, N3, N4, N5, N6, N7, N8, N9, B14, B15, B18, B20, B21, B23, B24, B25, B29, B31, B32, B80, B81, B82, CF1, CF2, CS1, CS2, CS3, CS4, CV1, CV2, CV3, CV4, CV5, EP1, EP2, ESC, GA1, GA2, L16, L20, L21, L22 … (134 en total)
- Línies FGC (fgc): L6, L7, L8, R5, R6, S1, S2, S3, S4, S8, S9, L12, R50, R53, R60, R63, RL1, RL2
- Línies Generalitat de Catalunya (bus): L0008, L0011, L0015, L0020, L0022, L0023, L0024, L0025, L0026, L0027, L0035, L0036, L0038, L0039, L0040, L0047, L0050, L0051, L0055, L0056, L0057, L0058, L0062, L0063, L0066, L0067, L0069, L0075, L0076, L0077, L0078, L0082, L0084, L0085, L0087, L0089, L0091, L0092, L0095, L0096, L0098, L0101, L0103, L0105, L0109, L0110, L0111, L0115, L0119, L0121, L0123, L0125, L0127, L0128, L0129, L0131, L0134, L0136, L0140, L0145 … (796 en total)
- Línies Renfe (regional): MD, R11, R13, R14, R15, R16, R17, RG1, RL3, RL4, RT1, RT2, REG.EXP., REGIONAL
- Línies Renfe (rodalies): R1, R2, R3, R4, R7, R8, R2N, R2S
- Línies TMB (bus): 6, 7, 13, 19, 21, 22, 23, 24, 27, 33, 34, 39, 46, 47, 52, 54, 55, 59, 60, 62, 63, 65, 67, 68, 70, 76, 78, 91, 94, 95, 96, 97, H2, H4, H6, H8, V1, V3, V5, V7, V9, X1, X2, X3, 104, 107, 109, 111, 112, 113, 114, 115, 117, 118, 119, 120, 121, 122, 123, 124 … (105 en total)
- Línies TMB (metro): L1, L2, L3, L4, L5, L11, L9N, L9S, L10N, L10S
- Línies TRAM (tram): T1, T3, T4, T5, T6

## Afluència

- **Metro (TMB 2025, real):** 171 parelles línia-estació al full de TMB, 138 de 138 estacions amb dada.
  - Calibratge del factor no feiner (GDD 4.4): el metro sencer suma 444.243.001 entrades l'any i 1.371.672 en un feiner mitjà (323.9 dies equivalents; mediana per estació. 321.0). Factor calibrat: **0.639** (a la configuració: 0.6).
- **FGC (2024, real):** 95 de 96 estacions amb dada.
  - Sense aparellar: CARRETERA DE LES AIGÜES, VALLVIDRERA SUPERIOR
  - Estacions del GTFS sense dada: Can Feu - Gràcia
- **Rodalies (estimat. GDD 2.2):** 123 estacions al fitxer de Renfe del 2018. 116 aparellades pel codi d'estació. Factor de xarxa f = 117.500.000 / 116.000.000 = **1.0129**.
  - Factor per línia: pendent. Les sèries per línia (ATM / OMC) no es poden baixar des de la CI (el servidor de l'OMC hi respon 404); de moment, un sol factor per a tota la xarxa, de la mateixa sèrie de l'Idescat. Amb un sol factor, totes les estacions creixen igual i es compleixen les comprovacions (a) i (b) del GDD 2.2.
  - Del fitxer del 2018 sense estació al GTFS: GRANOLLERS-CANOVELLES, LATOUR DE CAROL, LES FRANQUESES DEL VALLES, MOLLET-SANTA ROSA, MONTCADA-RIPOLLET, PARETS DEL VALLES, SANTA PERPETUA DE MOGODA
  - Comprovació pujats ≈ baixats (estacions de més de 1.000): diferència mediana 6.3%.
- **Regionals (estimat. GDD 2.2):** 11.300.000 viatgers el 2024. 35.379 en un feiner. repartits entre 93 estacions.
  - Calibratge de l'exponent de població amb les 116 estacions de Rodalies amb dada: **β = 0.46** (a la configuració: 0.5).
  - Estacions de Renfe sense cap estimació: Santa Perpètua de Mogoda Riera de Caldes
- **Bus TMB (real per línia, estimat per parada, GDD 2.3):** 109 línies al fitxer del 2025, 101 de 105 aparellades amb el GTFS.
  - Del fitxer sense línia al GTFS: 102, 116, 135, 155, 197, 198, 199, 79
  - Línies del GTFS sense dada (s'estimen amb l'ocupació mitjana): 137, 138, 978, 997
  - Ocupació mitjana d'una expedició de bus urbà (TMB): 49.6 viatgers.
- **Bus interurbà (estimat. GDD 2.3):** 67.262.744 viatgers el 2018 (Idescat) i 17.995 expedicions en un feiner al GTFS: ocupació mitjana 11.7 viatgers per expedició.
- **Bus AMB (estimat, GDD 2.3):** ocupació mitjana del bus urbà de TMB × expedicions de cada línia.
- **TRAM (estimat. GDD 2.3):** 35.044.000 viatgers el 2024. 109.718 en un feiner. repartits entre 55 parades de 5 línies (no hi ha dada oberta per línia).

## Comprovacions

- Nodes sense afluència: 2 de 13608.
- Grans estacions (≥ 20.000 viatgers en un feiner): 22: Barcelona-Sants (rodalies. 56.575), Diagonal (metro. 54.544), Catalunya (metro. 50.882), Espanya (metro. 44.249), Barcelona - Plaça Catalunya (fgc. 38.599), La Sagrera (metro. 38.320), Sagrada Família (metro. 37.583), Sants Estació (metro. 33.965), Passeig de Gràcia (metro. 32.714), Barcelona Plaça de Catalunya (rodalies. 30.207), Paral·lel (metro. 26.411), Universitat (metro. 25.859), Barcelona-Passeig de Gràcia (rodalies. 25.183), Collblanc (metro. 24.956), Hospital Clínic (metro. 24.552), Fabra i Puig (metro. 24.479), Verdaguer (metro. 24.096), Provença (fgc. 23.914), Urquinaona (metro. 23.681), Glòries (metro. 23.017), Clot (metro. 21.464), Liceu (metro. 21.182).
- Control: Baró de Viver (metro) 1.609 viatgers/feiner (real).
- Control: Casa de l'Aigua (metro) 236 viatgers/feiner (real).
- Control: Catalunya (metro) 50.882 viatgers/feiner (real).
- Control: Barcelona - Plaça Catalunya (fgc) 38.599 viatgers/feiner (real).
- Control: Barcelona Plaça de Catalunya (rodalies) 30.207 viatgers/feiner (estimat).
- Control: Sants Estació (metro) 33.965 viatgers/feiner (real).
- Control: Plaça de Sants (metro) 18.986 viatgers/feiner (real).
- Control: Barcelona-Sants (rodalies) 56.575 viatgers/feiner (estimat).

