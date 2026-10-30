# Càrrega a PostGIS

Càrrega de prova a un Supabase local (`scripts/carrega_postgis.py`), feta per la CI.

```
BEGIN
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE TABLE
COPY 15
COPY 947
COPY 1181
COPY 13617
COPY 32082
INSERT 0 15
INSERT 0 947
INSERT 0 1181
INSERT 0 13617
DELETE 0
INSERT 0 32082
DELETE 0
DELETE 0
COMMIT
    taula    | files 
-------------+-------
 municipis   |   947
 linies      |  1181
 nodes       | 13617
 linia_nodes | 32082
 fonts_dades |    15
(5 rows)


Nodes a menys de 250 m de Baró de Viver (L1):
          id           |                  nom                  | mode | viatgers | font_afluencia | metres 
-----------------------+---------------------------------------+------+----------+----------------+--------
 bus:tmb:2.3656.695226 | Sant Adrià - Ciutat d'Asunción        | bus  |        9 | estimat        |     20
 bus:tmb:2.1436.692554 | Ciutat d'Asunción - Sant Adrià        | bus  |      494 | estimat        |     75
 bus:tmb:2.2592.676615 | Ciutat d'Asunción - Fra Juníper Serra | bus  |       12 | estimat        |     85
 bus:tmb:2.360.674552  | Sant Adrià - Formiga                  | bus  |       10 | estimat        |     95
 bus:tmb:2.2599.682508 | Fra Juníper Serra - Formiga           | bus  |       13 | estimat        |    230
(5 rows)

Nodes per mode i font de l'afluència:
   mode   | font_afluencia | count | viatgers_feiner 
----------+----------------+-------+-----------------
 metro    | real           |   138 |         1482387
 rodalies | estimat        |   132 |          397577
 regional | estimat        |    61 |            5738
 fgc      | real           |    95 |          304803
 fgc      | estimat        |     1 |               0
 tram     | estimat        |    55 |          109718
 bus      | estimat        | 13135 |         1591566
(7 rows)

```
