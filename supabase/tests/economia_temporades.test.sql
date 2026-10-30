-- Tests de l'economia nova: compra a distància, concessió física, temporades, fites i skins.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(24);

-- Valors: han de coincidir amb dades/configuracio_joc.json (blocs «temporades» i «compra»).
select is((public.config_temporades() ->> 'durada_dies')::int, 30, 'Temporades de 30 dies');
select is(public.config_temporades() ->> 'inici', '2026-10-29', 'La primera temporada comença el 29/10/2026');
select is(public.multiplicador_concessio(), 1.5, 'La concessió física multiplica per 1,5');
select is((public.config_compra() ->> 'creixement_preu_per_andana')::numeric, 0.02, 'El preu puja un 2 % per andana');
select is((public.config_compra() ->> 'quota_manteniment')::numeric, 0.05, 'El manteniment és un 5 % de l''ingrés');

-- Temporades: número, inici i fi a la mitjanit de Catalunya.
select is((public.temporada_de('2026-10-29 12:00+01') ->> 'numero')::int, 1, 'El 29/10 és la temporada 1');
select is((public.temporada_de('2026-11-28 00:30+01') ->> 'numero')::int, 2, 'El 28/11 és la temporada 2');
select is((public.temporada_de('2026-11-10 12:00+01') ->> 'fi')::timestamptz, '2026-11-28 00:00+01'::timestamptz,
    'La temporada 1 s''acaba el 28/11 a mitjanit');
select is((public.temporada_de('2026-01-01 12:00+01') ->> 'numero')::int, 1, 'Abans de començar, temporada 1');

-- Fites i skins.
select ok((select count(*) from public.fites) >= 18, 'Hi ha les fites');
select ok(exists (select 1 from public.fites where id = 'provincies_4' and objectiu = 4), 'La fita de les quatre províncies');
select ok((select count(*) from public.fites where skin is not null) >= 4, 'Algunes fites donen skin');
select ok(exists (select 1 from public.skins where id = 'or'), 'Hi ha la skin d''or');

-- Permisos: el jugador compra, busca i consulta; no pot donar-se andanes ni fites ni tancar temporades.
select ok(has_function_privilege('authenticated', 'public.compra_andana(text)', 'execute'), 'Un jugador pot comprar andanes');
select ok(has_function_privilege('authenticated', 'public.compra_accions(text, integer)', 'execute'), 'Un jugador pot comprar accions');
select ok(has_function_privilege('authenticated', 'public.cerca_andanes(text, double precision, double precision)', 'execute'),
    'Un jugador pot buscar andanes');
select ok(has_function_privilege('authenticated', 'public.estat_temporada()', 'execute'), 'Un jugador pot veure la temporada');
select ok(not has_function_privilege('authenticated', 'public.adquireix_andana(uuid, text, boolean, boolean)', 'execute'),
    'Un jugador no pot donar-se andanes');
select ok(not has_function_privilege('authenticated', 'public.revisa_fites(uuid)', 'execute'), 'Un jugador no pot donar-se fites');
select ok(not has_function_privilege('authenticated', 'public.tanca_temporades()', 'execute'), 'Un jugador no pot tancar temporades');
select ok(not has_function_privilege('anon', 'public.compra_andana(text)', 'execute'), 'Un usuari anònim no pot comprar');

-- Sense sessió.
select is(public.compra_andana('qualsevol') ->> 'status', 'error', 'Sense sessió, no es compra res');
select is(public.estat_temporada(), null, 'Sense sessió, no hi ha temporada');

-- El preu sense andanes és el de la concessió.
select is(public.preu_andana(1000, null), public.preu_concessio(1000), 'Sense andanes, el preu és el de la concessió');

select * from finish();
rollback;
