-- Tests de la flota (fase 5) i del motor de caos (fase 6). S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(30);

-- Valors: han de coincidir amb dades/configuracio_joc.json (blocs «flota» i «caos») i dades/flota_cataleg.json.
select is((public.config_flota() ->> 'subvencio_per_ingres')::numeric, 1.083, 'Subvenció: 1,083 € per euro de bitllet (CNMC)');
select is((public.config_flota() ->> 'cost_km_bus')::numeric, 1.4395, 'Explotació del bus: 1,4395 €/km (Observatorio 2026)');
select is((public.config_flota() ->> 'dies_fianca')::int, 30, 'Fiança de 30 dies');
select is((public.config_caos() ->> 'multiplicador_tardor')::int, 3, 'Temporals: ×3 a la tardor');
select is((select preu from public.models_vehicle where id = 'bus_electric_12'), 604898::numeric, 'Bus elèctric de TMB: 604.898 €');
select is((select count(*)::int from public.models_vehicle), 7, 'Set models de vehicle');
select is((select count(*)::int from public.tipus_esdeveniment), 6, 'Sis tipus d''esdeveniment aleatori');
select is((select probabilitat from public.tipus_esdeveniment where id = 'robatori_coure'), 0.004::numeric, 'Robatori de coure: 0,4 % al dia');

-- Dades de prova (no del joc): una línia de bus de 10 parades amb 4 vehicles en hora punta, i un jugador.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values ('99001', 'Prova', '90', 1000, 10, 100);
insert into public.linies (id, operador, mode, codi, expedicions_feiner) values ('prova:B1', 'TMB', 'bus', 'B1', 100);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
select 'prova:' || i, 'Parada ' || i, 'bus', 'TMB', ('SRID=4326;POINT(1.' || lpad(i::text, 2, '0') || ' 41.00)')::extensions.geography,
       '99001', '90', '{B1}', 100, 500, 'estimat'
from generate_series(1, 10) as i;
insert into public.linia_nodes (linia_id, node_id, expedicions_feiner) select 'prova:B1', 'prova:' || i, 100 from generate_series(1, 10) as i;
insert into public.flota_linies (linia, mode, codi, operador, vehicles_punta, expedicions_feiner, km_feiner, viatgers_feiner, nodes)
values ('bus:B1', 'bus', 'B1', 'TMB', 4, 100, 1000, 8000, 10);
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000f001');
insert into public.perfils (id, saldo, prestigi, nivell) values ('00000000-0000-0000-0000-00000000f001', 1000, 0, 1)
on conflict (id) do update set saldo = 1000;
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000f001', 'prova:1', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000f001', 'prova:2', now(), now() + interval '100 years', true, false),
    ('00000000-0000-0000-0000-00000000f001', 'prova:3', now(), now() + interval '100 years', true, false);

-- Flota: el model, el que pot llogar (3 de 10 parades → 2 de 4 vehicles) i l'economia d'un vehicle.
select is(public.model_linia('bus:B1'), 'bus_electric_12', 'A una línia de TMB, el bus elèctric');
select is(public.maxim_vehicles('00000000-0000-0000-0000-00000000f001', 'bus:B1'), 2, 'Amb 3 de 10 parades, 2 de 4 vehicles');
select is((select round(quota, 4) from public.economia_vehicle('bus:B1', 1.0)), round(604898 / (12 * 365.0) * 0.01, 4),
    'La quota és el preu repartit en 12 anys, a l''1 %');
select is((select round(explotacio, 4) from public.economia_vehicle('bus:B1', 1.0)), round(1000 / 4.0 * 1.4395 * 0.01, 4),
    'L''explotació són els km del vehicle per 1,4395 €/km, a l''1 %');
select is((select round(ingres, 4) from public.economia_vehicle('bus:B1', 1.0)), round(public.ingres_diari(8000) * 2.083 / 4, 4),
    'L''ingrés és el de la línia amb la subvenció, repartit entre els 4 vehicles');

set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000f001';
select is(public.lloga_vehicles('bus:B1', 3) ->> 'status', 'limit_flota', 'No se''n poden llogar més dels que toquen');
select is(public.lloga_vehicles('bus:B1', 2) ->> 'status', 'valid', 'Es lloguen 2 vehicles');
select is((select vehicles from public.flota_jugador where jugador_id = '00000000-0000-0000-0000-00000000f001'), 2, 'Té 2 vehicles');
select ok((select saldo from public.perfils where id = '00000000-0000-0000-0000-00000000f001') < 1000, 'Paga la fiança');
select is(jsonb_array_length(public.estat_flota()), 1, 'La pantalla de flota ensenya la línia');
select ok(public.ingres_jugador_exacte('00000000-0000-0000-0000-00000000f001', true, 1)
          > (select sum(public.ingres_diari_concessio(c.node_id, 1.0) - public.manteniment_diari(c.node_id, c.jugador_id))
             from public.concessions c where c.jugador_id = '00000000-0000-0000-0000-00000000f001'),
    'Els vehicles fan cobrar més');
select is(public.retorna_vehicles('bus:B1', 2) ->> 'status', 'valid', 'Es tornen els vehicles');
select is((select saldo from public.perfils where id = '00000000-0000-0000-0000-00000000f001'), 1000::numeric
    + coalesce((select sum(import) from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000f001'
                and tipus in ('cobrament', 'maestria')), 0),
    'Tornant-los, es recupera tota la fiança');

-- Caos: un esdeveniment a la línia baixa l'ingrés de les andanes i dels vehicles mentre dura.
insert into public.esdeveniments (tipus, origen, clau, inici, fi, multiplicador, linies)
values ('avaria_infraestructura', 'prova', 'b1', now() - interval '1 hour', now() + interval '1 hour', 0.5, '{bus:B1}');
select is(public.factor_caos('prova:1', now()), 0.5::numeric, 'L''avaria deixa l''andana a la meitat');
select is(public.factor_caos('prova:1', now() + interval '2 hours'), 1::numeric, 'Quan s''acaba, tot torna a ser normal');
select is(public.factor_caos_linia('bus:B1', now()), 0.5::numeric, 'I els vehicles de la línia, també');
select is(jsonb_array_length((select e -> 'andanes' from jsonb_array_elements(public.esdeveniments_avui()) e
                              where e ->> 'tipus' = 'avaria_infraestructura')), 3, 'L''app sap quines andanes toca');

-- El sorteig sempre dona el mateix per al mateix dia, i no dona res dues vegades.
select ok(public.atzar('a') = public.atzar('a') and public.atzar('a') between 0 and 1, 'L''atzar es pot repetir');
create temp table sorteig as select public.sorteja_esdeveniments(date '2026-12-01') as n;
select is(public.sorteja_esdeveniments(date '2026-12-01'), 0, 'Tornar a sortejar el mateix dia no afegeix res');

-- Permisos.
select ok(not has_function_privilege('authenticated', 'public.sorteja_esdeveniments(date)', 'execute'), 'Un jugador no pot sortejar');
select ok(has_function_privilege('authenticated', 'public.lloga_vehicles(text, integer)', 'execute'), 'Un jugador pot llogar vehicles');
select ok(not has_function_privilege('anon', 'public.estat_flota()', 'execute'), 'Un anònim no veu cap flota');

select * from finish();
rollback;
