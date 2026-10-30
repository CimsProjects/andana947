-- Tests de l'avatar i de la introducció del director.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

select has_table('public', 'peces_avatar', 'Hi ha la taula de peces de l''avatar');
select has_column('public', 'perfils', 'avatar', 'El perfil desa l''avatar');
select has_column('public', 'perfils', 'introduccio_feta', 'El perfil desa si s''ha fet la introducció');

select ok(exists (select 1 from public.peces_avatar where id = 'tronc_samarreta' and not nomes_npc),
    'La samarreta es pot triar');
select ok(exists (select 1 from public.peces_avatar where id = 'cap_cap_estacio' and nomes_npc),
    'La gorra de cap d''estació és només del director');

select ok(has_function_privilege('authenticated', 'public.desa_avatar(jsonb)', 'execute'),
    'Un jugador pot desar el seu avatar');
select ok(not has_function_privilege('anon', 'public.completa_introduccio()', 'execute'),
    'Un usuari anònim no pot completar la introducció');

-- Sense sessió no es pot desar res.
select throws_ok(
    $$select public.desa_avatar('{"cos":"noi"}'::jsonb)$$,
    'P0001', 'Cal iniciar sessió.', 'Sense sessió, no es desa l''avatar'
);
select throws_ok('select public.completa_introduccio()', 'P0001', 'Cal iniciar sessió.',
    'Sense sessió, no es completa la introducció');

select * from finish();
rollback;
