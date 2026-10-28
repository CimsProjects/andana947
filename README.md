# Andana 947

Joc Android de geolocalització i gestió econòmica ambientat al transport públic de Catalunya. Comences com un petit operador de mobilitat local: camines fins a una parada o estació real, hi fas *check-in* amb el GPS i n'aconsegueixes la concessió. Cada concessió genera ingressos segons els passatgers reals que hi passen. Amb els beneficis compres autobusos i trens, obres línies i t'enfrontes al caos de cada dia: robatoris de coure, temporals, el Mobile o la Marató.

Del bus del barri fins al primer tren regional, per tots els 947 municipis.

## Estat

En disseny. L'abast i el full de ruta són a [`docs/abast.md`](docs/abast.md).

| Fase | Estat |
|---|---|
| 0. Configuració | Fet |
| 1. GDD | Pendent |
| 2. Pipeline de dades | Pendent |
| 3. Esquelet de l'app | Pendent |

## Estructura

| Carpeta | Contingut |
|---|---|
| `docs/` | Abast, GDD i guies (`supabase.md`). |
| `supabase/` | Migracions de la base de dades (esquema, RLS i funcions) i els seus tests. |

Les carpetes `app/`, `joc/`, `dades/` i `scripts/` arribaran amb les fases següents, amb la mateixa organització que [municipis](https://github.com/CimsProjects/municipis).

## Fonts de dades

Totes les dades del joc són reals i surten de fonts obertes. La llista, amb les llicències, s'omplirà a la fase 2.
