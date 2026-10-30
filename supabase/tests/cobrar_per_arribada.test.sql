-- Tests del cobrament per hores de servei i de les andanes del jugador.
-- S'executen amb `npx supabase test db`.

begin;
create extension if not exists pgtap with schema extensions;

select plan(8);

-- Han de coincidir amb dades/configuracio_joc.json (economia.serveiIniciHora i serveiFiHora).
select is((public.config_servei() ->> 'inici_hora')::int, 5, 'El servei comença a les 05:00');
select is((public.config_servei() ->> 'fi_hora')::int, 24, 'El servei acaba a les 24:00');

-- Els sis cobraments d'un dia sumen un dia sencer, també quan canvia l'hora.
select is(
    (select round(sum(public.fraccio_servei(t, t + interval '4 hours')), 3)
     from generate_series('2026-11-04 00:00+00'::timestamptz, '2026-11-04 20:00+00'::timestamptz, interval '4 hours') t),
    1.000, 'Un dia normal suma 1'
);
select is(
    (select round(sum(public.fraccio_servei(t, t + interval '4 hours')), 3)
     from generate_series('2026-10-24 00:00+00'::timestamptz, '2026-10-26 20:00+00'::timestamptz, interval '4 hours') t),
    3.000, 'Tres dies amb el canvi d''hora d''octubre sumen 3'
);
select is(
    (select round(sum(public.fraccio_servei(t, t + interval '4 hours')), 3)
     from generate_series('2027-03-27 00:00+00'::timestamptz, '2027-03-29 20:00+00'::timestamptz, interval '4 hours') t),
    3.000, 'Tres dies amb el canvi d''hora de març sumen 3'
);
select is(public.fraccio_servei('2026-11-04 00:00+00', '2026-11-04 04:00+00'), 0::numeric,
    'De matinada (01:00–05:00) no es cobra');

select ok(has_function_privilege('authenticated', 'public.andanes_jugador()', 'execute'),
    'Un jugador pot llegir les seves andanes');
select is(public.andanes_jugador(), null, 'Sense sessió, andanes_jugador retorna null');

select * from finish();
rollback;
