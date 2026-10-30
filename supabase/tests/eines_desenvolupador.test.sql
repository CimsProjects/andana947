-- Tests de les eines del mode desenvolupador. S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

-- Dades de prova (no del joc): una andana d'una línia i dos jugadors, un amb el mode desenvolupador.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values ('99001', 'Prova', '90', 1000, 10, 100);
insert into public.linies (id, operador, mode, codi, expedicions_feiner) values ('prova:R1', 'Prova', 'rodalies', 'R1', 40);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
values ('prova:1', 'U', 'rodalies', 'Prova', 'SRID=4326;POINT(1.00 41.00)', '99001', '90', '{R1}', 40, 1000, 'estimat');
insert into public.linia_nodes (linia_id, node_id, expedicions_feiner) values ('prova:R1', 'prova:1', 40);
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000d001'), ('00000000-0000-0000-0000-00000000d002');
insert into public.perfils (id, saldo, prestigi, nivell, mode_dev) values
    ('00000000-0000-0000-0000-00000000d001', 100, 0, 1, true),
    ('00000000-0000-0000-0000-00000000d002', 100, 0, 1, false)
on conflict (id) do update set saldo = excluded.saldo, mode_dev = excluded.mode_dev;
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000d001', 'prova:1', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000d002', 'prova:1', now(), now() + interval '100 years', true, false);

-- Sense el mode desenvolupador, res.
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000d002';
select is(public.dev_suma_maestria('prova:1', 1000) ->> 'status', 'error', 'Sense mode desenvolupador no se suma experiència');
select is(public.dev_desbloqueja_skins() ->> 'status', 'error', 'Sense mode desenvolupador no es desbloquegen skins');
select is(public.dev_cobra_ara() ->> 'status', 'error', 'Sense mode desenvolupador no es cobra');

-- Amb el mode desenvolupador.
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000d001';
select is(public.dev_suma_maestria('prova:1', 1000) ->> 'status', 'valid', 'Se suma experiència a l''andana');
select is((select nivell from public.maestries where jugador_id = '00000000-0000-0000-0000-00000000d001' and tipus = 'andana' and clau = 'prova:1'),
    5, '1.000 d''experiència: nivell 5');
select is((select nivell from public.maestries where jugador_id = '00000000-0000-0000-0000-00000000d001' and tipus = 'linia' and clau = 'rodalies:R1'),
    5, 'I la línia, també');
select is(public.dev_desbloqueja_skins() ->> 'status', 'valid', 'Es desbloquegen les skins');
select is((select count(*) from public.skins_jugador where jugador_id = '00000000-0000-0000-0000-00000000d001'),
    (select count(*) from public.skins), 'Totes les skins');
select is(public.dev_cobra_ara() ->> 'status', 'valid', 'Es cobren 4 hores d''arribades');

select * from finish();
rollback;
