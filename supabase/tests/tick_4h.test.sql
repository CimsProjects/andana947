-- Tests del tic econòmic de 4 hores i dels permisos de liquidació.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(8);

-- 1. Les funcions existeixen.
select has_function('public', 'liquida_periode', array['boolean', 'numeric'], 'Hi ha liquida_periode');
select has_function('public', 'tic_4h', 'Hi ha tic_4h');

-- 2. Cap client pot liquidar: ni anon ni authenticated.
select ok(not has_function_privilege('authenticated', 'public.tic_diari(boolean)', 'execute'),
    'Un jugador no pot cridar tic_diari');
select ok(not has_function_privilege('authenticated', 'public.tic_4h()', 'execute'),
    'Un jugador no pot cridar tic_4h');
select ok(not has_function_privilege('anon', 'public.liquida_periode(boolean, numeric)', 'execute'),
    'Un usuari anònim no pot cridar liquida_periode');

-- 3. Fracció fora de rang.
select throws_ok('select public.liquida_periode(true, 0)', 'P0001', null, 'La fracció 0 no és vàlida');
select throws_ok('select public.liquida_periode(true, 2)', 'P0001', null, 'Una fracció més gran que 1 no és vàlida');

-- 4. El tic de 4 hores paga una part del dia de servei (de 0, de matinada, a 4/19).
select ok((public.tic_4h() ->> 'fraccio')::numeric between 0 and 4.0 / 19 + 0.0001, 'tic_4h paga les hores de servei del període');

select * from finish();
rollback;
