-- Tests de les taules de dades de la xarxa (fase 2): estructura, RLS i la consulta de nodes a prop.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(18);

select has_table('public', 'municipis', 'Hi ha la taula de municipis');
select has_table('public', 'linies', 'Hi ha la taula de línies');
select has_table('public', 'nodes', 'Hi ha la taula de nodes');
select has_table('public', 'linia_nodes', 'Hi ha la taula de parades de cada línia');
select has_table('public', 'fonts_dades', 'Hi ha la taula de fonts');
select col_type_is('public', 'nodes', 'posicio', 'extensions', 'geography(Point,4326)', 'La posició és un punt geogràfic');

select is(
    (select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relname in ('municipis', 'linies', 'nodes', 'linia_nodes', 'fonts_dades')
       and c.relrowsecurity),
    5,
    'Totes les taules de dades tenen l''RLS activat'
);

-- Dades de prova (no són del joc): Baró de Viver i dues parades inventades al voltant.
insert into public.municipis values ('08019', 'Barcelona', '13', 1000, 100.5, 9.95);
insert into public.linies (id, operador, mode, codi, nom, color) values ('prova:L1', 'TMB', 'metro', 'L1', 'Línia 1', 'CE1126');
insert into public.nodes (id, nom, mode, operador, posicio, municipi, viatgers_feiner, font_afluencia) values
    ('prova:estacio', 'Estació', 'metro', 'TMB', 'SRID=4326;POINT(2.2005 41.4359)', '08019', 1650, 'real'),
    ('prova:a-prop', 'A prop', 'bus', 'TMB', 'SRID=4326;POINT(2.2010 41.4362)', '08019', 300, 'estimat'),
    ('prova:lluny', 'Lluny', 'bus', 'TMB', 'SRID=4326;POINT(2.2500 41.4500)', '08019', 30, 'estimat');
insert into public.linia_nodes values ('prova:L1', 'prova:estacio', 120);

select throws_ok(
    $$ insert into public.nodes (id, nom, mode, operador, posicio, viatgers_feiner, font_afluencia)
       values ('x', 'x', 'bus', 'x', 'SRID=4326;POINT(2 41)', -1, 'real') $$,
    '23514', null,
    'L''afluència no pot ser negativa'
);
select throws_ok(
    $$ insert into public.linies (id, operador, mode, codi, color) values ('x', 'x', 'bus', 'x', 'vermell') $$,
    '23514', null,
    'El color de línia és hexadecimal'
);

set local role anon;

select is((select count(*)::int from public.nodes where id like 'prova:%'), 3, 'Un usuari anònim pot llegir els nodes');
select is((select count(*)::int from public.linia_nodes), 1, 'Un usuari anònim pot llegir les parades de cada línia');
select throws_ok(
    $$ insert into public.nodes (id, nom, mode, operador, posicio, viatgers_feiner, font_afluencia)
       values ('y', 'y', 'bus', 'y', 'SRID=4326;POINT(2 41)', 1, 'real') $$,
    '42501', null,
    'Un usuari anònim no pot afegir nodes'
);
select throws_ok($$ update public.nodes set viatgers_feiner = 1e6 $$, '42501', null,
    'Un usuari anònim no pot canviar l''afluència');
select throws_ok($$ delete from public.municipis $$, '42501', null, 'Un usuari anònim no pot esborrar municipis');

select results_eq(
    $$ select id from public.nodes_a_prop(41.4359, 2.2005, 500) $$,
    $$ values ('prova:estacio'), ('prova:a-prop') $$,
    'nodes_a_prop retorna els nodes del radi, del més proper al més llunyà'
);
select ok(
    (select abs(distancia_metres - 53.4) < 1 from public.nodes_a_prop(41.4359, 2.2005, 500) where id = 'prova:a-prop'),
    'nodes_a_prop calcula la distància en metres'
);
select is(
    (select count(*)::int from public.nodes_a_prop(41.4359, 2.2005, 1e9, 1000)),
    3,
    'El radi màxim és de 5 km i el màxim de resultats, 200'
);

reset role;
set local role authenticated;
select is((select count(*)::int from public.municipis), 1, 'Un usuari amb sessió pot llegir els municipis');

select * from finish();
rollback;
