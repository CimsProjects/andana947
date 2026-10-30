-- Tests de les andanes de regal del Director.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(11);

select has_column('public', 'concessions', 'regal', 'Les concessions poden ser de regal');
select has_column('public', 'perfils', 'regal_triat', 'El perfil desa si ja s''ha triat el regal');

-- Els valors han de coincidir amb el bloc «regal» de dades/configuracio_joc.json.
select is((public.config_regal() ->> 'opcions_per_provincia')::int, 5, '5 opcions per província');
select is((public.config_regal() ->> 'trams_per_provincia')::int, 2, '2 trams per província');
select is((public.config_regal() ->> 'maxim_andanes_per_provincia')::int, 2, 'Màxim 2 andanes per província');
select is((public.config_regal() ->> 'viatgers_minims')::int, 20, 'Mínim 20 viatgers');
select is((public.config_regal() ->> 'viatgers_maxims')::int, 3000, 'Màxim 3.000 viatgers');

-- Permisos.
select ok(not has_function_privilege('authenticated', 'public.ofertes_regal_de(uuid)', 'execute'),
    'Un jugador no pot veure les ofertes d''un altre');
select ok(has_function_privilege('authenticated', 'public.tria_regal(text[])', 'execute'),
    'Un jugador pot triar el seu regal');
select ok(not has_function_privilege('anon', 'public.tria_regal(text[])', 'execute'),
    'Un usuari anònim no pot triar regal');

-- Sense sessió no es pot triar.
select throws_ok($$select public.tria_regal(array['x'])$$, 'P0001', 'Cal iniciar sessió.',
    'Sense sessió, no es tria res');

select * from finish();
rollback;
