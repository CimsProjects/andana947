-- Tests dels trams i les monedes (GDD 4.0): compra, saldo, arribades i RLS. S'executen amb `npx supabase test db`.
-- Els trams i els jugadors són de prova (no són del joc).

begin;
create extension if not exists pgtap with schema extensions;

select plan(28);

select has_table('public', 'trams', 'Hi ha la taula de trams');
select has_table('public', 'partides', 'Hi ha la taula de partides');
select has_table('public', 'trams_jugador', 'Hi ha la taula de trams dels jugadors');
select has_table('public', 'moviments', 'Hi ha el llibre de moviments');
select is(
    (select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relrowsecurity
       and c.relname in ('configuracio_joc', 'trams', 'temporades', 'partides', 'trams_jugador', 'moviments')),
    6,
    'Totes les taules del joc tenen l''RLS activat'
);
select isnt(public.temporada_actual(), null, 'Hi ha una temporada oberta');

-- La configuració, com la carrega scripts/carrega_postgis.py (només els blocs que fan servir les funcions).
insert into public.configuracio_joc (id, valors) values (1, '{
    "calendari": {"factorNoFeiner": 0.6},
    "trams": {"recompensaPetitMinima": 1, "recompensaPetitMaxima": 5, "recompensaMitjaMinima": 5,
              "recompensaMitjaMaxima": 10, "capitalInicial": 500, "horaIniciServei": 5, "horaFiServei": 24}
}') on conflict (id) do update set valors = excluded.valors;

insert into public.trams (id, operador, mode, codi, nom, parades, arribades_feiner, mida, preu) values
    ('prova:petit', 'Prova', 'bus', 'P1', 'Tram petit', 5, 57, 'petit', 300),
    ('prova:car', 'Prova', 'bus', 'P2', 'Tram petit car', 9, 90, 'petit', 1080),
    ('prova:mitja', 'Prova', 'bus', 'M1', 'Tram mitjà', 20, 300, 'mitja', 3000),
    ('prova:gran', 'Prova', 'metro', 'G1', 'Tram gran', 30, 9000, 'gran', null);

select throws_ok(
    $$ insert into public.trams (id, operador, mode, codi, nom, parades, arribades_feiner, mida, preu)
       values ('x', 'x', 'bus', 'x', 'x', 3, 10, 'gran', 100) $$,
    '23514', null,
    'Un tram gran no té preu'
);

-- Arribades: un dimecres sencer són les d'un feiner; un dissabte, × 0,6; de nit, cap.
select is(public.arribades_entre(190, '2026-11-04 00:00+01', '2026-11-05 00:00+01')::numeric, 190::numeric,
    'Un feiner sencer dona les arribades d''un feiner');
select is(round(public.arribades_entre(190, '2026-11-07 00:00+01', '2026-11-08 00:00+01')::numeric, 6), 114::numeric,
    'Un dissabte en dona el 60 %');
select is(public.arribades_entre(190, '2026-11-04 01:00+01', '2026-11-04 04:59+01')::numeric, 0::numeric,
    'De nit no hi ha arribades');
select is(round(public.arribades_entre(190, '2026-11-04 05:00+01', '2026-11-04 14:30+01')::numeric, 6), 95::numeric,
    'A mig servei, la meitat');

-- Dos jugadors de prova.
insert into auth.users (id, email) values
    ('00000000-0000-0000-0000-00000000000a', 'a@prova.cat'),
    ('00000000-0000-0000-0000-00000000000b', 'b@prova.cat');

set local role authenticated;
set local request.jwt.claims = '{"sub": "00000000-0000-0000-0000-00000000000a", "role": "authenticated"}';

select is((public.estat_partida() ->> 'saldo')::int, 500, 'Es comença amb el capital inicial');
select is((public.estat_partida() ->> 'saldo')::int, 500, 'Tornar a obrir l''app no torna a donar el capital');
select is((select count(*)::int from public.moviments where concepte = 'capital_inicial'), 1,
    'El capital inicial és un sol moviment');

select is(public.compra_tram('prova:gran') ->> 'resultat', 'gran', 'Un tram gran no es pot comprar sencer');
select is(public.compra_tram('prova:mitja') ->> 'resultat', 'sense_monedes', 'Amb el capital inicial no hi ha per a un mitjà');
select is(public.compra_tram('prova:car') ->> 'resultat', 'sense_monedes', 'Ni per a un petit massa car');
select is(public.compra_tram('no:existeix') ->> 'resultat', 'no_existeix', 'Un tram que no existeix');
select is(public.compra_tram('prova:petit'), '{"resultat": "comprat", "saldo": 200}'::jsonb,
    'Compra un tram petit i en paga el preu');
select is(public.compra_tram('prova:petit') ->> 'resultat', 'ja_es_teu', 'No es pot comprar dues vegades');
select is(public.estat_partida() -> 'trams', '["prova:petit"]'::jsonb, 'L''estat diu quins trams té');

select throws_ok($$ update public.partides set saldo = 1000000 $$, '42501', null,
    'El jugador no es pot canviar el saldo');
select throws_ok(
    $$ insert into public.trams_jugador (jugador_id, temporada_id, tram_id)
       values ('00000000-0000-0000-0000-00000000000a', public.temporada_actual(), 'prova:mitja') $$,
    '42501', null,
    'El jugador no es pot donar trams'
);

-- Una setmana sencera: 57 arribades × (5 feiners + 2 × 0,6) ≈ 353, d'1 a 5 monedes cadascuna. (Amb marge pel canvi
-- d'hora, que fa que una setmana no tingui sempre 168 hores de servei locals.)
reset role;
update public.trams_jugador set liquidat_fins = now() - interval '7 days', fraccio_arribada = 0.25;
select ok(
    public.liquida_partida('00000000-0000-0000-0000-00000000000a', public.temporada_actual()) between 340 * 1 and 360 * 5,
    'Cada arribada dona entre 1 i 5 monedes'
);
select ok(
    (select arribades from public.moviments where concepte = 'arribades') between 340 and 360,
    'Una setmana són les arribades de 5 feiners i 2 dies de cap de setmana'
);
select is(
    (select saldo from public.partides where jugador_id = '00000000-0000-0000-0000-00000000000a'),
    (select sum(monedes) from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000000a')::bigint,
    'El saldo és la suma dels moviments'
);

-- Un altre jugador no veu res del primer, i sense compte no es pot jugar.
set local role authenticated;
set local request.jwt.claims = '{"sub": "00000000-0000-0000-0000-00000000000b", "role": "authenticated"}';
select is((select count(*)::int from public.trams_jugador), 0, 'Un jugador no veu els trams dels altres');

set local role anon;
select throws_ok($$ select public.estat_partida() $$, '42501', null, 'Sense compte no es pot jugar');

select * from finish();
rollback;
