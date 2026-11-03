-- Tests d'esborrar el compte des de l'app. S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(8);

-- Dades de prova (no del joc): dos jugadors, cadascun fundador d'una cooperativa; el segon en té un altre membre.
insert into auth.users (id) values
    ('00000000-0000-0000-0000-00000000e501'),
    ('00000000-0000-0000-0000-00000000e502'),
    ('00000000-0000-0000-0000-00000000e503');
insert into public.perfils (id, saldo, prestigi, nivell) values
    ('00000000-0000-0000-0000-00000000e501', 500, 0, 1),
    ('00000000-0000-0000-0000-00000000e502', 500, 0, 1),
    ('00000000-0000-0000-0000-00000000e503', 500, 0, 1)
on conflict (id) do update set saldo = 500;
insert into public.moviments (jugador_id, concepte, import) values ('00000000-0000-0000-0000-00000000e501', 'Prova', 1);
insert into public.cooperatives (id, nom, comarca) values (990001, 'Coop sola', '13'), (990002, 'Coop amb gent', '13');
insert into public.membres_cooperativa (jugador_id, cooperativa, rol) values
    ('00000000-0000-0000-0000-00000000e501', 990001, 'fundador'),
    ('00000000-0000-0000-0000-00000000e502', 990002, 'fundador'),
    ('00000000-0000-0000-0000-00000000e503', 990002, 'membre');

-- Sense sessió no es pot.
select throws_ok('select public.esborra_compte()', '42501', 'Cal iniciar la sessió', 'Sense sessió no esborra res');
select ok(not has_function_privilege('anon', 'public.esborra_compte()', 'execute'), 'Els anònims no la poden cridar');

set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000e501';
select ok(public.esborra_compte(), 'El jugador esborra el seu compte');
select is((select count(*)::int from auth.users where id = '00000000-0000-0000-0000-00000000e501'), 0, 'L''usuari ja no hi és');
select is((select count(*)::int from public.perfils where id = '00000000-0000-0000-0000-00000000e501'), 0, 'Ni el perfil');
select is((select count(*)::int from public.moviments where jugador_id = '00000000-0000-0000-0000-00000000e501'), 0, 'Ni els moviments');
select is((select count(*)::int from public.cooperatives where id = 990001), 0, 'La cooperativa sense ningú s''esborra');

set local request.jwt.claim.sub = '00000000-0000-0000-0000-00000000e502';
select public.esborra_compte();
select is((select count(*)::int from public.cooperatives where id = 990002), 1, 'La cooperativa amb membres es queda');

select * from finish();
rollback;
