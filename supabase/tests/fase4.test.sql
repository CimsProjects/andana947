-- Tests de la Fase 4: MVP jugable (estructures, RLS, preus, nivell, check-in i tic diari).
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(25);

-- 1. Taules creades
select has_table('public', 'perfils', 'Hi ha la taula de perfils');
select has_table('public', 'concessions', 'Hi ha la taula de concessions');
select has_table('public', 'accions', 'Hi ha la taula d''accions');
select has_table('public', 'moviments', 'Hi ha la taula de moviments');
select has_table('public', 'checkins', 'Hi ha la taula de check-ins');

-- 2. RLS activat a totes les taules noves
select is(
    (select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relname in ('perfils', 'concessions', 'accions', 'moviments', 'checkins')
       and c.relrowsecurity),
    5,
    'Totes les taules de la fase 4 tenen l''RLS activat'
);

-- 3. Funcions auxiliars d'economia i nivell
select is(public.calcular_nivell(0), 1, '0 punts és nivell 1');
select is(public.calcular_nivell(99), 1, '99 punts és nivell 1');
select is(public.calcular_nivell(100), 2, '100 punts és nivell 2');
select is(public.calcular_nivell(283), 3, '283 punts és nivell 3');
select is(public.calcular_nivell(520), 4, '520 punts és nivell 4');

-- 4. Preu de concessions i accions (GDD 4.1 i 4.2)
-- 10 viatgers -> 10 € (mínim)
select is(public.preu_concessio(10), 10.00::numeric, 'Parada petita té el preu mínim de 10 €');
-- 1000 viatgers -> 130 €
select is(public.preu_concessio(1000), 130.00::numeric, 'Parada de 1.000 viatgers costa 130 €');
-- Gran estació (55.000 viatgers)
select ok(public.preu_accio(55000) > 1.00, 'Preu per acció d''una gran estació és > 1 €');

-- 5. Creació d'usuaris de prova i comprovació del trigger
-- Creem un usuari directament a auth.users
insert into auth.users (id, email) values ('00000000-0000-0000-0000-000000000001', 'jugador1@andana947.cat');

select is((select saldo from public.perfils where id = '00000000-0000-0000-0000-000000000001'), 200.00::numeric, 'El perfil s''ha creat automàticament amb 200 €');
select is((select count(*)::int from public.moviments where jugador_id = '00000000-0000-0000-0000-000000000001'), 1, 'S''ha anotat el capital inicial al llibre de moviments');

-- 6. Dades de prova de nodes
insert into public.municipis values ('08019', 'Barcelona', '13', 1600000, 101.3, 15794.6);
insert into public.municipis values ('25001', 'Abella de la Conca', '25', 180, 78.3, 2.3); -- Baixa densitat (<20)

insert into public.nodes (id, nom, mode, operador, posicio, municipi, viatgers_feiner, font_afluencia) values
    ('prova:parada-bcn', 'Parada BCN', 'bus', 'TMB', 'SRID=4326;POINT(2.1700 41.3800)', '08019', 500, 'estimat'),
    ('prova:estacio-gran', 'Estació Gran BCN', 'metro', 'TMB', 'SRID=4326;POINT(2.1705 41.3805)', '08019', 30000, 'real'),
    ('prova:parada-pirineu', 'Parada Pirineu', 'bus', 'Generalitat', 'SRID=4326;POINT(1.0800 42.1600)', '25001', 50, 'estimat');

-- 7. RLS per a usuaris autenticats
set local role authenticated;
set local "request.jwt.claims" to '{"sub": "00000000-0000-0000-0000-000000000001"}';

select is((select saldo from public.perfils where id = '00000000-0000-0000-0000-000000000001'), 200.00::numeric, 'El jugador pot consultar el seu perfil');

-- Intentar modificar saldo directament -> denegat
select throws_ok(
    $$ update public.perfils set saldo = 99999 where id = '00000000-0000-0000-0000-000000000001' $$,
    '42501', null,
    'El client no pot modificar directament el saldo'
);

-- 8. Prova de Check-in (Massa lluny)
select is(
    (select public.fer_checkin('prova:parada-bcn', 41.4000, 2.2000, 10.0, 0.0)->>'status'),
    'massa_lluny',
    'El check-in massa lluny és rebutjat'
);

-- 9. Prova de Check-in vàlid: Conquesta de concessió
select is(
    (select public.fer_checkin('prova:parada-bcn', 41.3800, 2.1700, 10.0, 0.0)->>'status'),
    'valid',
    'El check-in a distància correcta s''accepta i adquireix la concessió'
);

select ok((select count(*) = 1 from public.concessions where node_id = 'prova:parada-bcn' and jugador_id = '00000000-0000-0000-0000-000000000001'),
    'La concessió queda registrada a nom del jugador'
);

-- Saldo després de comprar la concessió (200 - preu)
select ok((select saldo < 200.00 from public.perfils where id = '00000000-0000-0000-0000-000000000001'),
    'El saldo s''ha descomptat al servidor'
);

-- 10. nodes_a_prop_jugador retorna l'estat de la concessió
select is(
    (select te_concessio from public.nodes_a_prop_jugador(41.3800, 2.1700, 500) where id = 'prova:parada-bcn'),
    true,
    'nodes_a_prop_jugador indica que el jugador té la concessió'
);

-- 11. estat_imperi retorna el resum
select ok(
    (select jsonb_array_length(public.estat_imperi()->'concessions_actives') = 1),
    'estat_imperi mostra 1 concessió activa'
);

-- 12. Tic diari (com a service_role)
reset role;
set local role service_role;

select is(
    (select public.tic_diari(true)->>'status'),
    'ok',
    'El tic diari s''executa correctament'
);

select * from finish();
rollback;
