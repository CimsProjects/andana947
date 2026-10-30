-- Cobrament continu (decidit el 30/10/2026): es cobra per cada tren o bus que arriba a les andanes del jugador, a mesura
-- que passa el temps, i no cada 4 hores. Substitueix el tic de 4 h (20261030000000_tick_4h.sql i següents).
--
-- La base de dades no té l'horari de cada parada: les arribades del dia es reparteixen igual dins del dia de servei,
-- de 05:00 a 24:00 [estimat] (config_servei, fraccio_servei). Cada cop que l'app ho demana (en obrir-la i cada minut),
-- o quan canvia alguna cosa que fa variar l'ingrés (comprar, fer check-in, millorar), el servidor paga el que s'ha
-- guanyat des de l'últim cop. Un job de pg_cron ho fa cada hora per a tothom, perquè el rànquing estigui al dia encara
-- que algú no obri l'app. Els cèntims que no arriben a un cèntim sencer es guarden per a la vegada següent.

-- 1. Estat del cobrament de cada jugador -----------------------------------------------------------------------------

alter table public.perfils
    add column if not exists cobrat_fins timestamptz,
    add column if not exists resta_cobrament numeric(14, 6) not null default 0,
    add column if not exists pendent_benvinguda numeric(12, 2) not null default 0,
    add column if not exists pendent_arribades numeric(14, 2) not null default 0,
    add column if not exists vist_el timestamptz;
comment on column public.perfils.cobrat_fins is 'Fins quan s''han cobrat les arribades del jugador.';
comment on column public.perfils.resta_cobrament is 'La part d''un cèntim que encara no s''ha pagat.';
comment on column public.perfils.pendent_benvinguda is 'El que s''ha cobrat mentre el jugador no mirava l''app (pantalla de benvinguda).';
comment on column public.perfils.pendent_arribades is 'Les arribades d''aquest cobrament [estimat].';
comment on column public.perfils.vist_el is 'L''últim cop que l''app oberta va demanar el cobrament.';

-- Fins ara, tot està cobrat (l'últim tic de 4 h); a partir d'ara, es cobra contínuament.
update public.perfils set cobrat_fins = now() where cobrat_fins is null;
alter table public.perfils alter column cobrat_fins set default now();

-- Un apunt per jugador i hora: «Arribades de les 17 h», que va creixent.
alter table public.moviments add column if not exists periode timestamptz;
create unique index if not exists moviments_cobrament_periode_idx
    on public.moviments (jugador_id, periode) where periode is not null;

-- 2. L'ingrés sense arrodonir --------------------------------------------------------------------------------------

-- L'ingrés del jugador en una fracció del dia, sense arrodonir als cèntims (un minut d'una andana petita no arriba a
-- un cèntim). La mateixa fórmula que ingres_jugador: arribades (×1,5 amb concessió física, × millores), menys el
-- manteniment, més els dividends.
create or replace function public.ingres_jugador_exacte(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns numeric
language sql
stable
security definer
set search_path = ''
as $$
    with dia as (select case when p_feiner then 1.0 else 0.6 end::double precision as factor),
    andanes as (
        select coalesce(sum(
            public.ingres_diari_concessio(c.node_id, dia.factor)
                * case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
                * public.factor_millores(p_jugador, c.node_id)
            - public.manteniment_diari(c.node_id, p_jugador)), 0) as total
        from public.concessions c cross join dia
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    ),
    dividends as (
        select coalesce(sum(
            (public.ingres_diari((n.viatgers_feiner::double precision * dia.factor)::numeric) / 100.0) * a.quantitat
                * case when a.presencial_fins >= now() then public.multiplicador_concessio() else 1 end), 0) as total
        from public.accions a join public.nodes n on n.id = a.node_id cross join dia
        where a.jugador_id = p_jugador and a.darrera_visita_el >= (now() - interval '30 days')
    )
    select greatest((select total from andanes) + (select total from dividends), 0) * p_fraccio;
$$;

-- Les arribades del jugador en una fracció del dia [estimat].
create or replace function public.arribades_jugador(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns numeric
language sql
stable
set search_path = ''
as $$
    select coalesce(sum(n.expedicions_feiner), 0) * case when p_feiner then 1.0 else 0.6 end * p_fraccio
    from public.concessions c join public.nodes n on n.id = c.node_id
    where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now();
$$;

-- 3. Cobrar ------------------------------------------------------------------------------------------------------------

-- Paga al jugador les arribades des de l'últim cobrament fins ara, dia per dia (feiner o no, a l'hora de Catalunya), i
-- en suma l'experiència de maestria. Torna el que s'ha pagat.
create or replace function public.liquida_jugador(p_jugador uuid)
returns numeric
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ara timestamptz := now();
    v_desde timestamptz;
    v_resta numeric;
    v_inici timestamptz;
    v_fi timestamptz;
    v_feiner boolean;
    v_fraccio numeric;
    v_total numeric := 0;
    v_arribades numeric := 0;
    v_pagar numeric(12, 2);
begin
    select p.cobrat_fins, p.resta_cobrament into v_desde, v_resta
    from public.perfils p where p.id = p_jugador
    for update;
    if not found then
        return 0;
    end if;
    -- Com a molt, els últims 60 dies (un jugador que torna després de molt de temps).
    v_desde := greatest(coalesce(v_desde, v_ara), v_ara - interval '60 days');
    if v_ara - v_desde < interval '1 second' then
        return 0;
    end if;

    v_inici := v_desde;
    while v_inici < v_ara loop
        -- Fins a la mitjanit de Catalunya següent (o fins ara).
        v_fi := least(v_ara,
            (date_trunc('day', v_inici at time zone 'Europe/Madrid') + interval '1 day') at time zone 'Europe/Madrid');
        v_feiner := extract(isodow from (v_inici at time zone 'Europe/Madrid')) between 1 and 5;
        v_fraccio := public.fraccio_servei(v_inici, v_fi);
        if v_fraccio > 0 then
            v_total := v_total + public.ingres_jugador_exacte(p_jugador, v_feiner, v_fraccio);
            v_arribades := v_arribades + public.arribades_jugador(p_jugador, v_feiner, v_fraccio);
            perform public.suma_maestria_jugador(p_jugador, v_feiner, v_fraccio);
        end if;
        v_inici := v_fi;
    end loop;

    -- Els cèntims sencers es paguen; la resta es guarda.
    v_pagar := floor((v_total + v_resta) * 100) / 100;
    update public.perfils
    set cobrat_fins = v_ara,
        resta_cobrament = v_total + v_resta - v_pagar,
        saldo = saldo + v_pagar,
        pendent_benvinguda = pendent_benvinguda + v_pagar,
        pendent_arribades = pendent_arribades + v_arribades,
        actualitzat_el = case when v_pagar > 0 then v_ara else actualitzat_el end
    where id = p_jugador;

    if v_pagar > 0 then
        insert into public.moviments (jugador_id, concepte, import, tipus, periode)
        values (p_jugador,
                'Arribades de les ' || extract(hour from (v_ara at time zone 'Europe/Madrid'))::int || ' h',
                v_pagar, 'cobrament', date_trunc('hour', v_ara))
        on conflict (jugador_id, periode) where periode is not null
        do update set import = public.moviments.import + excluded.import;
    end if;
    return v_pagar;
end;
$$;

-- Tothom (pg_cron, cada hora): el rànquing al dia encara que no s'obri l'app.
create or replace function public.liquida_tots()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_jugador uuid;
    v_total numeric := 0;
    v_jugadors integer := 0;
begin
    for v_jugador in select p.id from public.perfils p loop
        v_total := v_total + public.liquida_jugador(v_jugador);
        v_jugadors := v_jugadors + 1;
    end loop;
    return jsonb_build_object('status', 'ok', 'jugadors', v_jugadors, 'catalins', v_total);
end;
$$;

-- Abans de canviar el que dona diners (andanes, accions, millores), es cobra el que s'ha guanyat fins ara: així el
-- canvi només compta a partir d'aquest moment.
create or replace function public.liquida_abans_de_canviar()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.liquida_jugador(new.jugador_id);
    return new;
end;
$$;

drop trigger if exists concessions_liquida on public.concessions;
create trigger concessions_liquida before insert or update on public.concessions
    for each row execute function public.liquida_abans_de_canviar();
drop trigger if exists accions_liquida on public.accions;
create trigger accions_liquida before insert or update on public.accions
    for each row execute function public.liquida_abans_de_canviar();
drop trigger if exists millores_liquida on public.millores_andana;
create trigger millores_liquida before insert on public.millores_andana
    for each row execute function public.liquida_abans_de_canviar();

-- 4. Per a l'app ---------------------------------------------------------------------------------------------------

-- L'estat del que es cobra: saldo, el que s'ha guanyat avui, el ritme d'ara (per hora) i el que s'ha cobrat mentre
-- el jugador no mirava l'app. Abans, cobra.
create or replace function public.json_cobrament(p_jugador uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    with ara as (
        select now() as t,
               extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5 as feiner,
               date_trunc('day', now() at time zone 'Europe/Madrid') at time zone 'Europe/Madrid' as avui
    )
    select jsonb_build_object(
        'ara', ara.t,
        'saldo', p.saldo,
        'avui', coalesce((select sum(m.import) from public.moviments m
                          where m.jugador_id = p_jugador and m.tipus = 'cobrament' and m.creat_el >= ara.avui), 0),
        'ingres_hora', round(public.ingres_jugador_exacte(p_jugador, ara.feiner,
            public.fraccio_servei(ara.t, ara.t + interval '1 hour')), 2),
        'arribades_hora', round(public.arribades_jugador(p_jugador, ara.feiner,
            public.fraccio_servei(ara.t, ara.t + interval '1 hour'))),
        'en_servei', public.fraccio_servei(ara.t, ara.t + interval '1 minute') > 0,
        'pendent_total', p.pendent_benvinguda,
        'pendent_nombre', round(p.pendent_arribades)::int
    )
    from public.perfils p cross join ara
    where p.id = p_jugador;
$$;

-- En tornar a l'app: cobra i diu què s'ha cobrat mentre no hi era.
drop function if exists public.estat_cobraments();
create function public.estat_cobraments()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null or not exists (select 1 from public.perfils p where p.id = v_user_id) then
        return null;
    end if;
    perform public.liquida_jugador(v_user_id);
    return public.json_cobrament(v_user_id);
end;
$$;

-- Mentre l'app és oberta (cada minut): cobra. Si l'app ja era oberta fa poc, el jugador ho ha vist en directe i no
-- cal la pantalla de benvinguda.
create or replace function public.cobra_arribades()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_vist timestamptz;
begin
    if v_user_id is null then
        return null;
    end if;
    select p.vist_el into v_vist from public.perfils p where p.id = v_user_id;
    if not found then
        return null;
    end if;
    perform public.liquida_jugador(v_user_id);
    update public.perfils
    set vist_el = now(),
        pendent_benvinguda = case when v_vist >= now() - interval '5 minutes' then 0 else pendent_benvinguda end,
        pendent_arribades = case when v_vist >= now() - interval '5 minutes' then 0 else pendent_arribades end
    where id = v_user_id;
    return public.json_cobrament(v_user_id);
end;
$$;

-- «Recull» a la pantalla de benvinguda.
create or replace function public.marca_cobraments_vistos(p_fins timestamptz)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.perfils
    set pendent_benvinguda = 0, pendent_arribades = 0, vist_el = now(),
        cobraments_vistos_fins = greatest(coalesce(cobraments_vistos_fins, p_fins), least(p_fins, now()))
    where id = auth.uid();
end;
$$;

-- Mode desenvolupador: paga ara les últimes 4 hores de servei una altra vegada (diners de prova), com si el jugador
-- tornés a l'app, perquè surti la pantalla de benvinguda.
create or replace function public.dev_cobra_ara()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_pagat numeric;
begin
    if v_user_id is null or not coalesce((select p.mode_dev from public.perfils p where p.id = v_user_id), false) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només amb el mode desenvolupador.');
    end if;
    perform public.liquida_jugador(v_user_id);
    update public.perfils set cobrat_fins = now() - interval '4 hours', vist_el = now() - interval '1 hour'
    where id = v_user_id;
    v_pagat := public.liquida_jugador(v_user_id);
    if v_pagat <= 0 then
        return jsonb_build_object('status', 'error',
            'missatge', 'No hi ha res per cobrar: compra alguna andana (de matinada no passa cap tren).');
    end if;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'cobrament',
        'missatge', 'Cobrats ' || v_pagat || ' € de 4 hores d''arribades. Surt de l''app i torna-hi per veure la benvinguda.',
        'saldo', (select p.saldo from public.perfils p where p.id = v_user_id));
end;
$$;

-- 5. Sense tic de 4 hores --------------------------------------------------------------------------------------------

do $cron$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.unschedule(jobid) from cron.job
        where command ilike '%tic_4h%' or command ilike '%tic_diari%' or command ilike '%liquida_tots%'
           or jobname in ('tic_4h', 'tick_4h', 'liquida_tots');
        perform cron.schedule('liquida_tots', '7 * * * *', 'select public.liquida_tots();');
    else
        raise notice 'pg_cron no està activat: activa''l (Database → Extensions) i torna a executar aquesta migració.';
    end if;
end;
$cron$;

drop function if exists public.tic_4h();
drop function if exists public.tic_diari(boolean);
drop function if exists public.liquida_periode(boolean, numeric);
drop function if exists public.periode_cobrament_segons();

-- 6. Permisos ----------------------------------------------------------------------------------------------------------
revoke execute on function public.ingres_jugador_exacte(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.arribades_jugador(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.liquida_jugador(uuid) from public, anon, authenticated;
revoke execute on function public.liquida_tots() from public, anon, authenticated;
revoke execute on function public.liquida_abans_de_canviar() from public, anon, authenticated;
revoke execute on function public.json_cobrament(uuid) from public, anon, authenticated;
grant execute on function public.liquida_tots() to service_role;

revoke execute on function public.estat_cobraments() from public, anon;
revoke execute on function public.cobra_arribades() from public, anon;
revoke execute on function public.marca_cobraments_vistos(timestamptz) from public, anon;
grant execute on function public.estat_cobraments() to authenticated;
grant execute on function public.cobra_arribades() to authenticated;
grant execute on function public.marca_cobraments_vistos(timestamptz) to authenticated;
