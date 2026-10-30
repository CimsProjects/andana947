-- Tests de la maestria, les millores i els objectius d'«unir».
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(31);

-- Valors: han de coincidir amb dades/configuracio_joc.json (blocs «maestria», «millores» i «unir»).
select is(public.config_maestria() -> 'xp_nivells', '[0, 50, 150, 400, 1000, 2500, 6000, 15000, 35000, 80000]'::jsonb,
    'Els llindars d''experiència dels deu nivells');
select is((public.config_maestria() ->> 'xp_per_checkin')::int, 100, 'Un check-in dona 100 d''experiència');
select is(public.config_maestria() -> 'premi_nivell_andana', '[0, 5, 10, 20, 40, 80, 150, 300, 500, 1000]'::jsonb,
    'Els premis de cada nivell');
select is((public.config_maestria() ->> 'multiplicador_premi_linia')::int, 2, 'Les línies paguen el doble');
select is((public.config_unir() ->> 'radi_transbord_metres')::int, 200, 'Transbord a peu fins a 200 m');
select is((select count(*)::int from public.millores), 6, 'Hi ha sis millores');
select is((select bonus from public.millores where id = 'ascensor'), 0.20::numeric, 'L''ascensor dona un 20 %');
select is((select nivell_minim from public.millores where id = 'cafeteria'), 7, 'La cafeteria, a partir del nivell 7');
select ok(not exists (select 1 from public.millores where 'bus' = any (modes) and id = 'ascensor'), 'Les parades de bus no tenen ascensor');

-- Nivells.
select is(public.nivell_maestria(0), 1, 'Sense experiència, nivell 1');
select is(public.nivell_maestria(49), 1, '49 d''experiència, encara nivell 1');
select is(public.nivell_maestria(50), 2, '50 d''experiència, nivell 2');
select is(public.nivell_maestria(1e9), 10, 'El nivell màxim és el 10');
select is(public.xp_nivell(11), null, 'No hi ha nivell 11');

-- Dades de prova (no del joc): una línia de quatre parades seguides en dues comarques de dues províncies, i un jugador.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values
    ('99001', 'Prova A', '90', 1000, 10, 100), ('99002', 'Prova B', '90', 1000, 10, 100),
    ('98003', 'Prova C', '91', 1000, 10, 100);
insert into public.linies (id, operador, mode, codi, expedicions_feiner) values ('prova:R1', 'Prova', 'rodalies', 'R1', 40);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
values
    ('prova:1', 'U', 'rodalies', 'Prova', 'SRID=4326;POINT(1.00 41.00)', '99001', '90', '{R1}', 40, 1000, 'estimat'),
    ('prova:2', 'Dos', 'rodalies', 'Prova', 'SRID=4326;POINT(1.05 41.00)', '99002', '90', '{R1}', 40, 1000, 'estimat'),
    ('prova:3', 'Tres', 'rodalies', 'Prova', 'SRID=4326;POINT(1.10 41.00)', '98003', '91', '{R1}', 40, 1000, 'estimat'),
    ('prova:4', 'Quatre', 'rodalies', 'Prova', 'SRID=4326;POINT(1.15 41.00)', '98003', '91', '{R1}', 40, 1000, 'estimat'),
    ('prova:5', 'Cinc', 'bus', 'Prova', 'SRID=4326;POINT(1.1505 41.00)', '98003', '91', '{}', 0, 10, 'estimat');
insert into public.linia_nodes (linia_id, node_id, expedicions_feiner) values
    ('prova:R1', 'prova:1', 40), ('prova:R1', 'prova:2', 40), ('prova:R1', 'prova:3', 40), ('prova:R1', 'prova:4', 40);
insert into public.trams (linia_id, node_a, node_b) values
    ('prova:R1', 'prova:1', 'prova:2'), ('prova:R1', 'prova:2', 'prova:3'), ('prova:R1', 'prova:3', 'prova:4');
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000a001');
insert into public.perfils (id, saldo, prestigi, nivell) values ('00000000-0000-0000-0000-00000000a001', 1000, 0, 1)
    on conflict (id) do update set saldo = 1000;
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000a001', 'prova:1', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000a001', 'prova:2', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000a001', 'prova:4', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000a001', 'prova:5', now(), now() + interval '100 years', true, false);

-- Unir: U–Dos és un tram (dos municipis, una comarca); Quatre i Cinc, un transbord a peu (uns 40 m); Tres no és meva.
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'tram_llarg')::int, 2, 'La xarxa més gran té dues andanes');
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'uneix_municipis')::int, 2, 'U i Dos uneixen dos municipis');
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'uneix_comarques')::int, 1, 'Encara cap xarxa amb dues comarques');
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'uneix_linies')::int, 1, 'Una línia a la xarxa');
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000a001', 'prova:3', now(), now() + interval '100 years', true, false);
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'tram_llarg')::int, 5, 'Amb Tres, tot és una xarxa de cinc');
select is((public.progres_fites('00000000-0000-0000-0000-00000000a001') ->> 'uneix_provincies')::int, 2, 'La xarxa uneix dues províncies');
select ok(public.revisa_fites('00000000-0000-0000-0000-00000000a001') ? 'uneix_provincies_2', 'Es guanya la fita d''unir dues províncies');

-- Maestria: les arribades d'un dia feiner sencer (40 per andana) i el premi del nivell 2 (5 €; la línia, 10 €).
select is(public.suma_maestria_periode(true, 1) >= 1, true, 'Un dia d''arribades fa pujar de nivell');
select is((select nivell from public.maestries where jugador_id = '00000000-0000-0000-0000-00000000a001' and tipus = 'andana' and clau = 'prova:1'),
    1, '40 arribades: encara nivell 1');
select is((select nivell from public.maestries where jugador_id = '00000000-0000-0000-0000-00000000a001' and tipus = 'linia' and clau = 'rodalies:R1'),
    3, 'La línia suma les arribades de les quatre andanes (160): nivell 3');
select is((select sum(import) from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000a001' and tipus = 'maestria'),
    30.00::numeric, 'La línia cobra (5 + 10) × 2 = 30 €; les andanes, res');
select is(public.suma_xp('00000000-0000-0000-0000-00000000a001', 'andana', 'prova:1', 100), 1, 'Un check-in fa pujar l''andana al nivell 2');

-- Millores: l'ingrés de l'andana puja amb el bonus.
insert into public.millores_andana (jugador_id, node_id, millora) values ('00000000-0000-0000-0000-00000000a001', 'prova:1', 'aparcament_bicis');
select is(public.factor_millores('00000000-0000-0000-0000-00000000a001', 'prova:1'), 1.05::numeric, 'L''aparcament de bicis suma un 5 %');
select is(public.factor_millores('00000000-0000-0000-0000-00000000a001', 'prova:2'), 1::numeric, 'Sense millores, factor 1');

-- Permisos.
select ok(has_function_privilege('authenticated', 'public.compra_millora(text, text)', 'execute'), 'Un jugador pot comprar millores');
select ok(not has_function_privilege('authenticated', 'public.suma_xp(uuid, text, text, numeric)', 'execute'),
    'Un jugador no pot donar-se experiència');
select ok(not has_function_privilege('authenticated', 'public.suma_maestria_periode(boolean, numeric)', 'execute'),
    'Un jugador no pot liquidar la maestria');

select * from finish();
rollback;
