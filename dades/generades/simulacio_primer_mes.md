# Simulació del primer mes (fase 2): la Laia, amb parades de bus

La Laia viu al Bon Pastor i treballa a prop de Diagonal. Cada feiner fa check-in a Baró de Viver, Clot-Aragó, Passeig de Gràcia i Diagonal, i a les tres parades de bus més properes a Baró de Viver i a Diagonal (a menys de 250 m). Els dimarts passa per Arc de Triomf, els dimecres per Sants, els divendres per Plaça de Catalunya i Catalunya, els dissabtes per Espanya, i els dies 12 i 24 visita Casa de l'Aigua. Comença el dilluns 2 de novembre de 2026 amb el capital inicial. Sense caos ni altres accionistes. Afluència: dades/generades/nodes.csv.

## Nodes

| Node | Mode | Viatgers/feiner | Afluència | Ingrés sencer/dia | Forma | Preu |
|---|---|---:|---|---:|---|---:|
| Passeig de Gràcia, 104 (marquesina urbana) (bus L1624) | Bus | 0 | estimat | 0,05 € | Concessió | 10,00 € |
| Pg. Salzereda - Barri Riu Nord (bus B15) | Bus | 37 | estimat | 1,79 € | Concessió | 17,92 € |
| Pg Santa Coloma - Tiana (bus 60, M19, M28, N9) | Bus | 221 | estimat | 5,26 € | Concessió | 52,56 € |
| Casa de l'Aigua (L11) | Metro | 236 | real | 5,46 € | Concessió | 54,62 € |
| Pg de Gràcia - Rosselló (bus 22, 24, N24) | Bus | 386 | estimat | 7,34 € | Concessió | 73,42 € |
| Pg Santa Coloma - Baró de Viver (bus 60, M19, M28, N9…) | Bus | 391 | estimat | 7,40 € | Concessió | 73,98 € |
| Baró de Viver (L1) | Metro | 1.609 | real | 17,29 € | Concessió | 172,92 € |
| Pg de Gràcia - Diagonal (bus 22, 24, 7, N24…) | Bus | 1.660 | estimat | 17,62 € | Concessió | 176,18 € |
| Arc de Triomf (Rodalies) | Rodalies | 11.972 | estimat | 57,66 € | Concessió | 576,55 € |
| Clot-Aragó (Rodalies) | Rodalies | 15.026 | estimat | 66,08 € | Concessió | 660,78 € |
| Passeig de Gràcia (Rodalies) | Rodalies | 25.182 | estimat | 90,07 € | Accions | 9,01 €/acció |
| Plaça de Catalunya (Rodalies) | Rodalies | 30.207 | estimat | 100,46 € | Accions | 10,05 €/acció |
| Espanya (L1, L3) | Metro | 44.249 | real | 126,32 € | Accions | 12,63 €/acció |
| Catalunya (L1, L3) | Metro | 50.882 | real | 137,37 € | Accions | 13,74 €/acció |
| Diagonal (L3, L5) | Metro | 54.544 | real | 143,22 € | Accions | 14,32 €/acció |
| Sants (Rodalies) | Rodalies | 56.573 | estimat | 146,39 € | Accions | 14,64 €/acció |

## Evolució

| Dia | Data | Ingrés del dia | Saldo | Concessions | Accions | Nivell |
|---:|---|---:|---:|---:|---:|---:|
| 1 | dl. 2/11 | 19,99 € | 20,05 € | 1 | 3 | 4 |
| 7 | dg. 8/11 | 23,21 € | 51,82 € | 1 | 15 | 4 |
| 14 | dg. 15/11 | 44,79 € | 96,57 € | 2 | 36 | 4 |
| 21 | dg. 22/11 | 83,52 € | 215,64 € | 4 | 66 | 5 |
| 30 | dt. 1/12 | 247,00 € | 414,89 € | 9 | 86 | 6 |

En tot el període ingressa 2.560,20 € (986,95 € de concessions i 1.573,25 € de dividends) i n'inverteix 2.345,30 €. Acaba amb un saldo de 414,89 €, 1127 punts de prestigi (nivell 6).

Accions finals: Passeig de Gràcia (Rodalies) 20, Diagonal (L3, L5) 20, Espanya (L1, L3) 16, Sants (Rodalies) 10, Plaça de Catalunya (Rodalies) 10, Catalunya (L1, L3) 10.

Concessions: Baró de Viver (L1) (renovada 4 vegades), Passeig de Gràcia, 104 (marquesina urbana) (bus L1624) (renovada 3 vegades), Pg. Salzereda - Barri Riu Nord (bus B15) (renovada 2 vegades), Pg Santa Coloma - Baró de Viver (bus 60, M19, M28, N9…) (renovada 1 vegada), Pg Santa Coloma - Tiana (bus 60, M19, M28, N9) (renovada 1 vegada), Pg de Gràcia - Rosselló (bus 22, 24, N24) (renovada 1 vegada), Pg de Gràcia - Diagonal (bus 22, 24, 7, N24…) (renovada 1 vegada), Casa de l'Aigua (L11), Clot-Aragó (Rodalies).

Intermodalitat Perfecta: Passeig de Gràcia, 104 (marquesina urbana) (bus L1624) ×1,25, Diagonal (L3, L5) ×1,25, Pg de Gràcia - Rosselló (bus 22, 24, N24) ×1,25, Pg de Gràcia - Diagonal (bus 22, 24, 7, N24…) ×1,25.

## Detall diari

| Dia | Data | Ingrés | Saldo | Fets |
|---:|---|---:|---:|---|
| 1 | 2026-11-02 | 19,99 € | 20,05 € | conquereix Baró de Viver (L1); +3 acc. Passeig de Gràcia (Rodalies) |
| 2 | 2026-11-03 | 21,80 € | 23,83 € | +2 acc. Passeig de Gràcia (Rodalies) |
| 3 | 2026-11-04 | 23,60 € | 29,42 € | +2 acc. Passeig de Gràcia (Rodalies) |
| 4 | 2026-11-05 | 26,30 € | 28,69 € | +3 acc. Passeig de Gràcia (Rodalies) |
| 5 | 2026-11-06 | 29,00 € | 30,67 € | +3 acc. Passeig de Gràcia (Rodalies) |
| 6 | 2026-11-07 | 23,21 € | 28,61 € | +2 acc. Espanya (L1, L3) |
| 7 | 2026-11-08 | 23,21 € | 51,82 € |  |
| 8 | 2026-11-09 | 36,03 € | 42,81 € | renova Baró de Viver (L1); +5 acc. Passeig de Gràcia (Rodalies) |
| 9 | 2026-11-10 | 39,32 € | 39,80 € | +2 acc. Passeig de Gràcia (Rodalies); +1 acc. Diagonal (L3, L5); conquereix Passeig de Gràcia, 104 (marquesina urbana) (bus L1624) |
| 10 | 2026-11-11 | 42,18 € | 53,33 € | +2 acc. Diagonal (L3, L5) |
| 11 | 2026-11-12 | 48,64 € | 59,01 € | +3 acc. Diagonal (L3, L5) |
| 12 | 2026-11-13 | 55,80 € | 57,52 € | +4 acc. Diagonal (L3, L5) |
| 13 | 2026-11-14 | 44,79 € | 51,78 € | +4 acc. Espanya (L1, L3) |
| 14 | 2026-11-15 | 44,79 € | 96,57 € |  |
| 15 | 2026-11-16 | 71,60 € | 78,63 € | renova Baró de Viver (L1); +5 acc. Diagonal (L3, L5); conquereix Pg. Salzereda - Barri Riu Nord (bus B15) |
| 16 | 2026-11-17 | 80,55 € | 87,57 € | +5 acc. Diagonal (L3, L5); renova Passeig de Gràcia, 104 (marquesina urbana) (bus L1624) |
| 17 | 2026-11-18 | 87,87 € | 102,24 € | +5 acc. Sants (Rodalies) |
| 18 | 2026-11-19 | 95,26 € | 123,53 € | conquereix Pg Santa Coloma - Baró de Viver (bus 60, M19, M28, N9…) |
| 19 | 2026-11-20 | 107,16 € | 111,77 € | +5 acc. Plaça de Catalunya (Rodalies); +5 acc. Catalunya (L1, L3) |
| 20 | 2026-11-21 | 83,52 € | 132,12 € | +5 acc. Espanya (L1, L3) |
| 21 | 2026-11-22 | 83,52 € | 215,64 € |  |
| 22 | 2026-11-23 | 127,91 € | 217,56 € | renova Baró de Viver (L1); conquereix Pg Santa Coloma - Tiana (bus 60, M19, M28, N9); renova Pg. Salzereda - Barri Riu Nord (bus B15); conquereix Pg de Gràcia - Rosselló (bus 22, 24, N24) |
| 23 | 2026-11-24 | 149,93 € | 191,31 € | renova Passeig de Gràcia, 104 (marquesina urbana) (bus L1624); conquereix Pg de Gràcia - Diagonal (bus 22, 24, 7, N24…) |
| 24 | 2026-11-25 | 162,71 € | 226,21 € | +5 acc. Sants (Rodalies); conquereix Casa de l'Aigua (L11) |
| 25 | 2026-11-26 | 162,71 € | 388,92 € | renova Pg Santa Coloma - Baró de Viver (bus 60, M19, M28, N9…) |
| 26 | 2026-11-27 | 174,60 € | 444,61 € | +5 acc. Plaça de Catalunya (Rodalies); +5 acc. Catalunya (L1, L3) |
| 27 | 2026-11-28 | 133,16 € | 514,60 € | +5 acc. Espanya (L1, L3) |
| 28 | 2026-11-29 | 133,16 € | 647,76 € |  |
| 29 | 2026-11-30 | 180,92 € | 828,68 € | renova Baró de Viver (L1); renova Pg Santa Coloma - Tiana (bus 60, M19, M28, N9); renova Pg. Salzereda - Barri Riu Nord (bus B15); renova Pg de Gràcia - Rosselló (bus 22, 24, N24) |
| 30 | 2026-12-01 | 247,00 € | 414,89 € | conquereix Clot-Aragó (Rodalies); renova Passeig de Gràcia, 104 (marquesina urbana) (bus L1624); renova Pg de Gràcia - Diagonal (bus 22, 24, 7, N24…) |
