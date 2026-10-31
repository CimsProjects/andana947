-- Comprovacions de seguretat de tot l'esquema (docs/seguretat.md). S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(7);

select is((select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
           where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity), 0,
    'Totes les taules tenen RLS');
select is((select count(*)::int from information_schema.role_table_grants
           where table_schema = 'public' and grantee in ('anon', 'authenticated')
             and privilege_type in ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')), 0,
    'L''app no pot escriure directament cap taula: tot passa per funcions del servidor');
select is((select count(*)::int from pg_policies where schemaname = 'public' and cmd <> 'SELECT'), 0,
    'Cap política d''escriptura');
select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.prosecdef and has_function_privilege('anon', p.oid, 'execute')), 0,
    'Un usuari anònim no pot cridar cap funció SECURITY DEFINER');
select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.prolang in (select oid from pg_language where lanname in ('sql', 'plpgsql'))
             and not coalesce(array_to_string(p.proconfig, ',') like '%search_path=%', false)), 0,
    'Totes les funcions fixen el search_path');
select ok(not has_function_privilege('authenticated', 'public.suma_xp(uuid, text, text, numeric)', 'execute')
          and not has_function_privilege('authenticated', 'public.liquida_jugador(uuid)', 'execute')
          and not has_function_privilege('authenticated', 'public.puja_nivells(uuid, text, text)', 'execute'),
    'Un jugador no es pot donar diners ni experiència');

-- El rànquing no compta els comptes amb el mode desenvolupador.
insert into auth.users (id) values ('00000000-0000-0000-0000-00000000c801');
insert into public.perfils (id, saldo, prestigi, nivell, mode_dev) values ('00000000-0000-0000-0000-00000000c801', 0, 0, 1, true)
on conflict (id) do update set mode_dev = true;
insert into public.moviments (jugador_id, concepte, import, tipus) values ('00000000-0000-0000-0000-00000000c801', 'Prova', 1000, 'cobrament');
select ok(not exists (select 1 from public.ranking_de((public.temporada_actual() ->> 'numero')::int)
                      where jugador_id = '00000000-0000-0000-0000-00000000c801'),
    'Els comptes de desenvolupador no surten al rànquing');

select * from finish();
rollback;
