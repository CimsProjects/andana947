-- Extensions que necessita el joc.
-- PostGIS: posicions de les parades, radis de check-in i d'intermodalitat (100 m), municipis.
-- Es crea a l'esquema `extensions`, com recomana Supabase, i no a `public`.

create extension if not exists postgis with schema extensions;
