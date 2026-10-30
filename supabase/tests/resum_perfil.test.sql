-- Tests del resum del perfil.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(3);

select has_function('public', 'resum_perfil', 'Hi ha resum_perfil');
select ok(not has_function_privilege('anon', 'public.resum_perfil()', 'execute'), 'Un usuari anònim no pot llegir resums');
select is(public.resum_perfil(), null, 'Sense sessió, resum_perfil retorna null');

select * from finish();
rollback;
