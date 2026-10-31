-- Tests de comprar i vendre vehicles i del perfil assegurat. S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(16);

-- Ha de coincidir amb dades/configuracio_joc.json (flota.margeVenda).
select is((public.config_flota() ->> 'marge_venda')::numeric, 0.7, 'En vendre es cobra el 70 % del valor que queda');

-- Dades de prova (no del joc): una línia de bus de 4 parades amb 4 vehicles en hora punta.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values ('99001', 'Prova', '90', 1000, 10, 100);
insert into public.linies (id, operador, mode, codi, expedicions_feiner) values ('prova:B1', 'TMB', 'bus', 'B1', 100);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
select 'prova:' || i, 'Parada ' || i, 'bus', 'TMB', ('SRID=4326;POINT(1.0' || i || ' 41.00)')::extensions.geography,
       '99001', '90', '{B1}', 100, 500, 'estimat'
from generate_series(1, 4) as i;
insert into public.linia_nodes (linia_id, node_id, expedicions_feiner) select 'prova:B1', 'prova:' || i, 100 from generate_series(1, 4) as i;
insert into public.flota_linies (linia, mode, codi, operador, vehicles_punta, expedicions_feiner, km_feiner, viatgers_feiner, nodes)
values ('bus:B1', 'bus', 'B1', 'TMB', 4, 100, 1000, 8000, 4);
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000a501');
insert into public.perfils (id, saldo, prestigi, nivell) values ('00000000-0000-0000-0000-00000000a501', 20000, 0, 1)
on conflict (id) do update set saldo = 20000;
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000a501', 'prova:1', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000a501', 'prova:2', now(), now() + interval '100 years', true, false);

set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000a501';
-- 2 de 4 parades: 2 vehicles. Un de llogat i un de comprat.
select is(public.preu_compra_vehicle('bus:B1'), round(604898 * 0.01, 2), 'El bus costa el preu real a l''1 %');
select is(public.lloga_vehicles('bus:B1', 1) ->> 'status', 'valid', 'Se''n lloga un');
select is(public.compra_vehicle('bus:B1') ->> 'status', 'valid', 'Se''n compra un');
select is(public.compra_vehicle('bus:B1') ->> 'status', 'limit_flota', 'Llogats i comprats sumen per al màxim');
select is(public.vehicles_a_linia('00000000-0000-0000-0000-00000000a501', 'bus:B1'), 2, 'En té dos');
select is((select count(*)::int from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000a501'
           and concepte like 'Compra d''un vehicle%' and import = -6048.98), 1, 'Paga el preu');

-- El comprat dona més cada dia que el llogat: no paga la quota.
select ok((public.estat_flota() -> 0 ->> 'net_dia_propi')::numeric > (public.estat_flota() -> 0 ->> 'net_dia')::numeric,
    'Un vehicle comprat dona més cada dia');
select is((public.estat_flota() -> 0 ->> 'propis')::int, 1, 'La pantalla sap que en té un de comprat');

-- Vendre'l de seguida torna el 70 % (no es poden fer diners comprant i venent).
select is(public.valor_venda(6048.98, now(), 'bus'), round(6048.98 * 0.7, 2), 'Nou, val el 70 % del preu');
select ok(public.valor_venda(6048.98, now() - interval '6 years', 'bus') between 2100 and 2130, 'A mitja vida útil, la meitat');
select is(public.valor_venda(6048.98, now() - interval '20 years', 'bus'), 0::numeric, 'Passada la vida útil, no val res');
select is(public.ven_vehicle('bus:B1') ->> 'status', 'valid', 'Es ven');
select is(public.vehicles_a_linia('00000000-0000-0000-0000-00000000a501', 'bus:B1'), 1, 'En queda el llogat');

-- Perfil assegurat: un compte sense perfil en té en entrar.
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000a502');
delete from public.perfils where id = '00000000-0000-0000-0000-00000000a502';
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000a502';
select ok(public.assegura_perfil(), 'Un compte sense perfil en té en entrar');
select ok(exists (select 1 from public.perfils where id = '00000000-0000-0000-0000-00000000a502'), 'Es crea el perfil que faltava');

select * from finish();
rollback;
