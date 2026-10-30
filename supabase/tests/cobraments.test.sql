-- Tests dels cobraments visibles (el que s'ha cobrat mentre el jugador no mirava l'app).
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

select has_column('public', 'moviments', 'tipus', 'Els moviments tenen tipus');
select has_column('public', 'perfils', 'cobraments_vistos_fins', 'El perfil desa fins quan s''han vist els cobraments');
select hasnt_function('public', 'tic_4h', 'Ja no hi ha el tic de 4 hores: es cobra contínuament');

-- Permisos: el jugador consulta i marca; no calcula ni liquida.
select ok(has_function_privilege('authenticated', 'public.estat_cobraments()', 'execute'),
    'Un jugador pot consultar els seus cobraments');
select ok(has_function_privilege('authenticated', 'public.marca_cobraments_vistos(timestamptz)', 'execute'),
    'Un jugador pot marcar els cobraments com a vistos');
select ok(not has_function_privilege('authenticated', 'public.ingres_jugador(uuid, boolean, numeric)', 'execute'),
    'Un jugador no pot calcular l''ingrés de ningú');
select ok(not has_function_privilege('anon', 'public.estat_cobraments()', 'execute'),
    'Un usuari anònim no pot consultar cobraments');

-- Sense sessió no hi ha estat.
select is(public.estat_cobraments(), null, 'Sense sessió, estat_cobraments retorna null');

-- Un jugador sense concessions no cobra res al pròxim tic.
select is(public.ingres_jugador('00000000-0000-0000-0000-000000000000', true, 1.0 / 6), 0::numeric,
    'Sense concessions ni accions, l''ingrés és 0');

select * from finish();
rollback;
