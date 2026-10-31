-- Tests de la part social (fase 7): mercat d'accions, juntes i cooperatives. S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(26);

-- Valors: han de coincidir amb dades/configuracio_joc.json (bloc «social»).
select is((public.config_social() ->> 'comissio_venda')::numeric, 0.02, 'El venedor paga un 2 %');
select is((public.config_social() ->> 'majoria')::numeric, 0.5, 'Les propostes s''aproven per majoria de les accions venudes');
select is((public.config_social() ->> 'maxim_membres')::int, 20, 'Cooperatives de 20 membres com a molt');
select ok((select count(*) from public.comarques) >= 42, 'Hi ha els noms de les comarques');

-- Dades de prova (no del joc): una gran estació i tres jugadors.
insert into public.municipis (codi, nom, comarca, poblacio, superficie_km2, densitat) values ('99001', 'Prova', '13', 1000, 10, 100);
insert into public.nodes (id, nom, mode, operador, posicio, municipi, comarca, linies, expedicions_feiner, viatgers_feiner, font_afluencia)
values ('prova:gran', 'Gran Estació', 'rodalies', 'Prova', 'SRID=4326;POINT(1.0 41.0)', '99001', '13', '{}', 100, 50000, 'estimat'),
       ('prova:petita', 'Petita', 'rodalies', 'Prova', 'SRID=4326;POINT(1.1 41.0)', '99001', '13', '{}', 10, 500, 'estimat');
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000b701'), ('00000000-0000-0000-0000-00000000b702'),
    ('00000000-0000-0000-0000-00000000b703');
insert into public.perfils (id, saldo, prestigi, nivell) values
    ('00000000-0000-0000-0000-00000000b701', 10000, 0, 1), ('00000000-0000-0000-0000-00000000b702', 10000, 0, 1),
    ('00000000-0000-0000-0000-00000000b703', 10000, 0, 1)
on conflict (id) do update set saldo = 10000;
insert into public.accions (node_id, jugador_id, quantitat) values
    ('prova:gran', '00000000-0000-0000-0000-00000000b701', 20), ('prova:gran', '00000000-0000-0000-0000-00000000b702', 10);

-- Mercat: el primer posa 5 accions a la venda; el segon en compra 3.
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000b701';
create temp table ref as select public.preu_accio(50000) as preu;
select is(public.posa_venda('prova:gran', 5, (select preu from ref) * 10) ->> 'status', 'preu_fora', 'No es pot vendre molt per sobre del preu');
select is(public.posa_venda('prova:gran', 25, (select preu from ref)) ->> 'status', 'error', 'No es poden vendre més accions de les que tens');
select is(public.posa_venda('prova:gran', 5, (select preu from ref)) ->> 'status', 'valid', 'Es posen 5 accions a la venda');
select is(public.accions_lliures('00000000-0000-0000-0000-00000000b701', 'prova:gran'), 15, 'Les de la venda queden reservades');
create temp table oferta as select id from public.ofertes_accions where venedor_id = '00000000-0000-0000-0000-00000000b701';
select is(public.compra_oferta((select id from oferta), 1) ->> 'status', 'error', 'No es poden comprar les pròpies');
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000b702';
select is(public.compra_oferta((select id from oferta), 3) ->> 'status', 'valid', 'El segon en compra 3');
select is((select quantitat from public.accions where jugador_id = '00000000-0000-0000-0000-00000000b702'), 13, 'Ara en té 13');
select is((select quantitat from public.accions where jugador_id = '00000000-0000-0000-0000-00000000b701'), 17, 'El primer, 17');
select is((select quantitat from public.ofertes_accions where id = (select id from oferta)), 2, 'A l''oferta en queden 2');
select is((select import from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000b701' and tipus = 'mercat'),
    round((select preu from ref) * 3 * 0.98, 2), 'El venedor cobra menys la comissió');
select is(public.compra_oferta((select id from oferta), 8) ->> 'status', 'error', 'No se''n poden comprar més de les que hi ha');

-- Junta: el primer (17 de 30) és el president i té la majoria; el segon proposa una millora i el primer l'aprova.
select ok((public.estat_junta('prova:gran') -> 'accionistes' -> 0 ->> 'president')::boolean
          and (public.estat_junta('prova:gran') -> 'accionistes' -> 0 ->> 'quantitat')::int = 17,
    'El president és qui en té més');
select is(public.proposa_millora_estacio('prova:gran', 'pantalles') ->> 'status', 'valid', 'Un accionista proposa una millora');
select is((select estat from public.propostes_junta where node_id = 'prova:gran'), 'oberta', 'Amb 13 de 30, no n''hi ha prou');
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000b703';
select is(public.vota_proposta((select id from public.propostes_junta where node_id = 'prova:gran'), true) ->> 'status', 'error',
    'Qui no té accions no vota');
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000b701';
select is(public.vota_proposta((select id from public.propostes_junta where node_id = 'prova:gran'), true) ->> 'status', 'valid',
    'El president hi vota a favor');
select is((select estat from public.propostes_junta where node_id = 'prova:gran'), 'aprovada', 'Aprovada per majoria');
select is(public.factor_millores_estacio('prova:gran'), 1.10::numeric, 'Les pantalles pugen els dividends un 10 %');
select is((select count(*)::int from public.moviments where tipus = 'junta' and node_id = 'prova:gran'), 2,
    'La paguen els dos que hi han votat a favor');

-- Cooperatives: cal tenir andanes a la comarca.
insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal) values
    ('00000000-0000-0000-0000-00000000b701', 'prova:petita', now(), now() + interval '100 years', true, false);
select is(public.crea_cooperativa('Els de Prova', '01') ->> 'status', 'error', 'No es pot fundar on no tens andanes');
select is(public.crea_cooperativa('Els de Prova', '13') ->> 'status', 'valid', 'Es funda una cooperativa al Barcelonès');
set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000b702';
select is(public.uneix_cooperativa((select id from public.cooperatives where nom = 'Els de Prova')) ->> 'status', 'error',
    'Sense andanes a la comarca, no t''hi pots unir');

select * from finish();
rollback;
