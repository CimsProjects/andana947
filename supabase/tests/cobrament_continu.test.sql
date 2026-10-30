-- Tests del cobrament continu: es cobra per cada arribada, a mesura que passa el temps.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(14);

-- Dades de prova (no del joc): una andana i un jugador que la té.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values ('99001', 'Prova', '90', 1000, 10, 100);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
values ('prova:1', 'U', 'rodalies', 'Prova', 'SRID=4326;POINT(1.00 41.00)', '99001', '90', '{}', 40, 5000, 'estimat'),
       ('prova:2', 'Dos', 'rodalies', 'Prova', 'SRID=4326;POINT(1.10 41.00)', '99001', '90', '{}', 40, 5000, 'estimat');
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000c001');
insert into public.perfils (id, saldo, prestigi, nivell) values ('00000000-0000-0000-0000-00000000c001', 100, 0, 1)
on conflict (id) do update set saldo = 100;
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000c001', 'prova:1', now(), now() + interval '100 years', true, false);

-- Fa 24 hores que no es cobra: es paga un dia de servei sencer (entre 0,6 i 1 dia feiner, segons els dies).
update public.perfils set cobrat_fins = now() - interval '24 hours', resta_cobrament = 0, pendent_benvinguda = 0,
    pendent_arribades = 0, vist_el = now() - interval '1 day'
where id = '00000000-0000-0000-0000-00000000c001';
create temp table dia as
select public.ingres_jugador_exacte('00000000-0000-0000-0000-00000000c001', true, 1) as feiner;
create temp table pagat as select public.liquida_jugador('00000000-0000-0000-0000-00000000c001') as import;

select ok((select feiner from dia) > 0, 'L''andana dona diners');
select ok((select import from pagat) between round((select feiner from dia) * 0.6, 2) - 0.01 and (select feiner from dia) + 0.01,
    'Es cobra el dia de servei sencer');
select is((select saldo from public.perfils where id = '00000000-0000-0000-0000-00000000c001'), 100 + (select import from pagat),
    'El saldo puja el que s''ha cobrat');
select ok((select cobrat_fins from public.perfils where id = '00000000-0000-0000-0000-00000000c001') >= now() - interval '1 second',
    'Cobrat fins ara');
select is(public.liquida_jugador('00000000-0000-0000-0000-00000000c001'), 0::numeric, 'Tornar a cobrar de seguida no paga res');
select is((select count(*)::int from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000c001' and tipus = 'cobrament'),
    1, 'Un apunt per hora');
select ok((select resta_cobrament from public.perfils where id = '00000000-0000-0000-0000-00000000c001') between 0 and 0.01,
    'Els cèntims que falten es guarden');
select ok((select pendent_arribades from public.perfils where id = '00000000-0000-0000-0000-00000000c001') between 23.9 and 40.1,
    'Les arribades d''un dia (40 en un feiner, 24 en festiu)');

-- En tornar a l'app, es veu el que s'ha cobrat mentre no hi era.
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000c001';
select is((public.estat_cobraments() ->> 'pendent_total')::numeric, (select import from pagat), 'La benvinguda ensenya el que s''ha cobrat');
-- La primera crida de l'app oberta no ho esborra (l'app feia temps que no hi era); la segona, sí.
select ok((public.cobra_arribades() ->> 'pendent_total')::numeric > 0, 'En tornar, la benvinguda encara hi és');
select is((public.cobra_arribades() ->> 'pendent_total')::numeric, 0::numeric, 'Amb l''app oberta, es veu en directe');

-- Abans de comprar, es cobra fins ara: l'andana nova no cobra el passat.
update public.perfils set cobrat_fins = now() - interval '24 hours' where id = '00000000-0000-0000-0000-00000000c001';
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000c001', 'prova:2', now(), now() + interval '100 years', true, false);
select ok((select cobrat_fins from public.perfils where id = '00000000-0000-0000-0000-00000000c001') >= now() - interval '1 second',
    'En comprar, primer es cobra el que hi havia');

-- Permisos.
select ok(not has_function_privilege('authenticated', 'public.liquida_jugador(uuid)', 'execute'), 'Un jugador no es pot pagar');
select ok(has_function_privilege('authenticated', 'public.cobra_arribades()', 'execute'), 'Un jugador pot cobrar les seves arribades');

select * from finish();
rollback;
